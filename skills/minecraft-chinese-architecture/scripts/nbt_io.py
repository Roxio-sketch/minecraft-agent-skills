"""Small Java-edition big-endian NBT reader; arrays remain numpy views."""
import gzip
import io
import struct
import numpy as np


class Reader:
    def __init__(self, data, skip_names=()):
        self.data = memoryview(data)
        self.pos = 0
        self.skip_names = set(skip_names)

    def take(self, n):
        if n < 0 or self.pos + n > len(self.data):
            raise ValueError("Truncated NBT or negative length")
        out = self.data[self.pos:self.pos + n]
        self.pos += n
        return out

    def number(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]

    def string(self):
        return bytes(self.take(self.number('>H'))).decode('utf-8', 'replace')

    def skip(self, tag):
        if 1 <= tag <= 6:
            self.take((1,2,4,8,4,8)[tag - 1])
        elif tag == 8:
            self.take(self.number('>H'))
        elif tag in (7, 11, 12):
            self.take(self.number('>i') * {7:1,11:4,12:8}[tag])
        elif tag == 9:
            subtype = self.number('>B'); n = self.number('>i')
            if n < 0: raise ValueError('Negative NBT list length')
            if 1 <= subtype <= 6:
                self.take(n * (1,2,4,8,4,8)[subtype - 1])
            else:
                for _ in range(n): self.skip(subtype)
        elif tag == 10:
            while True:
                subtype = self.number('>B')
                if not subtype: break
                self.take(self.number('>H')); self.skip(subtype)
        else:
            raise ValueError(f'Unsupported NBT type {tag}')

    def value(self, tag):
        if 1 <= tag <= 6:
            return self.number(('>b','>h','>i','>q','>f','>d')[tag - 1])
        if tag == 8: return self.string()
        if tag in (7,11,12):
            n = self.number('>i')
            dtype = {7:'i1',11:'>i4',12:'>i8'}[tag]
            return np.frombuffer(self.take(n * np.dtype(dtype).itemsize), dtype=dtype)
        if tag == 9:
            subtype = self.number('>B'); n = self.number('>i')
            if n < 0: raise ValueError('Negative NBT list length')
            return [self.value(subtype) for _ in range(n)]
        if tag == 10:
            result = {}
            while True:
                subtype = self.number('>B')
                if not subtype: return result
                name = self.string()
                if name in self.skip_names: self.skip(subtype)
                else: result[name] = self.value(subtype)
        raise ValueError(f'Unsupported NBT type {tag}')

    def root(self):
        tag = self.number('>B')
        if tag != 10: raise ValueError('NBT root is not a compound')
        self.string()
        return self.value(tag)


def loads(data, skip_names=()):
    if data[:2] == b'\x1f\x8b': data = gzip.decompress(data)
    return Reader(data, skip_names).root()


def state_string(entry):
    name = str(entry['Name'])
    props = entry.get('Properties', {})
    if props:
        name += '[' + ','.join(f'{k}={v}' for k,v in sorted(props.items())) + ']'
    return name


def decode_states(palette, data, count=4096, packing='padded'):
    """Anvil indexes, X fastest: pre-1.16 continuous or 1.16+ padded longs."""
    n = len(palette)
    if not 1 <= n <= 65536: raise ValueError('Empty or excessive block palette')
    if packing not in ('padded', 'continuous'): raise ValueError('Unknown packing')
    if n == 1 and not len(data): return np.zeros(count, dtype=np.uint16)
    bits = max(4, (n - 1).bit_length())
    words = np.asarray(data).astype(np.uint64)
    per_long = 64 // bits
    expected = (count + per_long - 1) // per_long if packing == 'padded' else (count * bits + 63) // 64
    if len(words) != expected:
        raise ValueError(f'Expected {expected} packed longs for {n} states, got {len(words)}')
    if packing == 'padded':
        shifts = (np.arange(per_long, dtype=np.uint64) * bits)[None, :]
        values = ((words[:, None] >> shifts) & ((1 << bits)-1)).reshape(-1)[:count]
    else:
        bit_start = np.arange(count, dtype=np.uint64) * bits
        indexes, shifts = bit_start // 64, bit_start % 64
        values = words[indexes] >> shifts
        cross = shifts + bits > 64
        values[cross] |= words[indexes[cross] + 1] << (64 - shifts[cross])
        values &= (1 << bits) - 1
    if np.any(values >= n): raise ValueError('Block palette index out of bounds')
    return values.astype(np.uint16)


class StreamingMetadataReader:
    """Read metadata from huge gzip NBT; consume large arrays without retaining them."""
    def __init__(self, stream):
        self.stream = stream

    def take(self, n):
        if n < 0: raise ValueError('Negative NBT length')
        data = self.stream.read(n)
        if len(data) != n: raise ValueError('Truncated streaming NBT')
        return data

    def number(self, fmt):
        return struct.unpack(fmt,self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.number('>H')).decode('utf-8','replace')

    def value(self,tag):
        if 1 <= tag <= 6: return self.number(('>b','>h','>i','>q','>f','>d')[tag-1])
        if tag == 8: return self.string()
        if tag in (7,11,12):
            n=self.number('>i')
            if n<0: raise ValueError('Negative NBT array length')
            width={7:1,11:4,12:8}[tag]
            if n<=16:
                fmt={7:'b',11:'i',12:'q'}[tag]
                return list(struct.unpack('>'+fmt*n,self.take(n*width)))
            remaining=n*width
            while remaining:
                step=min(remaining,1024*1024);self.take(step);remaining-=step
            return {'nbt_array_type':tag,'length':n,'retained':False}
        if tag == 9:
            subtype=self.number('>B');n=self.number('>i')
            if n<0:raise ValueError('Negative list length')
            return [self.value(subtype) for _ in range(n)]
        if tag == 10:
            out={}
            while True:
                subtype=self.number('>B')
                if not subtype:return out
                name=self.string();out[name]=self.value(subtype)
        raise ValueError(f'Unsupported NBT type {tag}')

    def root(self):
        if self.number('>B')!=10:raise ValueError('Root must be compound')
        self.string();return self.value(10)
