"""Meaningful file, geometry, version and scale checks for the unified workflow."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import nbtlib as n
import numpy as np

from apply_finish import make_finish, export as finish_export
from architecture import build_component, generate, load_config
from calibrate_reference import calibrate
from compose_scene import make_scene
from generate_detail import make_detail, DEFAULTS as DETAILS
from generate_settlement import make_settlement
from import_legacy_schematic import import_legacy
from merge_litematic import merge_regions
from test_import_structure import save, packed
from validate_registry import validate, retarget, complete_registry
from voxel_model import Model


class DetailedComponents(unittest.TestCase):
    def test_detail_primitives_and_single_floor_double_eaves(self):
        for kind in DETAILS:
            m, d = make_detail({'kind': kind}); self.assertTrue(all(d['checks'].values()))
        for r in (2, 5):
            m, d = make_detail({'kind': 'tile_course', 'parameters': {'radius': r}})
            self.assertTrue(d['checks']['face_connected_geometry'])
        m, d = make_detail({'kind': 'double_eave_hall'})
        self.assertEqual(d['actual_accessible_floors'], 1); self.assertEqual(d['eave_bands'], 2)

    def test_column_profiles_and_declared_lean(self):
        for profile in ('straight', 'taper', 'entasis'):
            m, d = make_detail({'kind': 'column_base', 'parameters': {'profile': profile, 'inclination': 4}})
            self.assertEqual(d['sections'][-1]['center_x'], 4)
            self.assertTrue(all(d['checks'].values()))
        with self.assertRaises(ValueError): make_detail({'kind': 'column_base', 'parameters': {'height': 3}})


class SettlementSpaces(unittest.TestCase):
    def test_accessible_roof_terraces(self):
        for floors in (1, 2, 3):
            m, d = make_settlement({'kind': 'flat_roof_house', 'parameters': {'enclosed_floors': floors}})
            self.assertTrue(d['routes']['all_targets_reached'])
            self.assertEqual(d['roof_terrace_y'], 1 + 8 * floors)
            used = {m.palette[int(i)].split('[')[0] for i in np.unique(m.blocks)}
            self.assertNotIn('minecraft:deepslate_tile_stairs', used)
            for level in d['enclosed_level_envelopes']:
                y = level['floor_y'] + 6; hx, hz = level['half_xz']
                # Former eaves must become an enclosed wall, not an open band
                # around every supposed indoor floor after roof removal.
                for x, z in ((0, -hz), (0, hz), (-hx, 0), (hx, 0)):
                    self.assertNotEqual(m.get(x, y, z), 'minecraft:air')

    def test_three_sided_courts_and_offset_gate_seal(self):
        from generate_courtyard import walk_paths
        for x in (-20, -14, 14, 20):
            m, d = make_settlement({'kind': 'three_sided_court', 'parameters': {'gate_offset': x}})
            self.assertEqual(d['house_count'], 3)
            z = d['entry_z']; m.box((x - 1, 0, z), (x + 1, 6, z), 'minecraft:stone_bricks')
            self.assertFalse(walk_paths(m, d['start_floor_xyz'], d['target_floors_xyz'])['all_targets_reached'])

    def test_waterfront_homes_and_dry_bridge(self):
        for count in (1, 3):
            m, d = make_settlement({'kind': 'waterfront', 'parameters': {'houses_per_bank': count}})
            self.assertEqual(len(d['placements']), count * 2 + 1)
            self.assertTrue(d['routes']['all_targets_reached'])


class FreeComposition(unittest.TestCase):
    def config(self):
        return {'schema': 'chinese-scene/1', 'boxes': [{'min': [-25, -1, -25], 'max': [25, -1, 25], 'block': 'minecraft:stone'}],
                'components': [{'id': 'hall', 'config': {'schema': 'chinese-building/1'}, 'at': [0, 0, 0], 'turns': 1}],
                'routes': {'start': [20, -1, 0], 'targets': {'interior': 'hall.inside'}}}
    def test_rotated_entrance_contract_and_overlap_rejection(self):
        m, d = make_scene(self.config()); self.assertTrue(d['checks']['requested_routes_reachable'])
        bad = self.config(); duplicate = copy.deepcopy(bad['components'][0]); duplicate['id'] = 'overlap'; duplicate['turns'] = 0
        bad['components'].append(duplicate)
        with self.assertRaises(ValueError): make_scene(bad)

    def test_every_component_adapter_returns_routes_or_explicit_geometry(self):
        for schema in ('chinese-building/1', 'chinese-tower/1', 'chinese-corridor/1', 'chinese-regional/1', 'chinese-landmark/1', 'chinese-inhabited/1', 'chinese-detail/1'):
            m, d = build_component({'schema': schema})
            self.assertTrue(np.any(m.blocks)); self.assertIn('start_floor_xyz', d)

    def test_site_composition_accepts_all_documented_entrance_contracts(self):
        from generate_complex import make_complex
        cases = {'building': {}, 'tower': {}, 'regional': {'kind': 'cave'},
                 'landmark': {'kind': 'city_gate'}, 'garden': {}, 'courtyard': {}, 'multi_court': {},
                 'inhabited': {'kind': 'tower', 'use': 'library'},
                 'infrastructure': {'kind': 'fort'}, 'settlement': {'kind': 'flat_roof_house'}}
        for kind, config in cases.items():
            schema = 'chinese-' + kind.replace('_', '-') + '/1'
            part, _ = build_component({'schema': schema, **config})
            rotated = part.rotated(1); lo = rotated.origin[[0, 2]] - 14; hi = rotated.maximum[[0, 2]] + 14
            with self.subTest(kind=kind):
                m, d, _, _ = make_complex({'origin_xz': lo.tolist(), 'size_xz': (hi-lo+1).tolist(),
                    'terrain': {'base': 2, 'grade_z': 0, 'wave': 0, 'hills': []}, 'path_width': 3,
                    'components': [{'id': 'sample', 'kind': kind, 'config': config, 'position_xz': [0, 0], 'turns': 1}],
                    'entrance_xz': (lo+3).tolist(), 'connections': [['entrance', 'sample.entry']]})
                self.assertTrue(all(d['checks'].values()))

    def test_relative_input_and_source_attribution(self):
        from import_structure import read_nbt
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); m = Model([0, 0, 0], [2, 2, 2]); m.set(1, 0, 1, 'minecraft:stone'); m.save(root/'source.npz')
            (root/'source.import.json').write_text(json.dumps({'declared_author': 'Example source author', 'source_sha256': 'a' * 64}), encoding='utf8')
            before = (root/'source.npz').read_bytes()
            cfg = {'schema': 'chinese-scene/1', 'components': [{'id': 'original', 'model_file': 'source.npz'}]}
            (root/'scene.json').write_text(json.dumps(cfg), encoding='utf8'); resolved = load_config(root/'scene.json')
            self.assertTrue(Path(resolved['components'][0]['model_file']).samefile(root/'source.npz'))
            r = generate(resolved, root/'out', 'copy', False)
            self.assertEqual(json.loads((root/'out/copy.config.json').read_text('utf8')), resolved)
            self.assertTrue(r['design']['source_structure_reuse'])
            self.assertNotEqual(read_nbt(root/'out/copy.schem')['Metadata']['Author'], 'Original procedural example')
            self.assertEqual(r['design']['inputs'][0]['receipts'][0]['declarations']['declared_author'], 'Example source author')
            with self.assertRaisesRegex(ValueError, 'replace an input'):
                generate(resolved, root, 'source', False)
            self.assertEqual(before, (root/'source.npz').read_bytes())
            finished = finish_export({'schema': 'chinese-finish/1', 'model_file': str(root/'out/copy.npz'),
                                     'rules': [{'pattern': '^minecraft:stone$', 'state': 'minecraft:granite'}]}, root/'finished', 'result', False)
            inherited = finished['design']['source_model']['receipts'][0]['declarations']['inputs'][0]
            self.assertEqual(inherited['receipts'][0]['declarations']['declared_author'], 'Example source author')


class MaterialFinishes(unittest.TestCase):
    def test_seeded_finish_preserves_source_and_shapes(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder); m = Model([-4, -2, -4], [4, 2, 4]); m.box([-4, -2, -4], [4, 0, 4], 'minecraft:stone_bricks'); m.save(p/'source.npz')
            before = (p/'source.npz').read_bytes()
            cfg = {'schema': 'chinese-finish/1', 'model_file': str(p/'source.npz'), 'seed': 0xffffffff,
                   'rules': [{'pattern': 'stone_bricks', 'state': 'minecraft:mossy_stone_bricks', 'fraction': .5}]}
            a, ad = make_finish(cfg); b, bd = make_finish(cfg); np.testing.assert_array_equal(a.blocks, b.blocks)
            self.assertGreater(ad['changed_voxels'], 0)
            np.testing.assert_array_equal(a.blocks != 0, m.blocks != 0)
            cfg['seed'] = 12; c, cd = make_finish(cfg); self.assertFalse(np.array_equal(a.blocks, c.blocks))
            finish_export(cfg, p/'out', 'finished', False); self.assertEqual(before, (p/'source.npz').read_bytes())
            with self.assertRaises(ValueError): finish_export(cfg, p, 'source', False)

    def test_shape_changing_replacements_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder)/'source.npz'; m = Model([0, 0, 0], [0, 2, 0]); m.set(0, 0, 0, 'minecraft:stone_brick_slab[type=bottom]'); m.save(p)
            cfg = {'schema': 'chinese-finish/1', 'model_file': str(p), 'rules': [{'pattern': 'slab', 'state': 'minecraft:stone'}]}
            with self.assertRaises(ValueError): make_finish(cfg)


class ExtraFormats(unittest.TestCase):
    def region(self, pos, size, values):
        return n.Compound({'Position': n.Compound({k: n.Int(v) for k, v in zip('xyz', pos)}),
            'Size': n.Compound({k: n.Int(v) for k, v in zip('xyz', size)}),
            'BlockStatePalette': n.List[n.Compound]([n.Compound({'Name': n.String(v)}) for v in ('minecraft:air', 'minecraft:stone', 'minecraft:oak_planks')]),
            'BlockStates': packed(values, 2)})
    def test_regions_keep_negative_positions_and_explicit_precedence(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder)/'multi.litematic'
            root = {'Version': n.Int(6), 'MinecraftDataVersion': n.Int(4189), 'Regions': n.Compound({
                'A': self.region([-2, 0, 0], [-2, 1, 1], [1, 1]), 'B': self.region([-2, 0, 0], [2, 1, 1], [2, 0])})}
            save(p, root); before = p.read_bytes()
            with self.assertRaises(ValueError): merge_regions(p, ['A', 'B'])
            m, d = merge_regions(p, ['A', 'B'], conflict='replace')
            self.assertEqual(m.origin.tolist(), [-3, 0, 0]); self.assertEqual(m.get(-2, 0, 0), 'minecraft:oak_planks')
            self.assertEqual(m.get(-3, 0, 0), 'minecraft:stone'); self.assertEqual(before, p.read_bytes())
            with self.assertRaises(ValueError): merge_regions(p, ['A', 'A'])

    def test_numeric_schematic_high_ids_metadata_and_unmapped_rejection(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder); source = p/'legacy.schematic'; mapping = p/'map.json'
            save(source, {'Materials': n.String('Alpha'), 'Width': n.Short(3), 'Height': n.Short(1), 'Length': n.Short(1),
                 'Blocks': n.ByteArray([0, 44, 1]), 'Data': n.ByteArray([0, 2, 1]), 'AddBlocks': n.ByteArray([16, 0]), 'WEOffsetX': n.Int(-7)})
            mapping.write_text(json.dumps({'blocks': {'0:0': 'minecraft:air', '300:2': 'example:verified_block', '1:1': 'minecraft:granite'}}), encoding='utf8')
            m, d = import_legacy(source, mapping)
            self.assertEqual(m.get(-6, 0, 0), 'example:verified_block'); self.assertEqual(m.get(-5, 0, 0), 'minecraft:granite')
            mapping.write_text(json.dumps({'0:0': 'minecraft:air'}), encoding='utf8')
            with self.assertRaisesRegex(ValueError, 'Unmapped'): import_legacy(source, mapping)


class RegistryAndScale(unittest.TestCase):
    def test_propertyless_ids_are_joined_from_complete_registry(self):
        props = {'oak_log': [{'axis': ['x', 'y', 'z']}, {'axis': 'y'}]}
        joined = complete_registry(props, ['air', 'stone', 'oak_log'])
        self.assertEqual(joined['stone'], [{}, {}]); self.assertNotIn('tuff_bricks', joined)
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder)/'registry.json'; p.write_text(json.dumps(joined), encoding='utf8')
            m = Model([0, 0, 0], [0, 1, 0]); m.set(0, 0, 0, 'minecraft:stone')
            self.assertTrue(validate(m, p)['valid'])
            m.set(0, 0, 0, 'minecraft:stone[axis=y]'); self.assertFalse(validate(m, p)['valid'])
            m.set(0, 0, 0, 'minecraft:tuff_bricks'); self.assertFalse(validate(m, p)['valid'])
            p.write_text(json.dumps(props), encoding='utf8')
            with self.assertRaisesRegex(ValueError, 'incomplete'): validate(m, p)
        with self.assertRaises(ValueError): complete_registry(props, ['air', 'stone'])

    def test_property_validation_and_versioned_export(self):
        from import_structure import read_nbt
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder); registry = p/'registry.json'
            registry.write_text(json.dumps({'air': [{}, {}], 'stone': [{}, {}], 'oak_log': [{'axis': ['x', 'y', 'z']}, {'axis': 'y'}]}), encoding='utf8')
            registry.with_suffix('.source.json').write_text(json.dumps({'version': '1.20.1', 'sha256': hashlib.sha256(registry.read_bytes()).hexdigest()}), encoding='utf8')
            m = Model([0, 0, 0], [1, 2, 1]); m.set(0, 0, 0, 'minecraft:oak_log[axis=z]'); m.save(p/'source.npz')
            self.assertTrue(validate(m, registry)['valid']); r = retarget(p/'source.npz', registry, p/'target.schem')
            self.assertEqual(read_nbt(p/'target.schem')['DataVersion'], 3465); self.assertTrue(r['state_roundtrip'])
            m.set(0, 0, 0, 'minecraft:oak_log[axis=diagonal]'); self.assertFalse(validate(m, registry)['valid'])
            m.set(0, 0, 0, 'unknown:block'); self.assertFalse(validate(m, registry)['valid'])

    def test_landmark_fit_and_degenerate_rejection(self):
        source = np.array([[0, 0, 0], [2, 0, 0], [0, 0, 4], [1, 3, 2]], float)
        target = np.column_stack((-source[:, 2], source[:, 1], source[:, 0])) * 2 + [10, 2, -8]
        cfg = {'schema': 'architecture-reference-calibration/1', 'source_units': 'metres',
               'pairs': [{'id': str(i), 'source_xyz': s.tolist(), 'target_xyz': t.tolist()} for i, (s, t) in enumerate(zip(source, target))]}
        r = calibrate(cfg); self.assertEqual(r['quarter_turns_clockwise'], 1); self.assertAlmostEqual(r['blocks_per_source_unit'], 2)
        self.assertLess(r['rms_blocks'], 1e-8)
        for p in cfg['pairs']: p['source_xyz'] = [0, 0, 0]
        with self.assertRaises(ValueError): calibrate(cfg)


if __name__ == '__main__': unittest.main()
