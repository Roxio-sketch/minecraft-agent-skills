"""Failures observed while repairing occupied courts and hanging corridor lamps."""
import copy
import unittest

import numpy as np

from field_refinement import (RefinementError, audit_edit, projection_metrics,
                              plan_hanging_lights, audit_blocklight)
from voxel_model import Model


class ExactEditTests(unittest.TestCase):
    def setUp(self):
        self.before = Model((-3, 0, -3), (3, 7, 3))
        self.before.box((-3, 0, -3), (3, 0, 3), 'minecraft:stone')
        self.before.set(-2, 3, 2, 'minecraft:cherry_leaves[persistent=true]')
        self.before.set(1, 5, 0, 'minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
        self.after = copy.deepcopy(self.before)
        self.old = self.before.get(1, 5, 0)
        self.new = self.old.replace('oak_stairs', 'spruce_stairs')
        self.after.set(1, 5, 0, self.new)
        self.args = {'changes': [[1, 5, 0, self.new]], 'prior': [[1, 5, 0, self.old]],
                     'material_pairs': {'minecraft:oak_stairs': 'minecraft:spruce_stairs'}}

    def test_palette_order_and_explicit_shape_state_survive(self):
        r = audit_edit(self.before, self.after, **self.args)
        self.assertTrue(r['occupied_and_air_mask_unchanged'])
        self.assertEqual(r['changed_cells'], 1)
        self.assertEqual(r['compared_cells'], 392)

    def test_undeclared_tree_road_and_air_edits_are_rejected(self):
        for q, state in (((-2, 3, 2), 'minecraft:air'), ((0, 0, 1), 'minecraft:glowstone'),
                         ((0, 3, 0), 'minecraft:stone')):
            with self.subTest(q=q):
                broken = copy.deepcopy(self.after)
                broken.set(*q, state)
                with self.assertRaises(RefinementError) as ctx:
                    audit_edit(self.before, broken, **self.args)
                self.assertEqual(ctx.exception.report['errors']['undeclared_change'], 1)

    def test_protected_region_stale_prior_missing_change_and_property_loss(self):
        with self.assertRaises(RefinementError):
            audit_edit(self.before, self.after, **self.args,
                       protected=[{'id': 'interface', 'bounds': [[1, 5, 0], [1, 6, 0]]}])
        stale = {**self.args, 'prior': [[1, 5, 0, 'minecraft:stone']]}
        with self.assertRaises(RefinementError):
            audit_edit(self.before, self.after, **stale)
        with self.assertRaises(RefinementError):
            audit_edit(self.before, self.before, **self.args)
        broken = copy.deepcopy(self.after)
        turned = self.new.replace('north', 'south')
        broken.set(1, 5, 0, turned)
        with self.assertRaises(RefinementError) as ctx:
            audit_edit(self.before, broken, **{**self.args, 'changes': [[1, 5, 0, turned]]})
        self.assertIn('material_or_property_change', ctx.exception.report['errors'])


class ProjectionTests(unittest.TestCase):
    def test_roofs_ornaments_and_air_are_distinct_at_full_resolution(self):
        m = Model((-2, 0, -2), (2, 8, 2))
        m.box((-2, 0, -2), (2, 0, 2), 'minecraft:stone')
        m.box((-2, 6, -2), (-1, 6, 2), 'minecraft:dark_prismarine')
        m.set(0, 1, 0, 'minecraft:flower_pot')
        m.set(1, 0, 1, 'minecraft:grass_block')
        r = projection_metrics(m, [-2, -2, 2, 2], 0,
                               ['minecraft:dark_prismarine'], ['minecraft:stone'])
        self.assertEqual(r['area_cells'], 25)
        self.assertEqual(r['roof_projection_cells'], 10)
        self.assertEqual(r['at_floor_paving_cells'], 24)
        self.assertEqual(r['open_paving_cells'], 13)
        self.assertEqual(r['resolution_blocks'], 1)


class HangingLightTests(unittest.TestCase):
    def scene(self):
        m = Model((-5, 0, -9), (5, 9, 9))
        m.box((-1, 0, -8), (1, 0, 8), 'minecraft:stone')
        # Beam lies beyond the walkable band, with no supporting floor beneath.
        m.box((3, 7, -8), (3, 7, 8), 'minecraft:oak_planks')
        floors = [[x, 0, z] for x in range(-1, 2) for z in range(-8, 9)]
        return m, floors

    def plan(self, m, floors, **kwargs):
        return plan_hanging_lights(m, floors, ['minecraft:oak_planks'],
                                   'minecraft:lantern[hanging=true,waterlogged=false]',
                                   'minecraft:chain[axis=y,waterlogged=false]',
                                   emission=12, minimum=3,
                                   transparent_bases=['minecraft:lantern', 'minecraft:chain'], **kwargs)

    def test_edge_beam_supported_plan_and_atomic_failure(self):
        m, floors = self.scene()
        before = m.blocks.copy()
        with self.assertRaises(RefinementError):
            self.plan(m, floors, shoulder=0)
        np.testing.assert_array_equal(m.blocks, before)
        r = self.plan(m, floors, shoulder=2)
        self.assertTrue(r['passed'])
        self.assertGreater(r['new_lamps'], 0)
        for f in r['fixtures']:
            self.assertEqual(f['lamp'][0], 3)
            self.assertEqual(f['anchor'][1], 7)
            self.assertEqual(m.get(*f['anchor']), 'minecraft:oak_planks')
            self.assertGreaterEqual(f['lamp'][1], 4)
        np.testing.assert_array_equal(m.blocks, before)
        self.assertNotIn('minecraft:lantern[hanging=true,waterlogged=false]', m.palette)

    def test_opaque_wall_blocks_nearby_light_and_no_anchor_rejected(self):
        m, floors = self.scene()
        m.box((2, 1, -9), (2, 9, 9), 'minecraft:stone')
        with self.assertRaises(RefinementError):
            self.plan(m, floors)
        m, floors = self.scene()
        m.box((3, 7, -8), (3, 7, 8), 'minecraft:glass')
        with self.assertRaises(RefinementError):
            self.plan(m, floors)

    def test_three_clear_cells_existing_sources_and_strict_light_samples(self):
        m, floors = self.scene()
        r = self.plan(m, floors)
        for x, y, z, state in r['changes']:
            m.set(x, y, z, state)
        self.assertEqual(self.plan(m, floors)['new_lamps'], 0)
        with self.assertRaises(ValueError):
            self.plan(m, floors, clear_height=2)
        self.assertTrue(audit_blocklight([3, 8, 9], 3)['passed'])
        for values in ([3, None], [3, -1], [3, 2], [3, True]):
            with self.assertRaises(RefinementError):
                audit_blocklight(values, 3)

    def test_fixtures_cannot_be_their_own_structural_anchors(self):
        m, floors = self.scene()
        for base in ('minecraft:lantern', 'minecraft:chain'):
            with self.subTest(base=base), self.assertRaises(ValueError):
                plan_hanging_lights(m, floors, [base], 'minecraft:lantern', 'minecraft:chain')


if __name__ == '__main__':
    unittest.main(verbosity=2)
