"""Collection error isolation and exact geometry deduplication."""
import json
from pathlib import Path
import tempfile
import unittest

import numpy as np

from generate_hall import write_schematic
from import_collection import geometry_fingerprint, run
from voxel_model import Model


class CollectionTests(unittest.TestCase):
    def test_fingerprint_ignores_palette_order_and_unused_states(self):
        a=np.array([[[0,1],[1,0]]],np.uint16)
        b=1-a
        self.assertEqual(geometry_fingerprint(a,['minecraft:air','minecraft:stone']),
                         geometry_fingerprint(b,['minecraft:stone','minecraft:air','minecraft:dirt']))
        self.assertNotEqual(geometry_fingerprint(a,['minecraft:air','minecraft:stone']),
                            geometry_fingerprint(a,['minecraft:air','minecraft:dirt']))

    def test_one_bad_file_does_not_hide_good_files(self):
        with tempfile.TemporaryDirectory() as folder:
            source=Path(folder)/'source';source.mkdir();output=Path(folder)/'output'
            m=Model((0,0,0),(2,2,2));m.set(1,0,1,'minecraft:stone')
            write_schematic(m,source/'one.schem','one');write_schematic(m,source/'two.schem','two')
            (source/'broken.nbt').write_bytes(b'not valid NBT')
            r=run(source,output)
            self.assertEqual((r['files_imported'],r['files_rejected'],r['distinct_geometry_count']),(2,1,1))
            self.assertEqual(r['exact_geometry_duplicates'],[['one.schem','two.schem']])
            self.assertTrue((output/'one.schem.npz').exists())
            self.assertFalse((output/'broken.nbt.npz').exists())
            self.assertEqual(json.loads((output/'collection.report.json').read_text())['files_rejected'],1)


if __name__=='__main__':unittest.main(verbosity=2)
