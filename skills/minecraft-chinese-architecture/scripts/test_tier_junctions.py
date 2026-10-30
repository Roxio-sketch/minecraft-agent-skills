"""Regression of visible floating tiers and omitted floors/clearances."""
import copy, tempfile, unittest
from pathlib import Path
import numpy as np
from voxel_model import Model
from tier_junctions import DEFAULT,assemble_tier_junction,audit_tier_junction,audit_floor_plate,stair_clearance_boxes,stair_protected_boxes


def sample():
    c=copy.deepcopy(DEFAULT);m=Model((-20,0,-14),(20,25,14))
    # The lower frame is grounded; one thread to the upper frame makes the
    # whole model connected while leaving the photographed all-round slot.
    m.box((-16,0,-10),(16,0,10),'minecraft:stone_bricks')
    for x in (-16,16):
        for z in (-10,10):m.box((x,1,z),(x,13,z),'minecraft:dark_oak_planks')
    for x in (-16,16):m.box((x,13,-10),(x,13,10),'minecraft:dark_oak_planks')
    for z in (-10,10):m.box((-16,13,z),(16,13,z),'minecraft:dark_oak_planks')
    m.box((-16,14,-10),(-16,18,-10),'minecraft:dark_oak_planks')
    m.box((-16,18,-10),(12,18,-7),'minecraft:dark_oak_planks')
    for x,z in c['column_centers_xz']:m.box((x,19,z),(x,24,z),'minecraft:dark_oak_planks')
    return m,c


