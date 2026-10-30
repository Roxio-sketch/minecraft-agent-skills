"""Garden tour, wet/dry separation, openings, visibility and serialization."""
from pathlib import Path
import tempfile
import unittest

import numpy as np

from garden_components import make_bridge, make_garden_wall, line_of_sight
from generate_courtyard import can_support_dry_walk, walk_paths
from generate_garden import make_garden, export
from voxel_model import Model


class GardenComponents(unittest.TestCase):
    def test_moon_opening_lattice_and_sealed_wall(self):
        for radius in (3,4,6):
            wall,d=make_garden_wall(24,radius,-8,[12])
            m=Model((-24,0,-3),(24,16,3))
            m.box((-24,0,-3),(24,0,3),'minecraft:stone')
            m.paste(wall,conflict='replace')
            self.assertTrue(d['clearance_three_wide'])
            self.assertTrue(walk_paths(m,[-8,0,-2],{'other':[-8,0,2]})['all_targets_reached'])
            self.assertTrue(line_of_sight(m,[11.5,3.5,-2.5],[11.5,3.5,2.5])['visible'])
            self.assertFalse(line_of_sight(m,[12.5,3.5,-2.5],[12.5,3.5,2.5])['visible'])
            m.box((-8-radius,1,0),(-8+radius,2*radius+1,0),'minecraft:stone')
            self.assertFalse(walk_paths(m,[-8,0,-2],{'other':[-8,0,2]})['all_targets_reached'])
        for args in ((24,4,22,[]),(24,4,-8,[-8]),(24,4,-8,[12,13])):
            with self.assertRaises(ValueError):make_garden_wall(*args)

    def test_bridge_variants_and_broken_middle(self):
        for span,width,rise in ((5,3,0),(8,5,4),(16,3,3),(24,5,6)):
            m,d=make_bridge(span,width,rise)
            self.assertTrue(d['routes']['all_targets_reached'])
            self.assertEqual(d['deck_y_by_x'][span],[0,rise])
            m.box((0,-2,-(width+1)//2),(0,rise+3,(width+1)//2),'minecraft:air')
            self.assertFalse(walk_paths(m,[-span,0,0],{'exit':[span,0,0]})['all_targets_reached'])
        with self.assertRaises(ValueError):make_bridge(5,3,5)

    def test_liquids_and_thin_barriers_are_not_dry_floors(self):
        for state in ('minecraft:water[level=0]','minecraft:lava[level=0]',
                      'minecraft:oak_fence','minecraft:short_grass','minecraft:oak_door[half=lower]'):
            self.assertFalse(can_support_dry_walk(state))
            m=Model((0,-1,0),(6,5,0))
            m.box((0,-1,0),(6,0,0),'minecraft:stone')
            m.set(3,0,0,state)
            self.assertFalse(walk_paths(m,[0,0,0],{'end':[6,0,0]})['all_targets_reached'])


class GardenAssembly(unittest.TestCase):
    def test_sizes_seeds_and_dry_tours(self):
        for c in ({},{'width':84,'depth':80,'moon_radius':3,'tree_count':0},
                  {'width':140,'depth':120,'moon_radius':6,'bridge_width':5,'bridge_rise':5,'seed':9}):
            m,d=make_garden(c)
            self.assertTrue(all(d['checks'].values()))
            for path in d['routes']['routes_floor_xyz'].values():
                for x,y,z in path:
                    self.assertTrue(can_support_dry_walk(m.get(x,y,z)))
                    self.assertEqual(m.get(x,y+1,z),'minecraft:air')
                    self.assertEqual(m.get(x,y+2,z),'minecraft:air')
            self.assertTrue(d['water_cells_xyz'])
        for c in ({'width':85},{'depth':60},{'tree_count':4},{'moon_radius':2},{'unknown':True}):
            with self.assertRaises(ValueError):make_garden(c)

    def test_gate_closures_preserve_space_sequence(self):
        m,d=make_garden({})
        x,y,z=d['target_floors_xyz']['moon_gate'];r=d['moon_wall']['radius']
        m.box((x-r,1,z),(x+r,2*r+1,z),'minecraft:stone')
        result=walk_paths(m,d['start_floor_xyz'],{k:d['target_floors_xyz'][k] for k in ('forecourt','bridge_west','pavilion')})
        self.assertIn('forecourt',result['routes_floor_xyz'])
        self.assertEqual(set(result['unreachable']),{'bridge_west','pavilion'})
        m,d=make_garden({})
        front=-d['config']['depth']//2
        m.box((-2,1,front),(2,5,front),'minecraft:stone')
        result=walk_paths(m,d['start_floor_xyz'],{'forecourt':d['target_floors_xyz']['forecourt']})
        self.assertFalse(result['all_targets_reached'])

    def test_export_determinism_and_seed_variation(self):
        with tempfile.TemporaryDirectory() as folder:
            a=export({},folder,'garden',False)
            before=Path(folder,'garden.schem').read_bytes()
            b=export({},folder,'garden',False)
            self.assertTrue(b['design']['checks']['schematic_state_roundtrip'])
            self.assertEqual(before,Path(folder,'garden.schem').read_bytes())
            m,d=make_garden({'seed':3})
            self.assertNotEqual(len(d['water_cells_xyz']),len(a['design']['water_cells_xyz']))


if __name__=='__main__':unittest.main(verbosity=2)
