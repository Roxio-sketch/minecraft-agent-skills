"""Observable arcade circulation and carved/openwork geometry behavior."""
import copy
from pathlib import Path
import tempfile
import unittest

import numpy as np

from generate_courtyard import walk_paths
from generate_ornament import make_ornament, export as ornament_export
from generate_vernacular import make_vernacular


class ArcadedDwellings(unittest.TestCase):
    def test_sidewalk_under_occupied_storeys_and_each_stair(self):
        for params in ({}, {'shops':1,'floors':2,'module_width':10,'floor_height':6,'arcade_depth':4,'depth':20},
                       {'shops':2,'floors':4,'module_width':16,'floor_height':9,'arcade_depth':7,'depth':32}):
            with self.subTest(params=params):
                m,d=make_vernacular({'kind':'arcaded_shophouse','parameters':params})
                self.assertTrue(d['routes']['all_targets_reached'])
                c=d['config']['parameters']; self.assertEqual(len(d['rooms']),c['shops']*c['floors'])
                for x in range(c['shops']*c['module_width']+1):
                    z=c['arcade_depth']//2
                    self.assertEqual(m.get(x,1,z),'minecraft:air')
                    self.assertEqual(m.get(x,2,z),'minecraft:air')
                    self.assertNotEqual(m.get(x,c['floor_height'],z),'minecraft:air')
                for flight in d['stair_flights']:
                    self.assertTrue(walk_paths(m,flight['bottom_floor_xyz'],{'landing':flight['top_floor_xyz']})['all_targets_reached'])

    def test_sealing_a_shop_keeps_public_arcade_but_isolates_its_rooms(self):
        m,d=make_vernacular({'parameters':{'shops':2,'floors':2}})
        door=d['shop_doors'][0]; lo,hi=door['x_range']
        m.box((lo,1,door['z']),(hi,5,door['z']),'minecraft:stone_bricks')
        result=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
        self.assertIn('arcade_far_end',result['routes_floor_xyz'])
        self.assertTrue(all(name.startswith('unit_1.') for name in result['unreachable']))
        self.assertIn('unit_1.floor_2.rear',result['unreachable'])
        self.assertIn('unit_2.floor_2.rear',result['routes_floor_xyz'])

    def test_beds_have_pairs_and_accessible_adjacent_space(self):
        m,d=make_vernacular({'parameters':{'shops':1,'floors':3}})
        for f in d['fixtures']:
            self.assertIn(f['id'],d['routes']['routes_floor_xyz'])
            if f['kind']=='bed':
                a,b=f['cells_xyz']; self.assertEqual(b,[a[0],a[1],a[2]+1])
                self.assertIn('part=foot',m.get(*a));self.assertIn('part=head',m.get(*b))
                self.assertNotEqual(m.get(a[0],a[1]-1,a[2]),'minecraft:air')
                self.assertNotEqual(m.get(b[0],b[1]-1,b[2]),'minecraft:air')

    def test_invalid_stair_depth_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'depth must fit'):
            make_vernacular({'parameters':{'depth':20,'arcade_depth':8,'floor_height':9}})
        with self.assertRaises(ValueError):make_vernacular({'parameters':{'floors':True}})


class CarvedGeometry(unittest.TestCase):
    def test_raised_and_incised_patterns_change_actual_surfaces(self):
        for motif in ('lotus','meander','cloud_scroll'):
            for mode in ('raised','incised'):
                with self.subTest(motif=motif,mode=mode):
                    m,d=make_ornament({'parameters':{'motif':motif,'mode':mode,'depth':3}})
                    self.assertTrue(np.all(m.blocks[:,-1,:]!=0))
                    face_index=-int(m.origin[2]); front=m.blocks[:,face_index,:]
                    if mode=='incised':
                        self.assertGreater(int(np.count_nonzero(front==0)),20)
                        self.assertTrue(np.all(np.any(m.blocks!=0,axis=1)))
                    else:
                        self.assertGreater(int(np.count_nonzero(m.blocks[:,0,:])),0)
                        counts=[np.count_nonzero(m.blocks[:,z,:]) for z in range(face_index)]
                        self.assertEqual(counts,sorted(counts))

    def test_screen_is_open_all_the_way_through(self):
        for pattern in ('orthogonal','diagonal','ice_ray'):
            for thickness in (1,3):
                m,d=make_ornament({'kind':'lattice_screen','parameters':{'pattern':pattern,'depth':thickness}})
                through=np.all(m.blocks==0,axis=1)
                self.assertGreater(int(through.sum()),50)
                np.testing.assert_array_equal(through,m.blocks[:,0,:]==0)
                self.assertEqual(int(through.sum()),d['open_projected_cells'])
                self.assertTrue(d['checks']['face_connected_geometry'])

    def test_ice_ray_seed_reproducibility_and_change(self):
        cfg={'kind':'lattice_screen','parameters':{'pattern':'ice_ray','seed':1234}}
        a,_=make_ornament(cfg); b,_=make_ornament(copy.deepcopy(cfg))
        np.testing.assert_array_equal(a.blocks,b.blocks)
        cfg['parameters']['seed']=4321; c,_=make_ornament(cfg)
        self.assertFalse(np.array_equal(a.blocks,c.blocks))

    def test_solid_backing_is_configurable_and_files_roundtrip(self):
        with tempfile.TemporaryDirectory() as folder:
            r=ornament_export({'parameters':{'mode':'incised','backing':1,'depth':6}},Path(folder),'carving',False)
            self.assertTrue(r['design']['checks']['schematic_state_roundtrip'])
            self.assertEqual(r['design']['preserved_backing_layers'],1)
        with self.assertRaises(ValueError):make_ornament({'parameters':{'stroke_width':2}})
        with self.assertRaises(ValueError):make_ornament({'parameters':{'width':15,'repeats':8,'motif':'meander'}})
        with self.assertRaises(ValueError):make_ornament({'kind':'lattice_screen','palette':{'carving':'minecraft:glass_pane'}})


