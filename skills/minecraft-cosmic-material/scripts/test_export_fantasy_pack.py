"""Check exported archives against the FantasyPresets resource loader contract."""
from copy import deepcopy
from pathlib import Path
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

import export_fantasy_pack as exporter


class ExportFantasyPackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.catalog = exporter.read_json(exporter.CATALOG)
        self.original_bytes = exporter.CATALOG.read_bytes()
        self.recipe = {"schema": exporter.SCHEMA, "parameters": deepcopy(self.catalog["presets"][1]),
                       "preview": {"view": [0.2, -0.1], "time": 5, "shape": 1}}
        self.recipe_path = self.root / "recipe.json"
        self.output = self.root / "fantasy.zip"

    def tearDown(self):
        self.assertEqual(exporter.CATALOG.read_bytes(), self.original_bytes)
        self.temp.cleanup()

    def write_recipe(self, value=None, bom=False):
        self.recipe_path.write_text(json.dumps(self.recipe if value is None else value),
                                    encoding="utf-8-sig" if bom else "utf-8")

    def read_pack(self):
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(set(archive.namelist()), {"pack.mcmeta", exporter.RESOURCE})
            self.assertIsNone(archive.testzip())
            meta = json.loads(archive.read("pack.mcmeta"))
            self.assertEqual(meta["pack"]["pack_format"], 15)
            catalog = json.loads(archive.read(exporter.RESOURCE))
        self.assertEqual(catalog["schema"], "fantasy-material/v1")
        for preset in catalog["presets"]:
            self.assertIsInstance(preset["id"], str)
            self.assertIsInstance(preset["mode"], int)
            self.assertEqual(set(exporter.RANGES) - preset.keys(), set())
            for key in exporter.COLORS:
                self.assertEqual(len(preset[key]), 3)
            exporter.validated_parameters(preset)
        return catalog

    def test_custom_pack_preserves_catalog_and_accepts_bom(self):
        self.recipe["parameters"].update(seed=4096, warp=1.75, base=[0.03, 0.05, 0.21])
        self.write_recipe(bom=True)
        output, command = exporter.export_pack(self.recipe_path, self.output, "moon-metal")
        catalog = self.read_pack()
        self.assertEqual(catalog["presets"][:-1], self.catalog["presets"])
        self.assertEqual(catalog["presets"][-1]["id"], "moon-metal")
        self.assertEqual(catalog["presets"][-1]["base"], [0.03, 0.05, 0.21])
        self.assertNotIn("preview", catalog)
        self.assertEqual(output, self.output.resolve())
        self.assertEqual(command, '/give @s minecraft:diamond_sword{CosmicMaterial:{Material:"moon-metal",Mask:"blade"}}')

    def test_internal_flow_mode_is_exported(self):
        self.recipe["parameters"] = deepcopy(next(p for p in self.catalog["presets"] if p["id"] == "fantasy-flow"))
        self.write_recipe()
        exporter.export_pack(self.recipe_path, self.output, "amber-corridor")
        result = self.read_pack()["presets"]
        self.assertEqual(result[:-1], self.catalog["presets"])
        self.assertEqual(result[-1]["mode"], 6)
        self.assertEqual(result[-1]["id"], "amber-corridor")

    def test_replace_changes_only_selected_preset(self):
        self.recipe["parameters"]["speed"] = 0
        self.write_recipe()
        exporter.export_pack(self.recipe_path, self.output)
        result = self.read_pack()["presets"]
        expected = deepcopy(self.catalog["presets"])
        expected[1]["speed"] = 0
        self.assertEqual(result, expected)

    def test_new_interior_modes_preserve_ids_and_parameters(self):
        for mode in range(7, 13):
            with self.subTest(mode=mode):
                self.recipe["parameters"] = deepcopy(next(p for p in self.catalog["presets"] if p["mode"] == mode))
                self.write_recipe()
                destination = self.root / f"style-{mode}.zip"
                exporter.export_pack(self.recipe_path, destination)
                with zipfile.ZipFile(destination) as archive:
                    catalog = json.loads(archive.read(exporter.RESOURCE))
                selected = next(p for p in catalog["presets"] if p["mode"] == mode)
                self.assertEqual(selected, self.recipe["parameters"])

    def test_water_parameters_roundtrip_and_rejection(self):
        self.recipe["parameters"] = deepcopy(next(p for p in self.catalog["presets"] if p["mode"] == 12))
        self.recipe["parameters"].update(waveHeight=.41, waveLength=4.2, windAngle=270,
                                        choppiness=.33, foam=.22, roughness=.42)
        self.write_recipe()
        exporter.export_pack(self.recipe_path, self.output, "moon-water")
        self.assertEqual({k: self.read_pack()["presets"][-1][k] for k in exporter.WATER_RANGES},
                         {k: self.recipe["parameters"][k] for k in exporter.WATER_RANGES})
        self.output.unlink()
        for key, (low, high) in exporter.WATER_RANGES.items():
            for value in (low - .01, high + .01, float("nan"), True, "1"):
                bad = deepcopy(self.recipe)
                bad["parameters"][key] = value
                self.write_recipe(bad)
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    exporter.export_pack(self.recipe_path, self.output)
                self.assertFalse(self.output.exists())
            bad = deepcopy(self.recipe)
            del bad["parameters"][key]
            self.write_recipe(bad)
            if key == "waterTilt":
                self.assertEqual(exporter.validated_parameters(bad["parameters"])[key], .85)
                continue
            with self.subTest(missing=key), self.assertRaises(ValueError):
                exporter.export_pack(self.recipe_path, self.output)

    def test_rejects_nonfinite_wrong_types_and_out_of_range(self):
        invalid = [("scale", float("inf")), ("scale", float("nan")), ("scale", 0.29),
                   ("depth", 6.01), ("mode", 13), ("mode", 1.5), ("seed", 65536),
                   ("seed", True), ("emission", "1"), ("base", [0, 0.5, 1.01]),
                   ("accent", [0, float("inf"), 1]), ("highlight", [0, 1])]
        for key, value in invalid:
            with self.subTest(key=key, value=value):
                bad = deepcopy(self.recipe)
                bad["parameters"][key] = value
                self.write_recipe(bad)
                with self.assertRaises(ValueError):
                    exporter.export_pack(self.recipe_path, self.output, "invalid-test")
                self.assertFalse(self.output.exists())

    def test_rejects_unknown_mismatched_and_unsafe_ids(self):
        self.write_recipe()
        for identifier in ("../escape", "UpperCase", "two words", "", "rune-vault"):
            with self.subTest(identifier=identifier), self.assertRaises(ValueError):
                exporter.export_pack(self.recipe_path, self.output, identifier)
        for key, value in (("id", "unknown"), ("mode", 3)):
            bad = deepcopy(self.recipe)
            bad["parameters"][key] = value
            self.write_recipe(bad)
            with self.assertRaises(ValueError):
                exporter.export_pack(self.recipe_path, self.output)
        self.assertFalse(self.output.exists())

    def test_refuses_overwrite_until_force(self):
        self.write_recipe()
        self.output.write_bytes(b"existing user content")
        with self.assertRaises(FileExistsError):
            exporter.export_pack(self.recipe_path, self.output)
        self.assertEqual(self.output.read_bytes(), b"existing user content")
        exporter.export_pack(self.recipe_path, self.output, force=True)
        self.read_pack()

    def test_cli_runs_from_an_independent_skill_snapshot(self):
        snapshot = self.root / "snapshot"
        script = snapshot / "scripts" / "export_fantasy_pack.py"
        catalog = snapshot / "assets" / "fantasy-lab" / "recipes.json"
        script.parent.mkdir(parents=True)
        catalog.parent.mkdir(parents=True)
        shutil.copy2(Path(exporter.__file__), script)
        shutil.copy2(exporter.CATALOG, catalog)
        self.write_recipe()
        process = subprocess.run([sys.executable, str(script), "--recipe", str(self.recipe_path),
                                  "--out", str(self.output), "--id", "snapshot-metal"],
                                 cwd=self.root, capture_output=True, text=True, timeout=15)
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertIn('Material:"snapshot-metal"', process.stdout)
        self.assertIn("Forge 1.20.1", process.stdout)
        self.assertEqual(self.read_pack()["presets"][-1]["id"], "snapshot-metal")


if __name__ == "__main__":
    unittest.main()
