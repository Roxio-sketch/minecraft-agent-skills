"""Serial-court reachability, gate bottlenecks, offsets and export behavior."""
from pathlib import Path
import tempfile
import unittest

from generate_multi_court import assemble, export
from generate_courtyard import walk_paths


class MultiCourtTests(unittest.TestCase):
    def test_one_to_four_courts_and_offset_entrances(self):
        for c in ({'court_depths':[28],'court_width':28,'outer_gate_offset_x':0,'wing':{'bays_x':[4,6,4]}},
                  {}, {'court_depths':[32,40,32],'outer_gate_offset_x':-8,'wall_height':3,'gate':{'bays_z':[4,4]}},
                  {'court_depths':[32,40,48,64],'court_width':60,'outer_gate_offset_x':12,'wall_height':6,'row_gap':1}):
            with self.subTest(config=c):
                m,d=assemble(c)
                self.assertEqual(len(d['courts']),len(d['config']['court_depths']))
                self.assertEqual(len(d['gates_south_to_north']),len(d['courts']))
                self.assertEqual(d['gates_south_to_north'][0]['center_x'],d['config']['outer_gate_offset_x'])
                self.assertTrue(d['routes']['all_targets_reached'])
                self.assertEqual(len(d['main_route_gate_crossing_indexes']),len(d['courts']))
                for path in d['routes']['routes_floor_xyz'].values():
                    for x,y,z in path:
                        self.assertNotEqual(m.get(x,y,z),'minecraft:air')
                        self.assertEqual(m.get(x,y+1,z),'minecraft:air')
                        self.assertEqual(m.get(x,y+2,z),'minecraft:air')

    def test_closing_inner_gate_keeps_first_court_but_blocks_main_hall(self):
        m,d=assemble({});g=d['gates_south_to_north'][1]
        lo,hi=g['clear_between_posts_x']
        m.box((lo,0,g['z']),(hi,int(m.maximum[1]),g['z']),'minecraft:stone')
        result=walk_paths(m,d['start_floor_xyz'],{key:d['target_floors_xyz'][key] for key in ('court_1','main_hall')})
        self.assertIn('court_1',result['routes_floor_xyz'])
        self.assertNotIn('main_hall',result['routes_floor_xyz'])

    def test_closing_outer_gate_blocks_every_courtyard(self):
        m,d=assemble({});g=d['gates_south_to_north'][0];lo,hi=g['clear_between_posts_x']
        m.box((lo,0,g['z']),(hi,int(m.maximum[1]),g['z']),'minecraft:stone')
        r=walk_paths(m,d['start_floor_xyz'],{'first':d['target_floors_xyz']['court_1'],'main':d['target_floors_xyz']['main_hall']})
        self.assertEqual(r['routes_floor_xyz'],{})

    def test_exports_and_invalid_configurations(self):
        with tempfile.TemporaryDirectory() as directory:
            r=export({},directory,'courts',False)
            self.assertTrue(all(r['design']['checks'].values()))
            before=Path(directory,'courts.schem').read_bytes()
            export({},directory,'courts',False)
            self.assertEqual(before,Path(directory,'courts.schem').read_bytes())
        for c in ({'court_depths':[]},{'court_depths':[32]*5},{'court_depths':[27]},
                  {'court_width':31},{'outer_gate_offset_x':19},{'gate':{'kind':'hall'}},
                  {'gate':{'platform_height':2}},{'wing':{'bays_x':[16]*5}}):
            with self.subTest(config=c),self.assertRaises(ValueError):assemble(c)


if __name__=='__main__':unittest.main(verbosity=2)
