"""Terrain inputs, graded paths, reservations and full complex reachability."""
import copy
from pathlib import Path
import tempfile
import unittest

import numpy as np

from generate_complex import DEFAULT, make_complex, export
from terrain_planning import heightfield, grade_route


class TerrainPaths(unittest.TestCase):
    def test_heightfield_file_origin_and_immutability(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder,'site.npz');a=np.arange(40)[:,None]//4+np.zeros((40,40),dtype=int)
            np.savez_compressed(path,height_zx=a,origin_xz=[-20,-20]);before=path.read_bytes()
            result,source=heightfield([-20,-20],[40,40],{'heightmap':str(path)})
            np.testing.assert_array_equal(result,a);self.assertEqual(source['type'],'heightmap')
            result[:]=5;self.assertEqual(before,path.read_bytes())
            with self.assertRaises(ValueError):heightfield([0,0],[40,40],{'heightmap':str(path)})
            np.savez_compressed(path,height_zx=a.astype(float),origin_xz=[-20,-20])
            with self.assertRaises(ValueError):heightfield([-20,-20],[40,40],{'heightmap':str(path)})

    def test_grade_obstacle_footprint_and_fixed_heights(self):
        a=np.zeros((41,51),dtype=np.int64);a[:,30:]=6
        fixed=np.zeros_like(a,dtype=bool);fixed[17:24,3:8]=True;fixed[17:24,43:48]=True
        blocked=np.zeros_like(fixed);blocked[15:26,23:27]=True
        for width in (3,5,7):
            r=grade_route(a,fixed,blocked,[5,20,0],[45,20,6],width,8)
            self.assertFalse(np.any(r['mask']&blocked))
            self.assertTrue(np.all(r['heights'][r['mask']&fixed]==a[r['mask']&fixed]))
            self.assertEqual(r['route_local_xzy'][0],[5,20,0]);self.assertEqual(r['route_local_xzy'][-1],[45,20,6])
            self.assertLessEqual(r['max_step'],1)
            self.assertTrue(r['cut_cells']+r['fill_cells']>0)
        np.testing.assert_array_equal(a[:,30:],6)

    def test_fail_closed_on_blocked_or_impossible_route(self):
        a=np.zeros((31,31),dtype=int);fixed=np.zeros_like(a,dtype=bool);blocked=fixed.copy()
        blocked[:,15]=True
        with self.assertRaises(ValueError):grade_route(a,fixed,blocked,[5,15,0],[25,15,0])
        blocked[:]=False;a[:,16:]=12
        with self.assertRaises(ValueError):grade_route(a,fixed,blocked,[5,15,0],[25,15,12],max_grade=0)
        a[:]=0
        with self.assertRaisesRegex(ValueError,'budget'):grade_route(a,fixed,blocked,[5,15,0],[25,15,0],node_budget=1)
        with self.assertRaises(ValueError):grade_route(a,fixed,blocked,[0,0,0],[25,15,0])


class ComplexAssembly(unittest.TestCase):
    def test_default_all_storeys_and_original_heightmap(self):
        m,d,original,graded=make_complex({})
        self.assertTrue(all(d['checks'].values()))
        self.assertEqual(len(d['pads']),5);self.assertEqual(len(d['targets_floor_xyz']),9)
        self.assertTrue(np.any(original!=graded))
        self.assertGreater(d['earthwork']['net_cut_cells'],0)
        self.assertGreater(d['earthwork']['net_fill_cells'],0)
        for route in d['routes']['routes_floor_xyz'].values():
            for x,y,z in route:
                self.assertEqual(m.get(x,y+1,z),'minecraft:air')
                self.assertEqual(m.get(x,y+2,z),'minecraft:air')

    def test_flat_layout_and_heightmap_roundtrip(self):
        config={'terrain':{'base':-3,'grade_z':0,'wave':0},'path_width':3}
        with tempfile.TemporaryDirectory() as folder:
            a=export(config,folder,'flat',False);blob=Path(folder,'flat.schem').read_bytes()
            self.assertTrue(all(a['design']['checks'].values()))
            self.assertEqual(a['design']['earthwork']['changed_columns'],0)
            config['terrain']={'heightmap':str(Path(folder,'flat.heightfields.npz'))}
            b=export(config,folder,'flat-loaded',False)
            self.assertEqual(b['nonair_voxels'],a['nonair_voxels'])
            self.assertEqual(b['design']['terrain_source']['type'],'heightmap')
            # Names are stored in schematic metadata, so compare states rather than file names.
            m1=np.load(Path(folder,'flat.npz'));m2=np.load(Path(folder,'flat-loaded.npz'))
            np.testing.assert_array_equal(m1['blocks'],m2['blocks']);m1.close();m2.close()

    def test_invalid_assembly_does_not_silently_overlap_or_float(self):
        overlap=copy.deepcopy(DEFAULT['components']);overlap[1]['position_xz']=overlap[0]['position_xz']
        for config in ({'components':overlap},{'max_pad_cut_fill':0},{'path_width':7,'pad_margin':5},
                       {'connections':[['entrance','unknown']]},{'components':[]}):
            with self.assertRaises(ValueError):make_complex(config)


if __name__=='__main__':unittest.main(verbosity=2)
