"""Corridor geometry and multi-storey stair behavior, including blocked routes."""
import copy
import tempfile
import unittest

import numpy as np

from generate_corridor import make_corridor, export as export_corridor
from generate_tower import make_tower, audit_frame_seams, export as export_tower, DEFAULT as TOWER
from generate_courtyard import walk_paths


def tower_config(heights, width=3):
    levels=[copy.deepcopy(TOWER['levels'][-1]) for _ in heights]
    for item,height in zip(levels,heights):item['height']=height
    return {'levels':levels,'stair_width':width}


class CorridorTests(unittest.TestCase):
    def test_straight_elbow_u_and_reverse(self):
        for path,width in (([[0,0],[0,24]],3), ([[0,0],[0,24],[30,24]],5),
                           ([[-30,-10],[-30,20],[0,20],[0,-10]],7)):
            with self.subTest(path=path,width=width):
                m,d=make_corridor({'path_xz':path,'clear_width':width})
                r,rd=make_corridor({'path_xz':list(reversed(path)),'clear_width':width})
                np.testing.assert_array_equal(np.array(m.palette)[m.blocks],np.array(r.palette)[r.blocks])
                self.assertEqual(d['portals'],list(reversed(rd['portals'])))
                # Every centerline square really is clear, not just one BFS path.
                for seg in d['segments']:
                    dx,dz=seg['direction'];x,z=seg['start']
                    for n in range(seg['length']+1):
                        for off in range(-(width//2),width//2+1):
                            xx,zz=x+dx*n-dz*off,z+dz*n+dx*off
                            self.assertNotEqual(m.get(xx,0,zz),'minecraft:air')
                            for y in (1,2):self.assertEqual(m.get(xx,y,zz),'minecraft:air')

    def test_blocker_and_invalid_geometry(self):
        m,d=make_corridor({'path_xz':[[0,0],[0,24]]})
        m.box((int(m.origin[0]),1,12),(int(m.maximum[0]),int(m.maximum[1]),12),'minecraft:stone')
        self.assertFalse(walk_paths(m,d['portals'][0]['center_floor_xyz'],{'exit':d['portals'][1]['center_floor_xyz']})['all_targets_reached'])
        for c in ({'path_xz':[[0,0],[20,20]]}, {'path_xz':[[0,0],[0,20],[0,0]]},
                  {'path_xz':[[0,0],[0,30],[30,30],[30,10],[-10,10]]},
                  {'path_xz':[[0,0],[0,30],[30,30],[30,5],[5,5],[5,20]]},
                  {'clear_width':4}, {'column_spacing':True}, {'roof_curve':float('nan')}):
            with self.subTest(config=c),self.assertRaises(ValueError):make_corridor(c)

    def test_dimensions_materials_and_roundtrip(self):
        with tempfile.TemporaryDirectory() as directory:
            for i,c in enumerate(({}, {'path_xz':[[0,0],[32,0]],'clear_width':7,'column_height':12,'roof_rise':6,'eave_overhang':4,'railings':False},
                                  {'path_xz':[[0,0],[0,20]],'column_height':4,'roof_rise':1,'eave_overhang':1})):
                self.assertTrue(all(export_corridor(c,directory,f'corridor-{i}',False)['design']['checks'].values()))


class TowerTests(unittest.TestCase):
    def test_complete_floor_does_not_hide_an_open_perimeter_seam(self):
        m,d=make_tower({});up=d['levels'][1];p=d['config']['palette']
        solids=list({p[k].split('[')[0] for k in ('floor','foundation','beam','column','roof','trim','wall')})
        for x,z in ((0,-up['half_z']),(0,up['half_z']),(-up['half_x'],0),(up['half_x'],0)):
            old=m.get(x,up['floor_y']-1,z);m.set(x,up['floor_y']-1,z,'minecraft:air')
            a=audit_frame_seams(m,d['levels'],d['top_roof'],solids)
            self.assertFalse(a['passed']);self.assertIn([x,up['floor_y']-1,z],a['missing_or_partial_seams'])
            self.assertTrue(walk_paths(m,[2,d['levels'][0]['floor_y'],0],{'upper':[2,up['floor_y'],0]})['all_targets_reached'])
            m.set(x,up['floor_y']-1,z,old)
        m.set(0,up['floor_y']-1,up['half_z'],'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
        self.assertFalse(audit_frame_seams(m,d['levels'],d['top_roof'],solids)['passed'])

    def test_top_fascia_and_existing_lower_anchor_are_independently_audited(self):
        m,d=make_tower({});p=d['config']['palette']
        solids=list({p[k].split('[')[0] for k in ('floor','foundation','beam','column','roof','trim','wall')})
        lo,up=d['levels'][:2];xyz=(up['column_x'][1],up['floor_y']-3,lo['half_z'])
        m.set(*xyz,'minecraft:air');a=audit_frame_seams(m,d['levels'],d['top_roof'],solids)
        self.assertFalse(a['passed']);self.assertIn(list(xyz),a['broken_lower_anchors'])
        m.set(*xyz,p['beam']);top=d['levels'][-1]
        m.set(0,top['floor_y']+top['height']-2,top['half_z'],'minecraft:air')
        self.assertFalse(audit_frame_seams(m,d['levels'],d['top_roof'],solids)['passed'])

    def test_storey_counts_odd_heights_mixed_heights_and_widths(self):
        for heights in ([8,8],[9,9],[11,11,11],[12]*4,[10]*5,[9,12,8],[12,8,11],[8,11,9,12,10]):
            for width in (2,3):
                with self.subTest(heights=heights,width=width):
                    m,d=make_tower(tower_config(heights,width))
                    for i,flight in enumerate(d['flights']):
                        self.assertEqual(flight['first_steps']+flight['second_steps'],heights[i])
                        self.assertEqual(len(flight['stair_cells_xyz']),heights[i]*width)
                        targets={name:flight[name] for name in ('lower_approach','mid_landing','upper_exit')}
                        self.assertTrue(walk_paths(m,[2,d['levels'][i]['floor_y'],0],targets)['all_targets_reached'])
                        # Both landing sides and back need guard material above
                        # the landing, even when later floors use different rises.
                        x0,z0,x1,z1=d['stair_core_xz'];_,y,z=flight['mid_landing']
                        for x in (x0-1,x1+1):
                            for zz in (z,z+1):self.assertNotEqual(m.get(x,y+1,zz),'minecraft:air')
                        for x in range(x0,x1+1):self.assertNotEqual(m.get(x,y+1,z+2),'minecraft:air')
                    # Top-storey front rim blocks the unused upward flight.
                    x0,z0,x1,z1=d['stair_core_xz'];y=d['levels'][-1]['floor_y']
                    for x in range(x0,x0+width):self.assertNotEqual(m.get(x,y+1,z0-1),'minecraft:air')

    def test_stair_well_barrier_prevents_upper_floor_access(self):
        m,d=make_tower({})
        # Close the entire first inter-floor opening with a three-cell slab.
        # A roof or guard support must not offer another climbable route.
        x0,z0,x1,z1=d['stair_core_xz'];y=d['levels'][1]['floor_y']
        m.box((x0,y-1,z0),(x1,y+1,z1),'minecraft:stone')
        result=walk_paths(m,[2,d['levels'][0]['floor_y'],0],{'upper':[2,y,0]})
        self.assertFalse(result['all_targets_reached'])

    def test_taper_export_and_invalid_dimensions(self):
        with tempfile.TemporaryDirectory() as directory:
            for i,c in enumerate(({},tower_config([9,12,8],2))):
                self.assertTrue(all(export_tower(c,directory,f'tower-{i}',False)['design']['checks'].values()))
        for c in ({'levels':TOWER['levels'][:1]}, {'stair_width':4}, {'top_roof':{'form':'unknown'}},
                  {'levels':[{'bays_x':[6,8,6],'bays_z':[6,8],'height':10}]*2},
                  {'levels':list(reversed(TOWER['levels']))}, tower_config([7,10]),tower_config([13,10])):
            with self.subTest(config=c),self.assertRaises(ValueError):make_tower(c)


if __name__=='__main__':unittest.main(verbosity=2)
