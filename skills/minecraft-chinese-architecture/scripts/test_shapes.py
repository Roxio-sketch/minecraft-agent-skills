"""Shape volumes, face coverage, neighboring partial blocks and rendering smoke."""
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from block_shapes import boundary_faces, internal_faces, rectangles, shape_for
from render_shapes import render
from voxel_model import Model, rotate_state


class ShapeTests(unittest.TestCase):
    def test_mod_suffix_does_not_claim_vanilla_shape(self):
        for state in ('example:custom_slab[type=bottom]', 'example:custom_wall[up=false]', 'example:custom_stairs[facing=east]'):
            shape,status=shape_for(state)
            self.assertEqual(status,'cube_fallback')
            self.assertEqual(int(shape.sum()),4096)

    def test_stair_volume_orientation_and_inverse(self):
        for kind, volume in (("straight", 3072), ("inner_left", 3584), ("inner_right", 3584), ("outer_left", 2560), ("outer_right", 2560)):
            for half in ("bottom", "top"):
                state = f"minecraft:oak_stairs[facing=north,half={half},shape={kind},waterlogged=false]"
                a, status = shape_for(state)
                self.assertEqual(int(a.sum()), volume)
                for turn in range(4):
                    b, _ = shape_for(rotate_state(state, turn))
                    np.testing.assert_array_equal(np.rot90(a, -turn, axes=(1, 2)), b)
                self.assertFalse(a.flags.writeable)

    def test_rectangles_cover_exact_mask(self):
        rng = np.random.default_rng(123)
        for _ in range(24):
            a = rng.random((16, 16)) > .45
            coverage = np.zeros((16, 16), dtype=np.uint8)
            for x0, y0, x1, y1 in rectangles(a): coverage[y0:y1, x0:x1] += 1
            np.testing.assert_array_equal(coverage, a.astype(np.uint8))

    def test_partial_block_occlusion(self):
        bottom = "minecraft:stone_slab[type=bottom,waterlogged=false]"
        top = "minecraft:stone_slab[type=top,waterlogged=false]"
        cube = "minecraft:stone"
        air = "minecraft:air"
        self.assertEqual(boundary_faces(bottom, cube, "east"), ())
        self.assertEqual(boundary_faces(top, bottom, "up"), ())
        faces = boundary_faces(bottom, top, "east")
        self.assertEqual(sum((r[2] - r[0]) * (r[3] - r[1]) for _, _, r in faces), 128)
        self.assertIn(("up", 8, (0, 0, 16, 16)), internal_faces(bottom))
        self.assertEqual(len(boundary_faces(cube, air, "up")), 1)

    def test_views_and_fallback_reporting(self):
        m = Model((0, 0, 0), (3, 3, 3))
        m.set(0, 0, 0, "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]")
        m.set(1, 0, 0, "minecraft:stone_slab[type=bottom,waterlogged=false]")
        m.set(2, 0, 0, "minecraft:chest[facing=north,type=single,waterlogged=false]")
        m.set(3, 0, 0, "minecraft:iron_bars[east=true,north=false,south=false,west=true,waterlogged=false]")
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder, "sample.npz"); m.save(path)
            for view in ("isometric", "front", "side", "top"):
                png = Path(folder, f"{view}.png")
                result = render(path, png, view=view, size=(480, 360))
                self.assertEqual(result["occupied_voxels"], 4)
                self.assertEqual(result["cube_fallback_voxels"], 1)
                self.assertEqual(sum(result["solid_envelope_approximations"].values()), 1)
                self.assertGreater(result["faces_rendered"], 0)
                with Image.open(png) as image:
                    self.assertEqual(image.size, (480, 360))
            rotated = render(path, Path(folder, 'rotated.png'), camera_turns=2, size=(480,360))
            self.assertEqual(rotated['camera_quarter_turns'], 2)
            self.assertEqual(rotated['occupied_voxels'],4)


if __name__ == "__main__": unittest.main(verbosity=2)
