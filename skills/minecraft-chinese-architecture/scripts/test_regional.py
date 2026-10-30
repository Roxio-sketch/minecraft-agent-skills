"""Spatial differences, doors, structure and source-independent regional artifacts."""
from pathlib import Path
import tempfile
import unittest

import numpy as np

from generate_regional import make_regional, export
from generate_courtyard import walk_paths


class RegionalSpatialTests(unittest.TestCase):
    def test_stilt_lift_grid_and_stair_break(self):
        for lift in (2,4,8):
            m,d=make_regional({'kind':'stilt','parameters':{'lift':lift}})
            self.assertTrue(all(d['checks'].values()))
            self.assertEqual(d['target_floors_xyz']['interior'][1],lift+1)
            x,y,z=d['start_floor_xyz'];m.box((x-1,1,z+2),(x+1,lift+1,z+2),'minecraft:air')
            self.assertFalse(walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])['all_targets_reached'])

    def test_yurt_different_radii_and_closure(self):
        for radius,h,crown in ((8,3,1),(11,4,2),(18,6,3)):
            m,d=make_regional({'kind':'yurt','parameters':{'radius':radius,'wall_height':h,'crown_radius':crown}})
            self.assertTrue(all(d['checks'].values()))
            used=set(np.array(m.palette)[np.unique(m.blocks)].tolist())
            self.assertTrue(any('wool' in s for s in used));self.assertFalse(any('quartz' in s for s in used))
            m.box((-1,1,-radius),(1,3,-radius),'minecraft:stone')
            self.assertFalse(walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])['all_targets_reached'])

    def test_caves_cover_dividers_and_single_door_closure(self):
        for count,width in ((1,5),(3,7),(5,9)):
            m,d=make_regional({'kind':'cave','parameters':{'chambers':count,'chamber_width':width}})
            self.assertTrue(all(d['checks'].values()));self.assertEqual(len(d['vaults']),count)
            center=d['vaults'][0]['center_x'];m.box((center-1,1,-1),(center+1,3,0),'minecraft:stone')
            paths=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
            self.assertEqual(paths['unreachable'],['chamber_1'])

    def test_tianjing_open_court_drain_and_exterior_door(self):
        for params in ({},{'half_x':26,'half_z':24,'court_half_x':10,'court_half_z':9}):
            m,d=make_regional({'kind':'tianjing','parameters':params})
            self.assertTrue(all(d['checks'].values()));self.assertTrue(d['water_cells_xyz'])
            h=d['config']['parameters']['half_z'];m.box((-1,1,-h),(1,4,-h),'minecraft:stone')
            self.assertFalse(walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])['all_targets_reached'])

    def test_round_and_square_tulou_all_rooms_and_stair_gaps(self):
        for params in ({},{'shape':'square','radius':24,'court_radius':10,'storeys':2},
                       {'radius':38,'court_radius':22,'storeys':4,'storey_height':10}):
            m,d=make_regional({'kind':'tulou','parameters':params})
            self.assertTrue(all(d['checks'].values()))
            levels=d['config']['parameters']['storeys']
            self.assertEqual(len(d['routes']['routes_floor_xyz']),16*levels+levels-1)
            r=d['config']['parameters']['radius'];m.box((-2,1,-r),(2,4,-r+2),'minecraft:stone')
            self.assertFalse(walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])['all_targets_reached'])

    def test_serialization_and_parameter_rejections(self):
        with tempfile.TemporaryDirectory() as folder:
            for kind in ('stilt','yurt','cave','tianjing','tulou'):
                r=export({'kind':kind},folder,kind,False)
                self.assertTrue(r['design']['checks']['schematic_state_roundtrip'])
                self.assertFalse(r['design']['source_structure_reuse'])
        for config in ({'kind':'unverified'},{'kind':'stilt','parameters':{'lift':1}},
                       {'kind':'cave','parameters':{'chambers':2}},
                       {'kind':'tulou','parameters':{'court_radius':22}},
                       {'kind':'tulou','parameters':{'storey_height':7}},
                       {'kind':'yurt','palette':{'cloth':'minecraft:air'}},
                       {'kind':'cave','parameters':{'radius':8}}):
            with self.assertRaises(ValueError):make_regional(config)


if __name__=='__main__':unittest.main(verbosity=2)
