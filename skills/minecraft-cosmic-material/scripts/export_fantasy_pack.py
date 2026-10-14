"""Export a fantasy workbench recipe as a Minecraft 1.20.1 resource pack."""
from copy import deepcopy
from io import BytesIO
from pathlib import Path
import argparse
import json
import math
import re
import sys
import zipfile


SCHEMA = "fantasy-material/v1"
CATALOG = Path(__file__).resolve().parent.parent / "assets" / "fantasy-lab" / "recipes.json"
RESOURCE = "assets/cosmicmaterial/fantasy-recipes.json"
RANGES = {
    "mode": (0, 13), "seed": (0, 65535), "scale": (0.3, 3),
    "speed": (0, 1), "detail": (0.2, 2), "warp": (0, 2),
    "emission": (0, 3), "depth": (0.5, 6), "parallax": (0, 1.5),
    "exposure": (0.3, 2.5),
}
COLORS = ("base", "accent", "highlight")
WATER_RANGES = {"waveHeight": (0, 0.65), "waveLength": (0.55, 6), "windAngle": (0, 360),
                "choppiness": (0, 1), "foam": (0, 1), "roughness": (0.04, 0.55), "waterTilt": (0, 1),
                "waterLightAzimuth": (0, 360), "waterLightElevation": (-0.2, 1.4), "waterOpacity": (0.05, 1)}


def read_json(path):
    # utf-8-sig also accepts ordinary UTF-8 exported by the browser.
    with Path(path).open("r", encoding="utf-8-sig") as source:
        return json.load(source)


def valid_id(value):
    if not isinstance(value, str) or not re.fullmatch(r"[a-z0-9_-]+", value):
        raise ValueError("Preset id must contain only lowercase a-z, 0-9, '_' or '-'.")
    return value


def finite_number(value, key, low, high, integer=False):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f"{key} must be a number.")
    if not math.isfinite(value) or not low <= value <= high:
        raise ValueError(f"{key} must be finite and within [{low}, {high}].")
    if integer and value != int(value):
        raise ValueError(f"{key} must be an integer.")
    return int(value) if integer else value


def validated_parameters(parameters):
    if not isinstance(parameters, dict):
        raise ValueError("Recipe parameters must be an object.")
    clean = {"id": valid_id(parameters.get("id"))}
    for key, (low, high) in RANGES.items():
        clean[key] = finite_number(parameters.get(key), key, low, high,
                                   integer=key in ("mode", "seed"))
    for key in COLORS:
        value = parameters.get(key)
        if not isinstance(value, list) or len(value) != 3:
            raise ValueError(f"{key} must contain exactly three linear RGB numbers.")
        clean[key] = [finite_number(component, f"{key}[{i}]", 0, 1)
                      for i, component in enumerate(value)]
    if clean["mode"] == 12:
        for key, (low, high) in WATER_RANGES.items():
            defaults = {"waterTilt": 0.85, "waterLightAzimuth": 250, "waterLightElevation": 0.62, "waterOpacity": 0.90}
            clean[key] = finite_number(parameters.get(key, defaults.get(key)), key, low, high)
    for key in ("name", "mechanism"):
        if key in parameters:
            if not isinstance(parameters[key], str) or not parameters[key].strip():
                raise ValueError(f"{key} must be a nonempty string.")
            clean[key] = parameters[key]
    return clean


def merged_catalog(recipe, catalog, new_id=None):
    if not isinstance(recipe, dict) or recipe.get("schema") != SCHEMA:
        raise ValueError(f"Recipe schema must be {SCHEMA!r}.")
    parameters = validated_parameters(recipe.get("parameters"))
    if not isinstance(catalog, dict) or catalog.get("schema") != SCHEMA:
        raise ValueError("Bundled fantasy catalog has an unsupported schema.")
    presets = catalog.get("presets")
    if not isinstance(presets, list) or not presets:
        raise ValueError("Bundled fantasy catalog has no presets.")
    by_id = {}
    for index, preset in enumerate(presets):
        checked = validated_parameters(preset)
        if checked["id"] in by_id:
            raise ValueError("Bundled fantasy catalog contains a duplicate id.")
        by_id[checked["id"]] = index
    source_id = parameters["id"]
    if source_id not in by_id:
        raise ValueError(f"Unknown source preset {source_id!r}; export one from this workbench.")
    source = presets[by_id[source_id]]
    if parameters["mode"] != source["mode"]:
        raise ValueError("Recipe mode does not match its source preset.")
    target_id = source_id if new_id is None else valid_id(new_id)
    if new_id is not None and target_id in by_id:
        raise ValueError(f"Preset {target_id!r} already exists; omit --id to replace its source recipe.")
    result = deepcopy(catalog)
    selected = deepcopy(source)
    selected.update(parameters)
    selected["id"] = target_id
    if new_id is None:
        result["presets"][by_id[source_id]] = selected
    else:
        result["presets"].append(selected)
    return result, target_id


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, allow_nan=False, indent=2) + "\n").encode("utf-8")


def export_pack(recipe_file, output_file, new_id=None, force=False, catalog_file=CATALOG):
    output_file = Path(output_file).expanduser().resolve()
    if output_file.suffix.lower() != ".zip":
        raise ValueError("Output must be a .zip resource pack.")
    catalog, target_id = merged_catalog(read_json(recipe_file), read_json(catalog_file), new_id)
    metadata = {"pack": {"pack_format": 15, "description": f"Fantasy material: {target_id}"}}
    buffer = BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("pack.mcmeta", json_bytes(metadata))
        archive.writestr(RESOURCE, json_bytes(catalog))
    output_file.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation protects an existing pack even if another process creates it meanwhile.
    with output_file.open("wb" if force else "xb") as destination:
        destination.write(buffer.getvalue())
    command = '/give @s minecraft:diamond_sword{CosmicMaterial:{Material:"' + target_id + '",Mask:"blade"}}'
    return output_file, command


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipe", type=Path, required=True, help="JSON exported by the fantasy workbench.")
    parser.add_argument("--out", type=Path, required=True, help="Output resource pack ZIP.")
    parser.add_argument("--id", dest="new_id", help="Add a new lowercase preset id, preserving all existing presets.")
    parser.add_argument("--force", action="store_true", help="Replace an existing output ZIP.")
    args = parser.parse_args(argv)
    try:
        output, command = export_pack(args.recipe, args.out, args.new_id, args.force)
    except (ValueError, OSError, OverflowError) as error:
        print(f"Cannot export resource pack: {error}", file=sys.stderr)
        return 2
    print(f"Resource pack: {output}")
    print("Requires the updated Cosmic Material Lab mod on Forge 1.20.1.")
    print("Enable the pack and reload resources. A resource pack alone cannot register this renderer.")
    print(command)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
