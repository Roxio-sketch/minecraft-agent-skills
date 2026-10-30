"""Courtyard orientation, placement and reachable-room checks."""
import tempfile
import unittest
from pathlib import Path

import numpy as np

from generate_courtyard import assemble, export, walk_paths
from voxel_model import Model


class CourtyardTests(unittest.TestCase):
    def test_inward_doors_and_connected_paths(self):
        model, d = assemble({})
        self.assertEqual({k:v['faces'] for k,v in d['parts'].items()},
                         {'main':'south','east_wing':'west','west_wing':'east','gate':'north'})
        self.assertTrue(d['routes']['all_targets_reached'])
        self.assertEqual(set(d['routes']['route_steps']),{'main','east_wing','west_wing','gate'})
        for name, path in d['routes']['routes_floor_xyz'].items():
            self.assertEqual(path[0], d['start_floor_xyz'])
            self.assertEqual(path[-1], d['target_floors_xyz'][name])
            for x,y,z in path:
                self.assertNotEqual(model.get(x,y,z),'minecraft:air')
                self.assertEqual(model.get(x,y+1,z),'minecraft:air')
                self.assertEqual(model.get(x,y+2,z),'minecraft:air')
            for a,b in zip(path,path[1:]):
                self.assertEqual(abs(a[0]-b[0])+abs(a[2]-b[2]),1)
                self.assertLessEqual(abs(a[1]-b[1]),1)

    def test_path_blocker_and_step_limit(self):
        m=Model((0,0,0),(6,5,0))
        m.box((0,0,0),(6,0,0),'minecraft:stone')
        result=walk_paths(m,[0,0,0],{'room':[6,0,0]})
        self.assertTrue(result['all_targets_reached'])
        m.box((3,1,0),(3,3,0),'minecraft:stone')
        self.assertFalse(walk_paths(m,[0,0,0],{'room':[6,0,0]})['all_targets_reached'])
        m.box((3,2,0),(3,3,0),'minecraft:air')
        self.assertTrue(walk_paths(m,[0,0,0],{'room':[6,0,0]})['all_targets_reached'])
        # Low roof above the preceding step blocks an upward head sweep.
        m.set(2,3,0,'minecraft:stone')
        self.assertFalse(walk_paths(m,[0,0,0],{'room':[6,0,0]})['all_targets_reached'])

    def test_variants_and_serialization(self):
        with tempfile.TemporaryDirectory() as folder:
            for i,c in enumerate([{}, {'court_width':36,'court_depth':40},
                                  {'court_width':24,'court_depth':24},
                                  {'main':{'bays_x':[7,7], 'door_bay':1}}]):
                result=export(c,folder,f'court-{i}',render=False)
                self.assertTrue(all(result['checks'].values()))
            before=Path(folder,'court-0.schem').read_bytes()
            export({},folder,'court-0',render=False)
            self.assertEqual(before,Path(folder,'court-0.schem').read_bytes())
        with self.assertRaises(ValueError): assemble({'court_width':25})
        with self.assertRaises(ValueError): assemble({'gate':{'kind':'hall'}})
        with self.assertRaises(ValueError): assemble({'main':{'roof':None}})
        # A wide wing that enters the main building must not be silently pasted.
        with self.assertRaises(ValueError): assemble({'wing':{'bays_x':[16]*7}})


if __name__=='__main__': unittest.main(verbosity=2)
