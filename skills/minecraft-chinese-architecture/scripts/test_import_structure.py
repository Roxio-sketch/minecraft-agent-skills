"""Format regressions from known coordinates and scalar bit-packing fixtures."""
import gzip
import io
import tempfile
import unittest
from pathlib import Path
import nbtlib as n
import numpy as np
from import_structure import load_structure, normalize, packed_litematic, read_nbt, varints
from generate_hall import encode_varints, export, make_model, merge_config


def save(path, root):
    stream=io.BytesIO();n.File(root).write(stream)
    path.write_bytes(gzip.compress(stream.getvalue()))


def packed(values, bits):
    # Independent scalar arbitrary-precision integer oracle, not vector shifts.
    word=sum(int(v) << (i*bits) for i,v in enumerate(values))
    count=(len(values)*bits+63)//64
    data=[(word >> (i*64)) & ((1<<64)-1) for i in range(count)]
    return n.LongArray([v if v<(1<<63) else v-(1<<64) for v in data])


class ImportTests(unittest.TestCase):
    def test_java_structure_sparse_positions_mods_and_variants(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d,'house.nbt')
            palette=n.List[n.Compound]([n.Compound({'Name':n.String(s)}) for s in ['minecraft:stone','example:wall']])
            blocks=n.List[n.Compound]([
                n.Compound({'pos':n.List[n.Int]([2,1,3]),'state':n.Int(1),'nbt':n.Compound({'payload':n.String('ignored')})}),
                n.Compound({'pos':n.List[n.Int]([0,0,0]),'state':n.Int(0)})])
            root={'size':n.List[n.Int]([3,2,4]),'palette':palette,'blocks':blocks,'DataVersion':n.Int(3465)}
            save(path,root)
            a,p,o,r=load_structure(path)
            self.assertEqual(a.shape,(2,4,3))
            self.assertEqual(p[a[1,3,2]],'example:wall')
            self.assertEqual(p[a[0,0,0]],'minecraft:stone')
            self.assertEqual(p[a[0,3,2]],'minecraft:air')
            self.assertEqual(r['source_palette_count'],2)
            self.assertEqual(r['details']['omitted_positions'],22)
            self.assertEqual(r['omitted_block_entities'],1)
            self.assertEqual(r['nonair_namespace_counts'],{'minecraft':1,'example':1})
            other=n.List[n.Compound]([n.Compound({'Name':n.String(s)}) for s in ['minecraft:gold_block','example:door']])
            variants={**root,'palettes':n.List[n.List[n.Compound]]([palette,other])};del variants['palette']
            save(path,variants)
            with self.assertRaises(ValueError):load_structure(path)
            a,p,o,r=load_structure(path,palette_index=1)
            self.assertEqual(p[a[1,3,2]],'example:door')
            with self.assertRaises(ValueError):load_structure(path,palette_index=2)
            for bad_record in [
                n.Compound({'pos':n.List[n.Int]([-1,0,0]),'state':n.Int(0)}),
                n.Compound({'pos':n.List[n.Int]([3,0,0]),'state':n.Int(0)}),
                n.Compound({'pos':n.List[n.Int]([1,0,0]),'state':n.Int(2)}),
                n.Compound({'pos':n.List[n.Int]([0,0,0]),'state':n.Int(0)})]:
                bad={**root,'blocks':n.List[n.Compound]([*blocks,bad_record])};save(path,bad)
                with self.assertRaises(ValueError):load_structure(path)
            save(path,root)
            with self.assertRaises(ValueError):load_structure(path,max_voxels=23)

    def test_cross_long_palette_sizes(self):
        for size in [1,2,3,4,5,17,33,65,129,257,513]:
            values=np.array([(i*31+7)%size for i in range(513)])
            bits=max(2,(size-1).bit_length())
            np.testing.assert_array_equal(packed_litematic(packed(values,bits),len(values),size),values)
        with self.assertRaises(ValueError):packed_litematic([0],33,4)
        with self.assertRaises(ValueError):packed_litematic([3],1,3)

    def test_varints_and_sparse_palette(self):
        values=np.array([0,127,128,16384,2**31-1],dtype=np.uint32)
        np.testing.assert_array_equal(varints(encode_varints(values),len(values)),values)
        for raw,count in [(b'\x80',1),(b'\xff\xff\xff\xff\x08',1),(b'\x00\x00',1),(b'',1)]:
            with self.assertRaises(ValueError):varints(raw,count)
        ids,p=normalize(np.array([22,9,22,9]),{'minecraft:stone':22,'minecraft:cave_air':9})
        self.assertEqual(p.tolist(),['minecraft:air','minecraft:stone'])
        np.testing.assert_array_equal(ids,[1,0,1,0])
        with self.assertRaises(ValueError):normalize(np.array([8]),{'minecraft:air':0})

    def test_sponge_v2_v3_known_coordinates(self):
        with tempfile.TemporaryDirectory() as d:
            a=np.array([0,0,5,5,0,5,0,5,5,5,0,0],dtype=np.uint32).reshape(2,2,3)
            common={'Version':n.Int(2),'DataVersion':n.Int(4189),'Width':n.Short(3),'Height':n.Short(2),'Length':n.Short(2),'Offset':n.IntArray([-7,2,-3])}
            palette=n.Compound({'minecraft:air':n.Int(0),'minecraft:stone':n.Int(5)})
            data=n.ByteArray(np.frombuffer(encode_varints(a),dtype=np.int8))
            for version in [2,3]:
                root=dict(common);root['Version']=n.Int(version)
                if version==2:root.update(Palette=palette,BlockData=data)
                else:root={'Schematic':n.Compound({**root,'Blocks':n.Compound({'Palette':palette,'Data':data})})}
                path=Path(d,f'v{version}.schem');save(path,root)
                blocks,p,o,report=load_structure(path)
                np.testing.assert_array_equal(blocks,a//5)
                np.testing.assert_array_equal(o,[-7,2,-3])
                self.assertEqual(report['nonair_blocks'],6)
                with self.assertRaises(ValueError):load_structure(path,max_voxels=11)

    def test_signed_size_and_multiple_regions(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d,'negative.litematic')
            size=[-3,-2,-4];position=[10,6,-8];values=np.arange(24)%3
            region=n.Compound({'Position':n.Compound({k:n.Int(v) for k,v in zip('xyz',position)}),
                'Size':n.Compound({k:n.Int(v) for k,v in zip('xyz',size)}),
                'BlockStatePalette':n.List[n.Compound]([n.Compound({'Name':n.String(s)}) for s in ['minecraft:air','minecraft:stone','minecraft:oak_planks']]),
                'BlockStates':packed(values,2)})
            root={'Version':n.Int(6),'MinecraftDataVersion':n.Int(4189),'Regions':n.Compound({'A':region,'B':region})}
            for version in [5,6]:
                root['Version']=n.Int(version)
                save(path,root)
                with self.assertRaises(ValueError):load_structure(path)
                b,p,o,r=load_structure(path,'A')
                np.testing.assert_array_equal(o,[8,5,-11])
                np.testing.assert_array_equal(b,values.reshape(2,4,3))
                self.assertEqual(r['details']['signed_size_xyz'],size)
            with self.assertRaises(ValueError):read_nbt(path,max_bytes=8)

    def test_export_v2_roundtrip_and_exact_column_height(self):
        with tempfile.TemporaryDirectory() as d:
            for height in [5,10,13,20]:
                c=merge_config({'column_height':height,'width_bays':3,'depth_bays':1})
                m,details=make_model(c)
                for x in details['column_x']:
                    for z in details['column_z']:
                        # Check material values directly, without trusting report length.
                        states=[m.get(x,y,z) for y in range(c['platform_height']+1,c['platform_height']+height+4)]
                        self.assertEqual(states.count(c['palette']['column']),height)
                export(c,d,'hall',render=False)
                blocks,p,_,_=load_structure(Path(d,'hall.schem'))
                np.testing.assert_array_equal(p[blocks],np.array(m.palette)[m.blocks])


if __name__=='__main__':unittest.main(verbosity=2)
