"""Meaningful bit-packing, coordinate, corruption, and real-file cross-checks.

Run with Python from this directory. nbtlib is an independent optional test oracle.
"""
import io
import json
from pathlib import Path
import unittest
import numpy as np
import nbtlib
from nbt_io import loads, decode_states, state_string
from archive_scan import world_zip, regions, region_locations, chunk_payload, normalize_chunk, section_data


class PackingTests(unittest.TestCase):
    def test_continuous_five_and_nine_bit_boundaries(self):
        for n in (17, 257):
            bits=(n-1).bit_length()
            values=[i%n for i in range(4096)]
            packed=sum(v << (i*bits) for i,v in enumerate(values))
            words=np.array([(packed >> s) & ((1<<64)-1) for s in range(0,4096*bits,64)],dtype=np.uint64)
            got=decode_states(list(range(n)),words.view(np.int64),packing='continuous')
            np.testing.assert_array_equal(got,values)
            with self.assertRaises(ValueError):decode_states(list(range(n)),words,packing='padded')

    def test_legacy_normalization_and_rejection(self):
        for version,packing in ((1976,'continuous'),(2529,'padded'),(2730,'padded')):
            root={'DataVersion':version,'Level':{'xPos':-2,'zPos':3,'Sections':[
                {'Y':-1},{'Y':0,'Palette':[{'Name':'minecraft:stone'}],'BlockStates':np.zeros(256,np.int64)}]}}
            d=normalize_chunk(root)
            self.assertEqual(d['sections'][1]['block_states']['_packing'],packing)
            self.assertIsNone(section_data(d['sections'][0]))
            self.assertEqual(section_data(d['sections'][1])[1].shape,(16,16,16))
        with self.assertRaises(ValueError):normalize_chunk({'DataVersion':1343,'Level':{'xPos':0,'zPos':0,'Sections':[{'Y':0,'Blocks':b'123'}]}})
        with self.assertRaises(ValueError):normalize_chunk({'DataVersion':1976,'Level':{'xPos':0,'zPos':0,'Sections':[{'Y':0,'BlockStates':[]}]}})
        with self.assertRaises(ValueError):decode_states(['stone'],[-1]*256,packing='continuous')

    def test_padded_five_bit_boundary(self):
        # Five-bit values leave four unused high bits per long; entry 12 is in word 1.
        values = np.arange(4096, dtype=np.uint16) % 17
        words=[]
        for start in range(0,4096,12):
            word=sum(int(v) * (2 ** (5*j)) for j,v in enumerate(values[start:start+12]))
            words.append(word)
        got=decode_states(list(range(17)),np.array(words,dtype=np.uint64))
        np.testing.assert_array_equal(got,values)
        self.assertEqual(int(got[12]),12)
        self.assertEqual(int(got.reshape(16,16,16)[1,0,0]),256%17)
        self.assertEqual(int(got.reshape(16,16,16)[0,1,0]),16)

    def test_signed_high_bit_and_minimum_four_bits(self):
        # -1 is all ones: each 4-bit nibble must be palette index 15.
        got=decode_states(list(range(16)),np.full(256,-1,np.int64))
        self.assertTrue(np.all(got==15))
        got=decode_states(['stone'],[])
        self.assertEqual(got.size,4096)
        self.assertFalse(got.any())

    def test_corruption_is_not_air(self):
        with self.assertRaises(ValueError):decode_states(list(range(17)),np.zeros(256,np.uint64))
        with self.assertRaises(ValueError):decode_states(list(range(2)),np.full(256,-1,np.int64))
        with self.assertRaises(ValueError):loads(b'\x0a\x00\x00\x03\x00\x01x\x00')


def real_file_cross_checks(source):
    checked=[]
    with world_zip(source) as (z,_):
        rs,_=regions(z)
        for _,_,info in rs:
            locs=region_locations(z,info)
            if not locs:continue
            raw=z.read(info)
            for slot,sector,sectors in locs:
                payload=chunk_payload(raw,sector,sectors,info.filename)
                ours=loads(payload)
                oracle=nbtlib.File.parse(io.BytesIO(payload))
                legacy='Level' in oracle
                source=oracle['Level'] if legacy else oracle
                ours=normalize_chunk(ours)
                assert int(source['xPos'])==ours['xPos'] and int(source['zPos'])==ours['zPos']
                multi=False
                for a,b in zip(ours['sections'],source['Sections' if legacy else 'sections']):
                    if 'block_states' not in a:continue
                    ap=[state_string(p) for p in a['block_states']['palette']]
                    bp=[state_string(p) for p in (b['Palette'] if legacy else b['block_states']['palette'])]
                    assert ap==bp
                    if len(ap)<2:continue
                    if len(ap)>16:multi=True
                    got=section_data(a)[1].ravel()
                    data=b['BlockStates'] if legacy else b['block_states']['data'];bits=max(4,(len(bp)-1).bit_length());per=64//bits
                    # Independent scalar indexing: every block checked against nbtlib's arrays.
                    if legacy and int(oracle['DataVersion'])<2529:
                        all_bits=sum((int(word)&((1<<64)-1))<<(64*i) for i,word in enumerate(data))
                        expected=[(all_bits>>(i*bits))&((1<<bits)-1) for i in range(4096)]
                    else:
                        expected=[(int(data[i//per]) >> ((i%per)*bits)) & ((1<<bits)-1) for i in range(4096)]
                    np.testing.assert_array_equal(got,np.array(expected))
                if multi:
                    checked.append({'chunk':[ours['xPos'],ours['zPos']],'data_version':ours['DataVersion'],
                                    'schema':ours['_source_schema'],'includes_palette_over_16':True,'all_sections_and_4096_indices_match':True})
                    if len(checked)>=3:return checked
    raise AssertionError('Could not find 3 multi-state chunks')


if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(PackingTests)
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    if not result.wasSuccessful():raise SystemExit(1)
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--source');p.add_argument('--out');args=p.parse_args()
    if args.source:
        checks=real_file_cross_checks(args.source)
        report={'packing_tests':result.testsRun,'independent_nbtlib_cross_checks':checks}
        if args.out:Path(args.out).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(report))
