"""Compare supported shapes to axis-aligned Java client model JSON, read-only.

Supply an official Java 1.21.4 client jar. Assets stay in that jar and are not
copied to the skill. This checks solid model element unions, not textures/faces.
"""
import argparse
import hashlib
import itertools
import json
from pathlib import Path
import zipfile

import numpy as np

from block_shapes import shape_for
from voxel_model import join_state


def run(client_jar, out):
    models = {}
    archive = zipfile.ZipFile(client_jar)
    def read(namespace, category, name):
        return json.loads(archive.read(f"assets/{namespace}/{category}/{name}.json"))
    def model(name):
        if name in models: return models[name]
        namespace, key = name.split(":") if ":" in name else ("minecraft", name)
        definition = read(namespace, "models", key)
        if "elements" not in definition and "parent" in definition:
            definition = {**model(definition["parent"]), **definition}
        models[name] = definition
        return definition
    def matches(when, props):
        if "OR" in when: return any(matches(v, props) for v in when["OR"])
        if "AND" in when: return all(matches(v, props) for v in when["AND"])
        return all(props.get(k) in v.split("|") for k, v in when.items())
    def render_part(part):
        if isinstance(part, list): part = part[0]
        a = np.zeros((16, 16, 16), dtype=bool)
        for element in model(part["model"]).get("elements", []):
            if "rotation" in element: raise ValueError("Rotated model elements outside this verifier")
            lo, hi = element["from"], element["to"]
            if any(int(v) != v for v in lo + hi): raise ValueError("Non-integer model bounds")
            x0, y0, z0 = map(int, lo); x1, y1, z1 = map(int, hi)
            a[y0:y1, z0:z1, x0:x1] = True
        x_angle = part.get("x", 0)
        if x_angle == 180: a = a[::-1, ::-1, :]
        elif x_angle: raise ValueError("Only X=0/180 used by selected shape fixtures")
        angle = part.get("y", 0)
        if angle % 90: raise ValueError("Non-quarter-turn model rotation")
        return np.rot90(a, -(angle // 90), axes=(1, 2))
    def vanilla(name, props):
        blockstate = read("minecraft", "blockstates", name)
        a = np.zeros((16, 16, 16), dtype=bool)
        if "variants" in blockstate:
            choices = [value for key, value in blockstate["variants"].items()
                       if matches(dict(pair.split("=") for pair in key.split(",") if pair), props)]
            if len(choices) != 1: raise ValueError(f"Expected one variant for {name}: {props}")
            a |= render_part(choices[0])
        else:
            for part in blockstate["multipart"]:
                if matches(part.get("when", {}), props): a |= render_part(part["apply"])
        return a
    cases = []
    for facing, half, shape in itertools.product(("north", "east", "south", "west"), ("bottom", "top"), ("straight", "inner_left", "inner_right", "outer_left", "outer_right")):
        cases.append(("oak_stairs", {"facing": facing, "half": half, "shape": shape, "waterlogged": "false"}))
    for kind in ("bottom", "top", "double"):
        cases.append(("oak_slab", {"type": kind, "waterlogged": "false"}))
    # Iron bars are zero-thickness cutout planes; a solid envelope deliberately
    # does not claim to match their textured model. Exclude that approximation.
    for name in ("oak_fence", "glass_pane"):
        for values in itertools.product(("true", "false"), repeat=4):
            cases.append((name, dict(zip(("north", "east", "south", "west"), values))))
    for up in ("true", "false"):
        for values in itertools.product(("none", "low", "tall"), repeat=4):
            cases.append(("cobblestone_wall", {"up": up, **dict(zip(("north", "east", "south", "west"), values))}))
    for layers in range(1, 9): cases.append(("snow", {"layers": str(layers)}))
    cases.append(("red_carpet", {}))
    failures = []
    for name, props in cases:
        state = join_state("minecraft:" + name, props)
        actual, _ = shape_for(state)
        expected = vanilla(name, props)
        if not np.array_equal(actual, expected):
            failures.append({"state": state, "differing_subvoxels": int(np.count_nonzero(actual != expected)),
                             "actual_solid_subvoxels": int(actual.sum()), "vanilla_solid_subvoxels": int(expected.sum())})
    archive.close()
    report = {"schema": "vanilla-shape-verification/1", "client_jar": str(Path(client_jar).resolve()),
              "client_sha1": hashlib.sha1(Path(client_jar).read_bytes()).hexdigest(),
              "cases": len(cases), "passed": len(cases) - len(failures), "failures": failures,
              "excluded": ["iron_bars: zero-thickness textured planes; preview uses a solid envelope"],
              "scope": "Visible axis-aligned model element unions at 1/16 resolution; not collision, face culling, texture, fluid, or live game validation."}
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    Path(out).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("cases", "passed", "failures")}))
    if failures: raise AssertionError(f"{len(failures)} shape mismatches")
    return report


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--client-jar", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()
    run(args.client_jar, args.out)