class RegionalCourts(unittest.TestCase):
    def test_new_types_use_unified_composition_and_site_entry(self):
        from architecture import build_component
        from compose_scene import make_scene
        from generate_complex import make_complex
        panel,_=build_component({'schema':'chinese-ornament/1','kind':'lattice_screen'})
        scene,detail=make_scene({'schema':'chinese-scene/1','components':[{'id':'screen','config':{'schema':'chinese-ornament/1','kind':'lattice_screen'},'turns':1}]})
        self.assertEqual(np.count_nonzero(panel.blocks),np.count_nonzero(scene.blocks))
        part,_=build_component({'schema':'chinese-vernacular/1','parameters':{'shops':1,'floors':2}})
        rotated=part.rotated(1);lo=rotated.origin[[0,2]]-14;hi=rotated.maximum[[0,2]]+14
        m,d,_,_=make_complex({'origin_xz':lo.tolist(),'size_xz':(hi-lo+1).tolist(),
            'terrain':{'base':2,'grade_z':0,'wave':0,'hills':[]},'path_width':3,
            'components':[{'id':'shops','kind':'vernacular','config':{'parameters':{'shops':1,'floors':2}},'position_xz':[0,0],'turns':1}],
            'entrance_xz':(lo+3).tolist(),'connections':[['entrance','shops.entry']]})
        self.assertTrue(all(d['checks'].values()))

    def test_three_bays_and_distinct_projecting_room_counts(self):
        for count in (1,2):
            m,d=make_vernacular({'kind':'minnan_court','parameters':{'wing_rooms':count}})
            self.assertEqual(len(d['rooms']),3+2*count)
            self.assertEqual(len(d['door_openings']),3+2*count)
            for room in d['rooms']:
                self.assertIn(room['id'],d['routes']['routes_floor_xyz'])
            for y in range(1,int(m.maximum[1])+1):
                self.assertEqual(m.get(0,y,d['court_depth']//2),'minecraft:air')

    def test_main_range_ridge_and_wok_gables_are_real_extra_geometry(self):
        a,ad=make_vernacular({'kind':'minnan_court','parameters':{'ridge':'round'}})
        b,bd=make_vernacular({'kind':'minnan_court','parameters':{'ridge':'swallowtail'}})
        self.assertGreater(np.count_nonzero(b.blocks),np.count_nonzero(a.blocks))
        self.assertEqual(len(bd['main_range']['swallowtail_arms']),4)
        m,d=make_vernacular({'kind':'wok_ear_court'})
        for profile in d['main_range']['gable_profiles']:
            values=dict(profile['top_y_by_z'])
            self.assertEqual(values,{-z:y for z,y in values.items()})
            self.assertGreater(values[0],values[min(values)]+3)

    def test_court_gate_can_seal_all_private_rooms(self):
        m,d=make_vernacular({'kind':'wok_ear_court'})
        m.box((-1,1,0),(1,5,0),'minecraft:stone_bricks')
        paths=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
        self.assertFalse(paths['all_targets_reached'])
        self.assertIn('main.room_2',paths['unreachable'])
        self.assertIn('west.room_1',paths['unreachable'])

    def test_court_resize_preserves_routes_and_empty_sky(self):
        for kind in ('wok_ear_court','minnan_court'):
            for params in ({'bay_width':12,'wing_depth':8,'main_depth':16,'court_extra':12},
                           {'bay_width':8,'main_depth':10,'wing_depth':6,'roof_rise':3,'eave_height':8}):
                # The smaller range cannot carry deep wings and two porches.
                if params['bay_width']==8:
                    with self.assertRaises(ValueError):make_vernacular({'kind':kind,'parameters':params})
                else:
                    m,d=make_vernacular({'kind':kind,'parameters':params})
                    self.assertTrue(d['routes']['all_targets_reached'])


if __name__=='__main__':unittest.main()
