"""Access, furnishing, enclosure and editable-program behavior."""
import copy
from pathlib import Path
import tempfile
import unittest

import numpy as np

from generate_courtyard import walk_paths
from generate_functional import KINDS, TOWER, make_functional, program_config, export
from generate_inhabited import USES, make_inhabited


class FurnishedInteriors(unittest.TestCase):
    def test_uses_have_accessible_actual_fixtures(self):
        for use in USES:
            m, d = make_inhabited({'use': use})
            self.assertTrue(d['routes']['all_targets_reached'])
            self.assertEqual(len(d['fixtures']), 4)
            for fixture in d['fixtures']:
                x, y, z = fixture['use_floor_xyz']
                self.assertEqual(m.get(x, y + 1, z), 'minecraft:air')
                for xx, yy, zz, state in fixture['cells']:
                    self.assertEqual(m.get(xx, yy, zz).split('[')[0], state.split('[')[0])

    def test_tower_furniture_does_not_trap_stair_landings(self):
        for use in ('inn', 'library', 'bell'):
            m, d = make_inhabited({'kind': 'tower', 'use': use, 'building': TOWER})
            self.assertTrue(d['routes']['all_targets_reached'])
            for flight in d['base_design']['flights']:
                for x, y, z in flight['stair_cells_xyz']:
                    self.assertEqual(m.get(x, y + 1, z), 'minecraft:air')
                    self.assertEqual(m.get(x, y + 2, z), 'minecraft:air')
        with self.assertRaises(ValueError): make_inhabited({'use': 'invented'})
        with self.assertRaises(ValueError): make_inhabited({'furniture_per_zone': True})


class FunctionalPrograms(unittest.TestCase):
    def test_distinct_programs_and_all_rotated_interiors(self):
        role_sets = set()
        for kind in KINDS:
            m, d = make_functional({'kind': kind})
            self.assertTrue(all(d['checks'].values()))
            role_sets.add(tuple(sorted(d['program']['roles'].values())))
            self.assertGreater(len(d['target_floors_xyz']), 20)
        self.assertEqual(len(role_sets), len(KINDS))

    def test_sealing_outer_gate_blocks_all_interiors(self):
        m, d = make_functional({'kind': 'ancestral_hall'})
        x, y, z = d['boundary']['gate_floor_xyz']; h = d['boundary']['height']
        m.box((x - 2, y + 1, z), (x + 2, y + h, z), 'minecraft:stone_bricks')
        paths = walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz'])
        self.assertFalse(paths['all_targets_reached'])
        self.assertEqual(len(paths['unreachable']), len(d['target_floors_xyz']))

    def test_reproducible_export_and_explicit_component_overrides(self):
        cfg = {'kind': 'market_street', 'shops_per_side': 1, 'spacing': 60, 'enclosure': False}
        before = copy.deepcopy(cfg)
        with tempfile.TemporaryDirectory() as folder:
            r = export(cfg, folder, 'street', False)
            a = Path(folder, 'street.schem').read_bytes()
            export(cfg, folder, 'street', False)
            self.assertEqual(a, Path(folder, 'street.schem').read_bytes())
            self.assertTrue(Path(folder, 'street.complex.json').exists())
            self.assertTrue(r['design']['checks']['schematic_state_roundtrip'])
        self.assertEqual(cfg, before)
        with self.assertRaises(ValueError): program_config({'overrides': {'absent': {}}})
        with self.assertRaises(ValueError): make_functional({'kind': 'academy', 'overrides': {'library': {'position_xz': [0, 64]}}})


if __name__ == '__main__': unittest.main()
