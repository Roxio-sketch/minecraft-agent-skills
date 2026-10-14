"""Change only the Forge render stage in a known, already-reobfuscated lab JAR.

This is a build-environment fallback for the 2026-10-04 cloud occlusion fix.
Normal builds should compile BlackHoleClient.java instead.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import struct
import zipfile
from pathlib import Path


EXPECTED_SHA256 = "cd6614ddf24be7508a2b1c54a1aac519df8d63d1d4da300401297ecaf1794824"
CLASS_PATH = "dev/blackholelab/BlackHoleClient.class"
OLD = b"AFTER_PARTICLES"
NEW = b"AFTER_LEVEL"


def replace_utf8_constant(data: bytes) -> bytes:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java class file")
    count = struct.unpack_from(">H", data, 8)[0]
    source = io.BytesIO(data)
    out = io.BytesIO()
    out.write(source.read(10))
    changed = 0
    index = 1
    while index < count:
        tag_byte = source.read(1)
        if not tag_byte:
            raise ValueError("Truncated constant pool")
        tag = tag_byte[0]
        out.write(tag_byte)
        if tag == 1:
            size_data = source.read(2)
            size = struct.unpack(">H", size_data)[0]
            value = source.read(size)
            if value == OLD:
                value = NEW
                changed += 1
            out.write(struct.pack(">H", len(value)))
            out.write(value)
        else:
            sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2,
                     9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2,
                     17: 4, 18: 4, 19: 2, 20: 2}
            if tag not in sizes:
                raise ValueError(f"Unknown constant-pool tag {tag}")
            chunk = source.read(sizes[tag])
            if len(chunk) != sizes[tag]:
                raise ValueError("Truncated constant-pool entry")
            out.write(chunk)
            if tag in (5, 6):
                index += 1
        index += 1
    out.write(source.read())
    if changed != 1 or data.count(OLD) != 1:
        raise ValueError(f"Expected exactly one stage constant, got {changed}")
    return out.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    source_hash = hashlib.sha256(args.source.read_bytes()).hexdigest()
    if source_hash != EXPECTED_SHA256:
        raise ValueError(f"Unexpected source JAR SHA256: {source_hash}")
    if args.source.resolve() == args.destination.resolve():
        raise ValueError("Destination must differ from source")
    args.destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.source) as original, zipfile.ZipFile(args.destination, "w") as output:
        if original.testzip() is not None:
            raise ValueError("Input JAR has a corrupt entry")
        for info in original.infolist():
            data = original.read(info.filename)
            if info.filename == CLASS_PATH:
                data = replace_utf8_constant(data)
            output.writestr(info, data)
    with zipfile.ZipFile(args.destination) as patched:
        if patched.testzip() is not None:
            raise ValueError("Output JAR has a corrupt entry")
        before = zipfile.ZipFile(args.source).read(CLASS_PATH)
        after = patched.read(CLASS_PATH)
        if OLD in after or NEW not in after or before == after:
            raise ValueError("Stage patch not present")
    print(f"source_sha256={source_hash}")
    print(f"patched_sha256={hashlib.sha256(args.destination.read_bytes()).hexdigest()}")
    print(f"output={args.destination}")


if __name__ == "__main__":
    main()
