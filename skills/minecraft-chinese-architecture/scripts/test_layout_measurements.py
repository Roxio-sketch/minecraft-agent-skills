"""Observable coordinates, material filters and imperfect paired components."""
import json
from pathlib import Path
import tempfile
import unittest

import numpy as np

from measure_layout import measure, paired_doors, query
from render_sections import slice_plane
from voxel_model import Model


class LayoutMeasurements(unittest.TestCase):
    def test_local_coordinates_and_explicit_patterns(self):
        m = Model((-4, -2, 7), (4, 6, 12))
        m.box((-2, 0, 8), (1, 2, 10), 'minecraft:stone')
        m.set(2, 0, 8, 'minecraft:oak_stairs[waterlogged=false]')
        m.set(3, 0, 8, 'minecraft:water[level=0]')
        r = query(m, [-4, -2, 7, 4, 6, 12], r'^minecraft:stone$')
        self.assertEqual(r['matched_cells'], 36)
        self.assertEqual(r['bounds_xyz_inclusive'], [-2, 0, 8, 1, 2, 10])
        self.assertEqual(r['layers_y'], {0:12, 1:12, 2:12})
        self.assertEqual(query(m, [-4,-2,7,4,6,12], r'^minecraft:water(?:\[|$)')['matched_cells'], 1)
        for box in ([-5,0,8,1,2,10], [1,0,8,-2,2,10], [True,0,8,1,2,10]):
            with self.assertRaises(ValueError): query(m, box, 'stone')
        self.assertIsNone(query(m, [-4,-2,7,4,6,12], '^absent:material$')['bounds_xyz_inclusive'])

    def test_matching_door_halves_and_orphans(self):
        m = Model((-4,-2,7),(4,6,12))
        for x in (-1,0):
            for y,half in ((0,'lower'),(1,'upper')):
                m.set(x,y,8,f'minecraft:oak_door[facing=north,half={half},hinge=left,open=false,powered=false]')
        m.set(2,0,9,'example:oak_door[half=lower,open=false]')
        m.set(2,1,9,'example:oak_door[half=upper,open=true]')
        r = paired_doors(m)
        self.assertEqual(r['matched_pairs'],2)
        self.assertEqual(len(r['unpaired_cells']),2)
        self.assertEqual(r['pairs'][0]['lower_xyz'],[-1,0,8])
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp);m.save(p/'sample.npz')
            (p/'sample.import.json').write_text(json.dumps({'sha256':'test-source'}))
            a={'schema':'architecture-layout-annotations/1','samples':[{'id':'a','source':'test','model':'sample.npz','source_sha256':'test-source','queries':[]}]}
            self.assertEqual(measure(p,a)['samples'][0]['doors']['matched_pairs'],2)
            a['samples'][0]['source_sha256']='wrong'
            with self.assertRaises(ValueError):measure(p,a)

    def test_slice_axes_with_negative_origin(self):
        a=np.arange(3*4*5).reshape(3,4,5);o=np.array([-4,-2,7])
        plane,meta=slice_plane(a,o,'x',-2)
        np.testing.assert_array_equal(plane,a[::-1,:,2])
        self.assertEqual(meta['top_left_xyz'],[-2,0,7])
        np.testing.assert_array_equal(slice_plane(a,o,'y',-1)[0],a[1])
        np.testing.assert_array_equal(slice_plane(a,o,'z',8)[0],a[::-1,1,:])
        with self.assertRaises(ValueError):slice_plane(a,o,'z',11)


if __name__ == '__main__':unittest.main(verbosity=2)
