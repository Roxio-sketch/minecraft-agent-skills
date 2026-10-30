"""Routes, elevation, floor/eave distinction and underground containment."""
import unittest
import numpy as np

from generate_courtyard import walk_paths
from generate_infrastructure import make_infrastructure


class InfrastructureTests(unittest.TestCase):
    def test_roofed_bridges_keep_entire_width_clear(self):
        for width, rise in ((3, 0), (3, 3), (5, 5)):
            m, d = make_infrastructure({'kind': 'covered_bridge', 'parameters': {'clear_width': width, 'rise': rise, 'half_span': 18}})
            self.assertTrue(all(d['checks'].values()))
            self.assertGreater(m.maximum[1], rise + 5)

    def test_ascending_descending_and_turning_galleries(self):
        for path, width in (([[0, 2, 0], [30, 8, 0]], 3),
                            ([[0, 12, 0], [0, 6, -30], [-30, 2, -30]], 5),
                            ([[0, 2, 0], [0, 7, 30], [30, 12, 30]], 7)):
            m, d = make_infrastructure({'kind': 'hillside_gallery', 'parameters': {'path_xyz': path, 'clear_width': width}})
            self.assertTrue(d['checks']['planned_width_headroom'])
            for x, y, z in d['clear_band_floor_xyz']:
                self.assertNotEqual(m.get(x, y, z), 'minecraft:air')
        with self.assertRaises(ValueError): make_infrastructure({'kind': 'hillside_gallery', 'parameters': {'path_xyz': [[0, 2, 0], [10, 3, 10]]}})

    def test_irrigated_terraces_and_crop_soil(self):
        for width, count, rise in ((12, 2, 1), (18, 3, 2), (24, 5, 3)):
            m, d = make_infrastructure({'kind': 'terraced_farm', 'parameters': {'half_width': width, 'levels': count, 'level_rise': rise}})
            self.assertEqual(len(d['target_floors_xyz']), count)
            for x, y, z in d['crop_soil_xyz']:
                self.assertTrue(m.get(x, y, z).startswith('minecraft:farmland'))
                self.assertEqual(m.get(x, y + 1, z), 'minecraft:wheat[age=7]')

    def test_underground_rooms_and_sealed_descent(self):
        for depth in (6, 9, 16):
            m, d = make_infrastructure({'kind': 'tomb', 'parameters': {'depth': depth}})
            self.assertTrue(d['checks']['underground_cover'])
            self.assertTrue(all(pt[1] == -depth for pt in d['target_floors_xyz'].values()))
            z = d['descent_front_z'] + 2
            m.box((-1, -1, z), (1, 3, z), 'minecraft:stone_bricks')
            self.assertFalse(walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz'])['all_targets_reached'])

    def test_dense_eaves_are_not_counted_as_storeys(self):
        for mode, levels in (('storeyed', 2), ('storeyed', 5), ('dense_eaves', 2), ('dense_eaves', 7)):
            m, d = make_infrastructure({'kind': 'pagoda', 'parameters': {'mode': mode, 'levels': levels}})
            self.assertEqual(d['actual_accessible_floors'], levels if mode == 'storeyed' else 1)
            self.assertTrue(d['routes']['all_targets_reached'])
        with self.assertRaises(ValueError): make_infrastructure({'kind': 'pagoda', 'parameters': {'mode': 'storeyed', 'levels': 7}})

    def test_fort_corner_lookouts_and_gate_barrier(self):
        for height in (8, 14):
            m, d = make_infrastructure({'kind': 'fort', 'parameters': {'wall_height': height}})
            self.assertEqual(len([k for k in d['target_floors_xyz'] if k.startswith('lookout_')]), 4)
            z = d['gate_z']; m.box((-2, 1, z), (2, 6, z), 'minecraft:stone_bricks')
            self.assertFalse(walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz'])['all_targets_reached'])


if __name__ == '__main__': unittest.main()
