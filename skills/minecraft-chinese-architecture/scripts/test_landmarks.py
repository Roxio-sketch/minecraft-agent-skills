"""Landmark approach widths, roof boundaries and supported teaching components."""
from pathlib import Path
import tempfile
import unittest

import numpy as np

from generate_landmark import make_landmark, export, gauge
from generate_courtyard import walk_paths


class LandmarkTests(unittest.TestCase):
    def test_polygon_footprints_and_supported_columns(self):
        for sides,radius in ((4,8),(6,12),(8,16),(0,24)):
            m,d=make_landmark({'kind':'polygon_pavilion','parameters':{'sides':sides,'radius':radius}})
            self.assertTrue(all(d['checks'].values()));self.assertEqual(len(d['columns_xz']),sides or 8)
            self.assertEqual(d['roof_footprint_cells'],sum(gauge(x,z,sides,radius)<=1 for x in range(-radius,radius+1) for z in range(-radius,radius+1)))
            self.assertEqual(m.get(radius,11,radius),'minecraft:air')
            self.assertEqual(m.get(0,18,0),'minecraft:gold_block')

    def test_paifang_distinct_bays_and_low_clearance(self):
        for mid,side in ((8,6),(10,8),(16,12)):
            m,d=make_landmark({'kind':'paifang','parameters':{'central_bay':mid,'side_bay':side}})
            self.assertTrue(all(d['checks'].values()));self.assertEqual(np.diff(d['post_x']).tolist(),[side,mid,side])
            self.assertEqual(len(d['roof_components']),3)

    def test_city_gate_arch_shortcut_and_rooftop_stairs(self):
        for params in ({},{'half_width':22,'half_depth':12,'wall_height':16,'arch_width':11,'arch_height':10}):
            m,d=make_landmark({'kind':'city_gate','parameters':params});p=d['config']['parameters']
            self.assertTrue(all(d['checks'].values()))
            original=d['routes']['routes_floor_xyz']['inside'];self.assertTrue(any(z==0 and x==0 and y==0 for x,y,z in original))
            m.box((-p['arch_width']//2,1,-p['half_depth']),(p['arch_width']//2,p['arch_height'],-p['half_depth']),'minecraft:stone')
            paths=walk_paths(m,d['start_floor_xyz'],{'inside':d['target_floors_xyz']['inside']})
            self.assertTrue(paths['all_targets_reached']) # a finite wall segment can be walked around
            self.assertGreater(len(paths['routes_floor_xyz']['inside']),len(original))

    def test_extended_roof_shapes_and_flat_crown(self):
        for form in ('lu_ding','cross','t_shape','fan'):
            m,d=make_landmark({'kind':'roof_study','parameters':{'form':form}})
            self.assertTrue(all(d['checks'].values()));xyz=d['surface_xyz'];surface={(x,z):y for x,y,z in xyz}
            if form=='lu_ding':
                self.assertEqual({surface[x,z] for x in range(-4,5) for z in range(-4,5)},{8})
            elif form in ('cross','t_shape'):
                self.assertNotIn((16,12),surface);self.assertIn((0,12),surface)
                self.assertEqual((0,-12) in surface,form=='cross')
            else:self.assertNotIn((0,0),surface);self.assertTrue(all(z>=0 for x,z in surface))

    def test_frame_modes_and_multiple_component_scales(self):
        for mode in ('beam_lift','post_tie'):
            m,d=make_landmark({'kind':'timber_frame','parameters':{'mode':mode}})
            self.assertTrue(all(d['checks'].values()));self.assertGreater(len(d['purlins_xy']),3)
            self.assertTrue(all('spruce_log[axis=z]' in m.get(x,y,0) for x,y in d['purlins_xy']))
        for kind,key,values in [('bracket_study','tiers',(1,4)),('caisson','tiers',(2,6))]:
            for value in values:
                m,d=make_landmark({'kind':kind,'parameters':{key:value}});self.assertTrue(all(d['checks'].values()))

    def test_exports_and_invalid_constraints(self):
        with tempfile.TemporaryDirectory() as folder:
            for kind in ('polygon_pavilion','paifang','city_gate','roof_study','timber_frame','bracket_study','caisson'):
                r=export({'kind':kind},folder,kind,False);self.assertTrue(r['design']['checks']['schematic_state_roundtrip'])
        for c in ({'kind':'polygon_pavilion','parameters':{'sides':5}},
                  {'kind':'paifang','parameters':{'central_bay':9}},
                  {'kind':'city_gate','parameters':{'wall_height':9,'arch_height':10}},
                  {'kind':'roof_study','parameters':{'half_x':8,'arm_half_width':8}},
                  {'kind':'caisson','parameters':{'radius':7,'tiers':6}}):
            with self.assertRaises(ValueError):make_landmark(c)


if __name__=='__main__':unittest.main(verbosity=2)
