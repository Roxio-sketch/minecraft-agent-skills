"""Observed compact bays retain columns, glazing, access and export semantics."""
import tempfile
import unittest
import numpy as np

from facade_kernel import panel_roles,LEGACY_PATTERNS,BOUNDED_PATTERNS
from generate_building import make_building,verify,export
from roof_kernel import FORMS


class CompactFacadeTests(unittest.TestCase):
    def test_bounded_windows_keep_real_wall_margins_at_different_scales(self):
        for pattern in BOUNDED_PATTERNS:
            for width in (3, 4, 5, 8, 15, 31, 60):
                for height in (3, 4, 5, 8, 16, 30):
                    with self.subTest(pattern=pattern,width=width,height=height):
                        roles=panel_roles(width,height,pattern)
                        self.assertFalse(roles[[0,-1]].any())
                        self.assertFalse(roles[:,[0,-1]].any())
                        ys,xs=np.where(roles==1)
                        self.assertGreater(len(xs),0)
                        self.assertLessEqual(int(xs.max()-xs.min()+1),7)
                        self.assertLessEqual(int(ys.max()-ys.min()+1),5)
        with self.assertRaises(ValueError):panel_roles(2,8,'framed')

    def test_explicit_panel_override_preserves_columns_door_and_other_panels(self):
        base={'bays_x':[8,8,8],'bays_z':[8,8],'column_height':9,'facade_pattern':'framed'}
        before,bd,bc=make_building(base)
        m,d,c=make_building({**base,'facade_overrides':[{'face':'left','bay':1,'pattern':'framed_pair'}]})
        self.assertTrue(all(verify(m,d,c).values()))
        changed=[]
        for y,z,x in np.argwhere(np.array(before.palette)[before.blocks]!=np.array(m.palette)[m.blocks]):
            changed.append((m.origin+[x,y,z]).tolist())
        self.assertTrue(changed)
        self.assertTrue(all(q[0]==-12 and 1<=q[2]<=7 for q in changed))
        for overrides in ([{'face':'front','bay':1,'pattern':'solid'}],
                          [{'face':'left','bay':9,'pattern':'framed'}],
                          [{'face':'left','bay':0,'pattern':'framed'}]*2):
            with self.assertRaises(ValueError):make_building({**base,'facade_overrides':overrides})

    def test_narrow_paired_window_keeps_one_opening_and_its_wall(self):
        # A five-cell panel has room for one glazed cell plus rim and wall.
        # It cannot express two windows without sacrificing the wall margin.
        for pattern in BOUNDED_PATTERNS:
            roles=panel_roles(5,10,pattern)
            self.assertEqual(np.count_nonzero(np.any(roles==1,axis=0)),1)
            self.assertFalse(roles[:,[0,-1]].any())

    def test_inhabited_default_is_bounded_and_normalized_config_reproduces_it(self):
        from generate_inhabited import make_inhabited
        m,d=make_inhabited({'use':'library'})
        self.assertEqual(d['config']['building']['facade_pattern'],'framed')
        again,_=make_inhabited(d['config'])
        np.testing.assert_array_equal(np.array(m.palette)[m.blocks],np.array(again.palette)[again.blocks])
        _,old=make_inhabited({'use':'library','building':{'facade_pattern':'cross'}})
        self.assertEqual(old['config']['building']['facade_pattern'],'cross')

    def test_narrow_and_short_apertures_keep_infill(self):
        for width in range(1,16):
            for height in range(5,15):
                for pattern in LEGACY_PATTERNS:
                    with self.subTest(width=width,height=height,pattern=pattern):
                        a=panel_roles(width,height,pattern)
                        self.assertTrue(np.all(a[:2]==0))
                        self.assertTrue(np.all(a[-2:]==0))
                        if pattern=='solid':self.assertTrue(np.all(a==0))
                        else:
                            self.assertGreater(np.count_nonzero(a==1),0)
                            self.assertEqual(a[2,0],1)
                            self.assertEqual(a[-3,-1],1)
        np.testing.assert_array_equal(panel_roles(2,5),[[0,0],[0,0],[1,1],[0,0],[0,0]])
        for args in ((0,5),(3,4),(True,7),(3,7,'unknown')):
            with self.assertRaises(ValueError):panel_roles(*args)

    def test_compact_measured_grid_and_four_block_columns(self):
        for pattern in LEGACY_PATTERNS:
            for form in FORMS:
                c={'bays_x':[3,4,3],'bays_z':[3,4,3],'column_height':4,'platform_height':1,
                   'platform_margin':2,'eave_overhang':2,'stair_width':3,'facade_pattern':pattern,
                   'roof':{'form':form,'rise':3,'uplift':0,'gable_inset':2}}
                with self.subTest(form=form,pattern=pattern):
                    m,d,config=make_building(c)
                    self.assertTrue(all(verify(m,d,config).values()))
                    self.assertEqual(d['column_x'],[-5,-2,2,5])
                    self.assertEqual(d['column_z'],[-5,-2,2,5])
                    self.assertEqual(d['column_wood_y'],[3,6])
                    self.assertEqual(d['doorway']['x_min'],-1)
                    self.assertEqual(d['doorway']['x_max'],1)
                    for panel in d['facade_panels']:
                        n=0
                        for u in range(panel['interior_range'][0],panel['interior_range'][1]+1):
                            for y in range(panel['y_range'][0],panel['y_range'][1]+1):
                                x,z=(u,panel['fixed_coordinate']) if panel['axis']=='x' else (panel['fixed_coordinate'],u)
                                n+=m.get(x,y,z)==config['palette']['window']
                        self.assertEqual(n,panel['infill_voxels'])
                        if pattern!='solid':self.assertGreater(n,0)
                    if form=='overhanging_gable' and pattern=='cross':
                        with tempfile.TemporaryDirectory() as folder:
                            report=export(c,folder,'compact',False)
                            self.assertTrue(report['checks']['schematic_state_roundtrip'])

    def test_two_block_entrance_and_open_pavilion(self):
        for kind in ('hall','pavilion'):
            c={'kind':kind,'bays_x':[3,3],'bays_z':[3,3],'column_height':4,'stair_width':3,
               'roof':{'form':'pyramidal','rise':2,'uplift':0}}
            m,d,c=make_building(c)
            self.assertTrue(all(verify(m,d,c).values()))
            self.assertEqual(d['doorway']['x_max']-d['doorway']['x_min']+1,2)


if __name__=='__main__':unittest.main(verbosity=2)
