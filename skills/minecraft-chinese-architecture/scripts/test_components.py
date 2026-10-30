"""Behavioral checks for rotation, bounded composition and roof/building forms."""
from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import numpy as np
from scipy.ndimage import label

from generate_building import export, make_building, verify
from roof_kernel import FORMS, make_roof
from voxel_model import Model, rotate_state


class ComponentTests(unittest.TestCase):
    def test_rotation_known_directions(self):
        cases = {
            "minecraft:oak_stairs[facing=north,half=top,shape=inner_left,waterlogged=false]": "minecraft:oak_stairs[facing=east,half=top,shape=inner_left,waterlogged=false]",
            "minecraft:oak_log[axis=x]": "minecraft:oak_log[axis=z]",
            "minecraft:oak_sign[rotation=15,waterlogged=false]": "minecraft:oak_sign[rotation=3,waterlogged=false]",
            "minecraft:rail[shape=north_east,waterlogged=false]": "minecraft:rail[shape=south_east,waterlogged=false]",
            "minecraft:powered_rail[powered=false,shape=ascending_east,waterlogged=false]": "minecraft:powered_rail[powered=false,shape=ascending_south,waterlogged=false]",
            "minecraft:jigsaw[orientation=north_up]": "minecraft:jigsaw[orientation=east_up]",
            "minecraft:oak_fence[east=false,north=true,south=false,waterlogged=false,west=false]": "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=false]",
        }
        for state, expected in cases.items():
            self.assertEqual(rotate_state(state, 1), expected)
            current = state
            for _ in range(4): current = rotate_state(current, 1)
            self.assertEqual(current, state)

    def test_coordinate_rotation_and_inverse(self):
        m = Model((-5, -2, 3), (1, 2, 6))
        m.set(-4, 0, 5, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
        m.set(1, 2, 3, "minecraft:stone")
        rotated = m.rotated(1)
        self.assertEqual(rotated.origin.tolist(), [-6, -2, -5])
        self.assertIn("facing=east", rotated.get(-5, 0, -4))
        self.assertEqual(rotated.get(-3, 2, 1), "minecraft:stone")
        inverse = rotated.rotated(3)
        np.testing.assert_array_equal(inverse.origin, m.origin)
        np.testing.assert_array_equal(np.array(inverse.palette)[inverse.blocks], np.array(m.palette)[m.blocks])

    def test_bounds_and_atomic_paste(self):
        m = Model((0, 0, 0), (4, 4, 4))
        m.set(2, 2, 2, "minecraft:stone")
        source = Model((0, 0, 0), (1, 1, 1))
        source.box((0, 0, 0), (1, 1, 1), "minecraft:gold_block")
        before, palette = m.blocks.copy(), list(m.palette)
        with self.assertRaises(ValueError): m.paste(source, (2, 2, 2))
        with self.assertRaises(IndexError): m.paste(source, (4, 4, 4), conflict="replace")
        with self.assertRaises(IndexError): m.set(-1, 0, 0, "minecraft:diamond_block")
        with self.assertRaises(IndexError): m.box((0, 0, 0), (5, 0, 0), "minecraft:stone")
        np.testing.assert_array_equal(m.blocks, before)
        self.assertEqual(m.palette, palette)
        outcome = m.paste(source, (2, 2, 2), conflict="keep")
        self.assertEqual(outcome["written_voxels"], 7)
        self.assertEqual(m.get(2, 2, 2), "minecraft:stone")
        outcome = m.paste(source, (2, 2, 2), conflict="replace")
        self.assertEqual(outcome["differing_occupied_overlaps"], 1)
        air = Model((0, 0, 0), (0, 0, 0))
        m.paste(air, (2, 2, 2), conflict="replace", include_air=True)
        self.assertEqual(m.get(2, 2, 2), "minecraft:air")
        with self.assertRaises(ValueError): Model((0, 0, 0), (1000, 1000, 1000))

    def test_roof_morphology_and_connections(self):
        results = {}
        for form in FORMS:
            cfg = dict(half_x=14, half_z=9, form=form, base_y=6, rise=6, gable_inset=4,
                       uplift=0 if form in ("flush_gable", "barrel_gable") else 1)
            if form == "pyramidal": cfg["half_x"] = 9
            m, report = make_roof(**cfg)
            self.assertEqual(label(m.blocks != 0)[1], 1, form)
            surface = np.array(report["surface_y_rows_zx"])
            np.testing.assert_array_equal(surface, surface[:, ::-1])
            np.testing.assert_array_equal(surface, surface[::-1, :])
            results[form] = surface
            if form == "hip_and_gable": self.assertGreater(report["gable_infill_voxels"], 0)
            if form == "barrel_gable": self.assertEqual(report["ridge_cells"], 0)
            if form == "pyramidal": self.assertEqual(report["ridge_cells"], 1)
        # Gable end rises to the crown; a hip end starts near its eave.
        self.assertGreater(results["overhanging_gable"][9, -1], results["hip"][9, -1])
        self.assertGreater(results["hip_and_gable"][9, 24], results["hip_and_gable"][9, 25])
        self.assertFalse(np.array_equal(results["barrel_gable"], results["flush_gable"]))

    def test_high_roofs_and_rotated_geometry(self):
        for form in FORMS:
            for rise in (1, 8, 20, 40):
                args = dict(half_x=8, half_z=5, rise=rise, curve=2.5, gable_inset=3,
                            form=form, uplift=0 if form in ("flush_gable", "barrel_gable") else 4)
                if form == "pyramidal": args["half_x"] = 5
                m, report = make_roof(**args)
                self.assertEqual(label(m.blocks != 0)[1], 1, (form, rise))
                # Rotating the explicit geometry should match a Z-axis roof
                # state-for-state, with its half-spans swapped.
                n, other = make_roof(**{**args, "half_x": args["half_z"], "half_z": args["half_x"], "ridge_axis": "z"})
                rotated = m.rotated(1)
                np.testing.assert_array_equal(np.array(rotated.palette)[rotated.blocks], np.array(n.palette)[n.blocks], err_msg=f"{form} {rise}")

    def test_real_measured_spacing_and_export(self):
        cases = [
            {"bays_x": [7, 7], "bays_z": [6, 12, 6], "column_height": 13},
            {"bays_x": [6, 6, 12, 6, 6], "bays_z": [6, 6], "column_height": 11},
            {"bays_x": [4, 6, 8], "bays_z": [4, 8], "door_bay": 0},
            {"kind": "pavilion", "bays_x": [6, 6], "bays_z": [6, 6], "roof": {"form": "pyramidal"}},
        ]
        with tempfile.TemporaryDirectory() as folder:
            for i, config in enumerate(cases):
                report = export(config, folder, f"sample-{i}", render=False)
                self.assertTrue(all(report["checks"].values()))
            original = Path(folder, "sample-0.schem").read_bytes()
            export(cases[0], folder, "sample-0", render=False)
            self.assertEqual(Path(folder, "sample-0.schem").read_bytes(), original)
            for form in FORMS:
                config = {"roof": {"form": form, "uplift": 0 if form in ("flush_gable", "barrel_gable") else 1}}
                if form == "pyramidal": config.update(bays_x=[6, 6], bays_z=[6, 6])
                m, d, c = make_building(config)
                self.assertTrue(all(verify(m, d, c).values()))
            for bad in ({"bays_x": [7, 7, 7]}, {"door_bay": 4}, {"roof": {"form": "pagoda"}}, {"column_height": True}, {"typo": 3}):
                with self.assertRaises(ValueError): make_building(bad)


if __name__ == "__main__":
    unittest.main(verbosity=2)
