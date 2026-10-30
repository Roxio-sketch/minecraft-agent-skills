"""Verify half-block roof surfaces and explicit thin-post selections."""
import json
from pathlib import Path
import tempfile
import unittest
import numpy as np

from measure_components import run, shape_line_profile
from voxel_model import Model


class Measurements(unittest.TestCase):
    def test_slab_surface_heights_and_negative_coordinates(self):
        p=['minecraft:stone_slab[type=bottom]','minecraft:stone_slab[type=top]','minecraft:stone_slab[type=double]']
        v=np.array([[[0,1,2]]]);mask=np.ones_like(v,dtype=bool)
        profile=shape_line_profile(v,p,mask,[-3,7,-2,-1,7,-2],'x',0)
        self.assertEqual(profile['fixed_coordinate_1_16'],-24)
        self.assertEqual(profile['runs_start_end_exclusive_surface_y_1_16'],[[-48,-32,120],[-32,0,128]])
        self.assertEqual(profile['approximate_shape_states'],[])

    def test_fence_column_filter_and_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);m=Model((0,0,0),(6,6,4))
            m.box((0,5,0),(6,5,4),'minecraft:stone_slab[type=bottom]')
            for x in (1,5):m.box((x,1,2),(x,3,2),'minecraft:acacia_fence')
            # A two-cell incomplete post must not be measured as a third column.
            m.box((3,1,2),(3,2,2),'minecraft:acacia_fence')
            m.save(root/'sample.npz');(root/'sample.json').write_text(json.dumps({'sha256':'verified'}),encoding='utf8')
            sample={'id':'sample','label':'sample','source':'test','box':[0,0,0,6,6,4],'roof_box':[0,5,0,6,5,4],
                    'roof_pattern':'stone_slab','shape_profiles':True,'source_sha256':'verified','interpretation':'Test fixture',
                    'column_row':{'axis':'x','fixed':2,'range':[0,6],'y_range':[1,3],'material_pattern':'acacia_fence'}}
            annotations=root/'annotations.json';annotations.write_text(json.dumps({'samples':[sample]}),encoding='utf8')
            run(root,annotations,root/'out.json')
            result=json.loads((root/'out.json').read_text())['samples'][0]['column_rows']['column_row']
            self.assertEqual(result['positions'],[1,5]);self.assertEqual(result['spacings'],[4])
            self.assertEqual(result['material_segment_height'],3)
            sample['source_sha256']='different';annotations.write_text(json.dumps({'samples':[sample]}),encoding='utf8')
            with self.assertRaises(ValueError):run(root,annotations,root/'out.json')


if __name__=='__main__':unittest.main(verbosity=2)
