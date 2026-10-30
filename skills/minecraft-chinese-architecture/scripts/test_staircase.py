"""Regression for same-height stair grooves, reversed risers and blocked access."""
import copy,tempfile,unittest
from stair_kernel import make_staircase,verify_staircase,row_position,top_landing_box
from architecture import generate,build_component

class StaircaseTests(unittest.TestCase):
    def test_shared_full_support_needs_known_shape_or_explicit_evidence(self):
        m,d=make_staircase({'rises':[4]});row=next(r for r in d['rows'] if r['y']==3)
        x,z=row_position(d['config'],row['t'],0);m.set(x,1,z,'example:timber')
        self.assertFalse(verify_staircase(m,d)['continuous_support'])
        d['config']['solid_blocks']=['example:timber']
        self.assertTrue(verify_staircase(m,d)['continuous_support'])
        m.set(x,1,z,'minecraft:oak_slab[type=bottom]')
        self.assertFalse(verify_staircase(m,d)['continuous_support'])
        for state in ('minecraft:water','minecraft:oak_stairs','minecraft:oak_slab'):
            with self.assertRaises(ValueError):make_staircase({'solid_blocks':[state]})

    def test_turn_landing_preserves_last_riser_in_four_directions(self):
        from voxel_model import Model
        for facing in ('north','east','south','west'):
            part,d=make_staircase({'facing':facing,'rises':[7],'tread_depth':1,'width':3,'top_landing':5})
            m=Model(part.origin-[12,0,12],part.maximum+[12,0,12]);m.paste(part)
            band=top_landing_box(d,2,[-9,9]);m.box(band['min'],band['max'],d['config']['palette']['floor'])
            self.assertTrue(all(verify_staircase(m,d).values()),facing)
    def test_symmetric_turn_landing_overwrites_riser_and_is_rejected(self):
        m,d=make_staircase({'rises':[7],'tread_depth':1,'width':3,'top_landing':5})
        x,y,z=d['target_floors_xyz']['upper']
        m.box((x-1,y,z-1),(x+1,y,z+1),d['config']['palette']['floor'])
        self.assertFalse(verify_staircase(m,d)['half_block_max_rise'])
    def test_turn_landing_does_not_invent_room_or_clip_flight(self):
        _,d=make_staircase({'top_landing':3,'width':5})
        for depth,span in ((4,None),(0,None),(2,[0,1]),(2,[3,-3])):
            with self.assertRaises(ValueError):top_landing_box(d,depth,span)
    def test_existing_building_approach_has_no_initial_half_block_dip(self):
        from generate_building import make_building
        for rise in (1,2,3,8):
            m,b,c=make_building({'platform_height':rise})
            x=b['doorway']['aisle_x'];z=-b['platform_half_z']-rise
            _,d=make_staircase({'start_floor_xyz':[x,0,z],'facing':'south','width':c['stair_width'],
              'rises':[rise],'tread_depth':1,'bottom_landing':1,'top_landing':1,'headroom':2,
              'palette':{'foundation':c['palette']['foundation'],'floor':c['palette']['floor'],'stairs':c['palette']['access_stairs']}})
            self.assertTrue(all(verify_staircase(m,d).values()))
    def test_four_directions_deep_treads_and_mixed_flights(self):
        for facing in ('north','east','south','west'):
            for depth in (1,2,3,5):
                with self.subTest(facing=facing,depth=depth):
                    m,d=make_staircase({'facing':facing,'tread_depth':depth,'rises':[3,5,2],
                      'width':4,'start_floor_xyz':[-11,7,13],'foundation_y':4})
                    self.assertTrue(all(verify_staircase(m,d).values()))
                    self.assertEqual(d['riser_count'],10)
                    self.assertEqual(d['target_floors_xyz']['upper'][1],17)
                    self.assertEqual(sum(r['kind']=='riser' for r in d['rows']),10)
    def test_repeated_stairs_at_same_height_detected(self):
        m,d=make_staircase({'rises':[4],'tread_depth':3})
        row=next(r for r in d['rows'] if r['kind']=='tread');x,z=row_position(d['config'],row['t'],0)
        m.set(x,row['y'],z,'minecraft:smooth_quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]')
        c=verify_staircase(m,d)
        self.assertFalse(c['surface_continuous_without_dips']);self.assertFalse(c['flat_treads_and_landings'])
    def test_reversed_stair_blocker_and_missing_support_detected(self):
        for defect in ('reverse','head','support'):
            m,d=make_staircase({'rises':[4],'tread_depth':2})
            r=[r for r in d['rows'] if r['kind']=='riser'][2];x,z=row_position(d['config'],r['t'],0);y=r['y']
            if defect=='reverse':m.set(x,y,z,'minecraft:smooth_quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]')
            elif defect=='head':m.set(x,y+2,z,'minecraft:stone')
            else:m.set(x,y-1,z,'minecraft:air')
            self.assertFalse(all(verify_staircase(m,d).values()))
    def test_wide_axis_landing_and_schematic_roundtrip(self):
        c={'schema':'chinese-staircase/1','width':31,'rises':[8,7,9],'tread_depth':3,'landing_depth':8}
        m,d=build_component(c);self.assertTrue(all(d['checks'].values()))
        with tempfile.TemporaryDirectory() as tmp:
            r=generate(c,tmp,'ceremonial-flight',False)
            self.assertTrue(r['design']['checks']['schematic_state_roundtrip'])
    def test_mod_geometry_needs_explicit_alias_and_invalid_input(self):
        for c in ({'palette':{'stairs':'example:jade_stairs'}},{'tread_depth':0},{'rises':[0]},
                  {'width':True},{'foundation_y':5},{'palette':{'floor':'minecraft:water'}}):
            with self.subTest(config=c),self.assertRaises(ValueError):make_staircase(c)
        m,d=make_staircase({'rises':[2],'palette':{'stairs':'example:jade_stairs'},
          'shape_aliases':{'example:jade_stairs':'minecraft:quartz_stairs'}})
        self.assertTrue(all(d['checks'].values()))

if __name__=='__main__':unittest.main(verbosity=2)