class TierJunctionTests(unittest.TestCase):
    def stair_sample(self):
        from stair_kernel import make_staircase
        m,c=sample();part,d=make_staircase({'start_floor_xyz':[-6,9,6],'rises':[9],
          'width':3,'tread_depth':1,'bottom_landing':2,'top_landing':5})
        m.paste(part,conflict='replace',include_air=True)
        c['kind']='storey';c['voids']=stair_clearance_boxes([d],13,21)
        c['protected']=stair_protected_boxes([d],13,21)
        return m,c,d

    def test_stair_treads_survive_floor_and_collar_in_final_composition(self):
        from stair_kernel import verify_staircase
        m,c,d=self.stair_sample();assemble_tier_junction(m,c)
        self.assertTrue(audit_tier_junction(m,c)['passed'])
        self.assertTrue(all(verify_staircase(m,d).values()))
        self.assertTrue(m.get(-6,18,-3).startswith('minecraft:smooth_quartz_stairs'))

    def test_later_tread_overwrite_fails_even_while_headroom_is_clear(self):
        m,c,d=self.stair_sample();assemble_tier_junction(m,c)
        for bad in ('minecraft:dark_oak_planks','minecraft:smooth_quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]','minecraft:air'):
            broken=m.rotated(0);broken.set(-6,18,-3,bad)
            r=audit_tier_junction(broken,c)
            self.assertTrue(r['checks']['declared_voids_clear'])
            self.assertFalse(r['checks']['protected_occupancy_intact'])
            self.assertFalse(r['passed'])

    def test_protection_is_not_permission_to_invent_a_hole(self):
        for state in ('minecraft:air','minecraft:water[level=0]','minecraft:oak_fence','example:unknown'):
            m,c,d=self.stair_sample();c['protected'].append({'id':'false-floor','min':[4,18,4],'max':[4,18,4],'state':state})
            before=m.blocks.copy()
            with self.assertRaises(ValueError):assemble_tier_junction(m,c)
            np.testing.assert_array_equal(before,m.blocks)

    def test_missing_protected_tread_refused_atomically(self):
        m,c,d=self.stair_sample();m.set(-6,18,-3,'minecraft:air');before=m.blocks.copy()
        with self.assertRaises(ValueError):assemble_tier_junction(m,c)
        np.testing.assert_array_equal(before,m.blocks)

    def test_beam_cannot_use_a_protected_half_stair_as_full_bearing(self):
        m,c,d=self.stair_sample();c['beam_axis']='cross';c['post_radius']=1
        # A new declared column would put a bearing post on the incoming tread.
        c['column_centers_xz'].append([-6,-3])
        m.set(-6,13,-10,'minecraft:dark_oak_planks');m.set(-6,13,10,'minecraft:dark_oak_planks')
        m.set(-16,13,-3,'minecraft:dark_oak_planks');m.set(16,13,-3,'minecraft:dark_oak_planks')
        before=m.blocks.copy()
        with self.assertRaises(ValueError):assemble_tier_junction(m,c)
        np.testing.assert_array_equal(before,m.blocks)

    def test_visible_gap_detected_even_if_connected(self):
        m,c=sample();r=audit_tier_junction(m,c)
        self.assertFalse(r['checks']['closed_perimeter_collar'])
        self.assertFalse(r['checks']['complete_declared_floor'])
        assemble_tier_junction(m,c)
        self.assertTrue(audit_tier_junction(m,c)['passed'])

    def test_collar_slot_or_floor_hole_fails(self):
        for xyz,key in [((0,15,-9),'closed_perimeter_collar'),((0,18,0),'complete_declared_floor'),((0,15,-7),'continuous_column_bearings')]:
            with self.subTest(xyz=xyz):
                m,c=sample();assemble_tier_junction(m,c);m.set(*xyz,'minecraft:air')
                self.assertFalse(audit_tier_junction(m,c)['checks'][key])

    def test_partial_shape_does_not_hide_a_half_block_gap(self):
        for state in ['minecraft:quartz_slab[type=bottom]','minecraft:quartz_stairs[facing=north,half=bottom,shape=straight]',
                      'minecraft:oak_fence','minecraft:water[level=0]','example:unknown_cube']:
            with self.subTest(state=state):
                m,c=sample();assemble_tier_junction(m,c);m.set(0,18,0,state)
                self.assertFalse(audit_tier_junction(m,c)['checks']['complete_declared_floor'])

    def test_missing_lower_anchor_is_rejected_atomically(self):
        m,c=sample();m.set(0,13,-10,'minecraft:air');old=m.blocks.copy()
        with self.assertRaises(ValueError):assemble_tier_junction(m,c)
        np.testing.assert_array_equal(old,m.blocks)

    def test_declared_stairwell_kept_open_and_blocker_detected(self):
        m,c=sample();c['kind']='storey'
        c['voids']=[{'id':'stairwell','min':[-4,17,-2],'max':[-2,21,2]}]
        assemble_tier_junction(m,c)
        self.assertEqual(m.get(-3,18,0),'minecraft:air')
        m.set(-3,19,0,'minecraft:stone_bricks')
        self.assertFalse(audit_tier_junction(m,c)['checks']['declared_voids_clear'])

    def test_circulation_conflict_rejected_before_mutation(self):
        m,c=sample();c['kind']='storey'
        c['voids']=[{'id':'conflicting-stairwell','min':[-1,14,-8],'max':[1,18,-6]}]
        before=m.blocks.copy()
        with self.assertRaises(ValueError):assemble_tier_junction(m,c)
        np.testing.assert_array_equal(before,m.blocks)

    def test_mod_shapes_require_evidence_and_roundtrip(self):
        m,c=sample();c['palette']={k:'example:verified_solid' for k in c['palette']}
        with self.assertRaises(ValueError):assemble_tier_junction(m,c)
        c['solid_blocks'].append('example:verified_solid');assemble_tier_junction(m,c)
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'tier.npz';m.save(p)
            self.assertTrue(audit_tier_junction(Model.load(p),c)['passed'])

    def test_floor_audit_checks_all_cells_and_excludes_only_declared_void(self):
        m=Model((-5,0,-5),(5,4,5));m.box((-5,0,-5),(5,0,5),'minecraft:stone_bricks')
        m.box((-1,0,-1),(1,0,1),'minecraft:air')
        args={'solid_blocks':['minecraft:stone_bricks']}
        self.assertFalse(audit_floor_plate(m,[-5,-5,5,5],0,**args)['passed'])
        self.assertTrue(audit_floor_plate(m,[-5,-5,5,5],0,void_xz=[[-1,-1,1,1]],**args)['passed'])
        m.set(4,0,3,'minecraft:air')
        self.assertFalse(audit_floor_plate(m,[-5,-5,5,5],0,void_xz=[[-1,-1,1,1]],**args)['passed'])

    def test_offset_and_asymmetric_frame(self):
        m,c=sample();assemble_tier_junction(m,c)
        out=Model((80,20,-54),(120,45,-26));out.blocks=m.blocks.copy();out.palette=m.palette.copy();out.index=m.index.copy()
        for key in ('lower_frame_xz','upper_frame_xz','roof_opening_xz'):
            a=c[key];c[key]=[a[0]+100,a[1]-40,a[2]+100,a[3]-40]
        c['column_centers_xz']=[[x+100,z-40] for x,z in c['column_centers_xz']]
        c['lower_beam_y']+=20;c['upper_floor_y']+=20
        self.assertTrue(audit_tier_junction(out,c)['passed'])

    def test_scene_routes_to_the_real_interface_kernel(self):
        from compose_scene import make_scene
        m,c=sample()
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'source.npz';m.save(p)
            model,d=make_scene({'schema':'chinese-scene/1','components':[{'id':'frame','model_file':str(p)}],
              'tier_junctions':[c]})
            self.assertTrue(d['checks']['declared_tier_junctions_complete'])
            model.set(0,18,0,'minecraft:air')
            self.assertFalse(audit_tier_junction(model,c)['passed'])

    def test_all_four_rotations_and_corner_seals(self):
        m,c=sample();assemble_tier_junction(m,c)
        for turns in range(4):
            q=copy.deepcopy(c)
            def turn(x,z):
                for _ in range(turns):x,z=-z,x
                return x,z
            for key in ('lower_frame_xz','upper_frame_xz','roof_opening_xz'):
                x0,z0,x1,z1=q[key];pts=[turn(x,z) for x in (x0,x1) for z in (z0,z1)]
                q[key]=[min(p[0] for p in pts),min(p[1] for p in pts),max(p[0] for p in pts),max(p[1] for p in pts)]
            q['column_centers_xz']=[list(turn(x,z)) for x,z in q['column_centers_xz']]
            q['beam_axis']='x' if turns%2 else 'z'
            self.assertTrue(audit_tier_junction(m.rotated(turns),q)['passed'])
        for x,z in ((-14,-9),(-14,9),(14,-9),(14,9)):
            other=m.rotated(0);other.set(x,15,z,'minecraft:air')
            self.assertFalse(audit_tier_junction(other,c)['checks']['closed_perimeter_collar'])

    def test_stair_clearance_uses_actual_rows(self):
        from stair_kernel import make_staircase
        _,d=make_staircase({'rises':[6,4],'tread_depth':3,'landing_depth':5})
        holes=stair_clearance_boxes([d],6,9)
        self.assertTrue(holes)
        self.assertTrue(all(6<=v['min'][1]<=v['max'][1]<=9 for v in holes))
        self.assertTrue(any('row-' in v['id'] for v in holes))

    def test_offset_bearing_routes_around_declared_shaft(self):
        m,c=sample();c['kind']='storey';c['post_radius']=0
        c['voids']=[{'id':'shaft','min':[-12,14,-7],'max':[-12,15,-7]}]
        with self.assertRaises(ValueError):assemble_tier_junction(m,c)
        c['bearing_offsets_xz']=[[-3,0]]+[[0,0] for _ in c['column_centers_xz'][1:]]
        assemble_tier_junction(m,c)
        self.assertTrue(audit_tier_junction(m,c)['passed'])
        self.assertEqual(m.get(-12,15,-7),'minecraft:air')
        self.assertEqual(m.get(-15,15,-7),'minecraft:dark_oak_planks')


if __name__=='__main__':unittest.main(verbosity=2)
