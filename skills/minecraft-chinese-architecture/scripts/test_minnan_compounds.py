import copy
from pathlib import Path
import tempfile
import unittest

import numpy as np

from generate_courtyard import walk_paths
from generate_vernacular import make_vernacular, export


def build(**parameters):
    return make_vernacular({'kind':'minnan_compound','parameters':parameters})


def seal(model,points,height=4):
    for x,y,z in points:
        model.box((x,y,z),(x,y+height-1,z),'minecraft:stone_bricks')


class MinnanCompoundTests(unittest.TestCase):
    def test_separate_and_continuous_front_roofs_are_different_geometry(self):
        a,ad=build(form='san_gai_lang')
        b,bd=build(form='two_luo')
        # The courtyard and rear range remain comparable; the front roof
        # changes actual heights and gable closures above the entrance row.
        self.assertEqual(a.blocks.shape,b.blocks.shape)
        y0=8-int(a.origin[1]);z1=7-int(a.origin[2])
        aa=np.asarray(a.palette)[a.blocks[y0:,:z1]]
        bb=np.asarray(b.palette)[b.blocks[y0:,:z1]]
        self.assertFalse(np.array_equal(aa,bb))
        for m,d in ((a,ad),(b,bd)):
            self.assertTrue(d['routes']['all_targets_reached'])
            for x,y,z in d['external_gates'][0]['opening_xyz']:
                self.assertEqual(m.get(x,y,z),'minecraft:air')

    def test_ancestral_position_changes_skyline_and_onward_passage(self):
        for location in ('rear','middle'):
            m,d=build(form='three_luo',ancestral_range=location)
            cfg=d['config']['parameters'];step=cfg['range_depth']+cfg['wing_rooms']*cfg['wing_room_width']+cfg['court_extra']
            heights=[]
            for row in range(3):
                occupied=[y for y in range(int(m.origin[1]),int(m.maximum[1])+1) if m.get(0,y,row*step)!='minecraft:air']
                heights.append(max(occupied))
            self.assertEqual(heights.index(max(heights)),1 if location=='middle' else 2)
            onward=[p for p in d['onward_portals'] if p['part']=='range_2']
            self.assertEqual(len(onward),2 if location=='middle' else 1)
            if location=='middle':
                self.assertNotEqual(m.get(0,3,step+cfg['range_depth']//2),'minecraft:air')

    def test_closing_middle_portals_blocks_the_next_court(self):
        for location in ('rear','middle'):
            m,d=build(form='three_luo',ancestral_range=location)
            for p in d['onward_portals']:
                if p['part']=='range_2':seal(m,p['opening_xyz'])
            paths=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
            self.assertFalse(paths['all_targets_reached'])
            self.assertIn('range_3.room_2',paths['unreachable'])
            self.assertIn('range_2.room_2',paths['routes_floor_xyz'])

    def test_hulong_front_gate_works_independently_of_core(self):
        m,d=build(hulong='both')
        seal(m,d['external_gates'][0]['opening_xyz'])
        for connection in d['guoshui']:seal(m,connection['core_opening_xyz'])
        paths=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
        self.assertIn('range_2.ancestral_hall',paths['unreachable'])
        for side in ('east','west'):
            self.assertIn('hulong.'+side+'.room_3',paths['routes_floor_xyz'])
        # The independent entrance remains necessary; after sealing all of
        # them the same exterior path cannot reach any household room.
        for gate in d['external_gates'][1:]:seal(m,gate['opening_xyz'],5)
        paths=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
        self.assertIn('hulong.east.room_3',paths['unreachable'])
        self.assertIn('hulong.west.room_3',paths['unreachable'])

    def test_all_external_gates_can_close_the_whole_compound(self):
        for form in ('two_luo','san_gai_lang','three_luo'):
            m,d=build(form=form,hulong='east')
            for gate in d['external_gates']:seal(m,gate['opening_xyz'],5)
            paths=walk_paths(m,d['start_floor_xyz'],d['target_floors_xyz'])
            self.assertEqual(len(paths['routes_floor_xyz']),0)

    def test_covered_crossings_and_open_side_court_coexist(self):
        for lane in (8,10,14):
            m,d=build(hulong='east',lane_width=lane)
            for passage in d['guoshui']:
                xa,za,xb,zb=passage['bounds_xz']
                for x in range(xa,xb+1):
                    for z in range(za,zb+1):
                        self.assertNotEqual(m.get(x,passage['roof_y'],z),'minecraft:air')
            for x,f,z in d['sky_probe_floors_xyz']:
                self.assertTrue(all(m.get(x,y,z)=='minecraft:air' for y in range(f+1,int(m.maximum[1])+1)))

    def test_split_court_changes_supporting_floor_not_just_metadata(self):
        for split in (False,True):
            m,d=build(form='three_luo',split_court=split)
            for court in d['courts']:
                lower=d['target_floors_xyz'][court['id']+'.lower']
                upper=d['target_floors_xyz'][court['id']+'.upper']
                self.assertEqual(upper[1]-lower[1],int(split))
                for x,y,z in (lower,upper):
                    self.assertNotEqual(m.get(x,y,z),'minecraft:air')
                    self.assertEqual(m.get(x,y+1,z),'minecraft:air')

    def test_parameter_changes_preserve_fit_and_nonuniform_side_bays(self):
        for p in ({'form':'three_luo','hulong':'both','ancestral_range':'middle','bay_width':14,
                   'range_depth':14,'wing_depth':8,'wing_rooms':2,'court_extra':12,'hulong_rooms':7},
                  {'form':'san_gai_lang','hulong':'west','lane_width':8,'hulong_depth':6,'ridge':'round'},
                  {'form':'two_luo','wing_rooms':2,'eave_height':12,'roof_rise':7,'hulong':'east','hulong_rooms':6}):
            m,d=build(**p)
            self.assertTrue(d['routes']['all_targets_reached'])
            for h in d['hulong']:
                self.assertGreaterEqual(min(h['room_bays']),6)
                self.assertLessEqual(max(h['room_bays'])-min(h['room_bays']),2)
        for p in ({'ancestral_range':'middle'},{'hulong':'east','hulong_rooms':9},{'split_court':1},
                  {'bay_width':10,'wing_depth':10},{'form':'three_luo','range_depth':11},{'lane_width':6},
                  {'form':'san_gai_lang','wing_rooms':2}):
            with self.assertRaises(ValueError):build(**p)

    def test_export_roundtrip_and_rotated_component(self):
        from architecture import build_component
        from compose_scene import make_scene
        cfg={'schema':'chinese-vernacular/1','kind':'minnan_compound','parameters':{'form':'two_luo','hulong':'east'}}
        m,_=build_component(cfg)
        scene,d=make_scene({'schema':'chinese-scene/1','components':[{'id':'compound','config':copy.deepcopy(cfg),'turns':3}]})
        self.assertEqual(np.count_nonzero(m.blocks),np.count_nonzero(scene.blocks))
        with tempfile.TemporaryDirectory() as folder:
            result=export(cfg,Path(folder),'compound',False)
            self.assertTrue(result['design']['checks']['schematic_state_roundtrip'])


if __name__=='__main__':unittest.main()
