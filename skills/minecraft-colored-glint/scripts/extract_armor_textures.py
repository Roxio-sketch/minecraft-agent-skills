#!/usr/bin/env python3
"""Extract eight local Minecraft 1.20.1 armor PNGs for the private workbench."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import struct
import tempfile
from zipfile import BadZipFile, ZipFile
import zlib


SCHEMA = "minecraft-armor-preview-source/1"
MEMBERS = {
    f"{material}_{layer}.png":
        f"assets/minecraft/textures/models/armor/{material}_layer_{layer}.png"
    for material in ("iron", "diamond", "netherite", "gold")
    for layer in (1, 2)
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def verify_png(data: bytes, name: str) -> tuple[int, int]:
    """Check PNG structure/CRC and bounded, noninterlaced vanilla pixel data."""
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"{name}: not a PNG")
    offset, chunks, compressed = 8, [], bytearray()
    header = None
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError(f"{name}: truncated PNG chunk")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError(f"{name}: invalid PNG chunk length")
        payload = data[offset + 8:offset + 8 + length]
        expected_crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(kind + payload) & 0xffffffff != expected_crc:
            raise ValueError(f"{name}: PNG CRC mismatch")
        if not chunks and kind != b"IHDR":
            raise ValueError(f"{name}: IHDR must be first")
        if kind == b"IHDR":
            if header is not None or length != 13:
                raise ValueError(f"{name}: invalid IHDR")
            header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            if length or end != len(data):
                raise ValueError(f"{name}: invalid IEND or trailing bytes")
            chunks.append(kind)
            offset = end
            break
        chunks.append(kind)
        offset = end
    if header is None or not chunks or chunks[-1] != b"IEND" or not compressed:
        raise ValueError(f"{name}: incomplete PNG")
    width, height, depth, color, compression, filtering, interlace = header
    if (width, height) != (64, 32):
        raise ValueError(f"{name}: expected vanilla armor size 64x32, got {width}x{height}")
    legal_depths = {0: (1, 2, 4, 8, 16), 2: (8, 16), 3: (1, 2, 4, 8), 4: (8, 16), 6: (8, 16)}
    if color not in legal_depths or depth not in legal_depths[color] or compression or filtering or interlace:
        raise ValueError(f"{name}: unsupported PNG format for vanilla preview")
    if color == 3 and b"PLTE" not in chunks:
        raise ValueError(f"{name}: indexed PNG has no palette")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    stride = (width * channels * depth + 7) // 8 + 1
    expected_size = stride * height
    decoder = zlib.decompressobj()
    pixels = decoder.decompress(bytes(compressed), expected_size + 1)
    if len(pixels) != expected_size or not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
        raise ValueError(f"{name}: invalid or oversized PNG pixel stream")
    if any(pixels[row * stride] > 4 for row in range(height)):
        raise ValueError(f"{name}: invalid PNG scanline filter")
    return width, height


def read_source(source: Path) -> tuple[dict[str, bytes], dict]:
    with ZipFile(source) as archive:
        names = [item.filename for item in archive.infolist()]
        for member in ("version.json", *MEMBERS.values()):
            if names.count(member) != 1:
                raise ValueError(f"Required archive member missing or duplicated: {member}")
            info = archive.getinfo(member)
            if info.is_dir() or info.file_size > 1024 * 1024:
                raise ValueError(f"Unexpected archive member type/size: {member}")
        version = json.loads(archive.read("version.json"))
        if not isinstance(version, dict) or version.get("id") != "1.20.1":
            raise ValueError("Expected the Minecraft Java 1.20.1 client.jar (version.json id)")
        images = {name: archive.read(member) for name, member in MEMBERS.items()}
        # Validate every source before creating any destination or writing a file.
        for name, data in images.items():
            verify_png(data, name)
    provenance = {
        "schema": SCHEMA,
        "origin": "User-specified local Minecraft Java 1.20.1 client.jar",
        "source_archive": str(source),
        "source_archive_sha256": file_digest(source),
        "minecraft_version": "1.20.1",
        "extracted_at_utc": datetime.now(timezone.utc).isoformat(),
        "purpose": "Private local armor design preview",
        "redistribution": "Local extraction only; these Minecraft textures are not part of the distributed skill.",
        "files": [
            {"name": name, "archive_member": MEMBERS[name], "sha256": digest(data), "width": 64, "height": 32}
            for name, data in images.items()
        ],
    }
    return images, provenance


def checked_target(root: Path, name: str) -> Path:
    target = root / "textures" / name
    if target.is_symlink() or (root / "textures").is_symlink():
        raise ValueError(f"Refusing a symlink target: {target}")
    try:
        target.resolve().relative_to(root)
    except ValueError as exc:
        raise ValueError(f"Destination escapes the requested output directory: {target}") from exc
    if target.exists() and not target.is_file():
        raise ValueError(f"Destination is not a regular file: {target}")
    return target


def write_outputs(root: Path, images: dict[str, bytes], provenance: dict, overwrite: bool) -> None:
    manifest_target = checked_target(root, "source.json")
    targets = {name: checked_target(root, name) for name in images}
    old_records = {}
    if manifest_target.exists():
        if not overwrite:
            raise ValueError(f"Output already exists (use --overwrite for a previous extraction): {manifest_target}")
        try:
            previous = json.loads(manifest_target.read_text(encoding="utf-8"))
            if previous.get("schema") != SCHEMA or not isinstance(previous.get("files"), list):
                raise ValueError("Unrecognized provenance")
            old_records = {item["name"]: item for item in previous["files"]}
            if set(old_records) != set(MEMBERS):
                raise ValueError("Unexpected provenance members")
        except (ValueError, TypeError, KeyError, AttributeError) as exc:
            raise ValueError(f"Refusing to overwrite an unrelated source.json: {manifest_target}") from exc
    expected_existing = {}
    for name, target in targets.items():
        if target.exists():
            if not overwrite:
                raise ValueError(f"Output already exists: {target}")
            existing_hash = file_digest(target)
            record = old_records.get(name, {})
            if record.get("archive_member") != MEMBERS[name] or record.get("sha256") != existing_hash:
                raise ValueError(f"Refusing to overwrite an untracked or modified texture: {target}")
            expected_existing[name] = existing_hash
        else:
            expected_existing[name] = None
    expected_existing["source.json"] = file_digest(manifest_target) if manifest_target.exists() else None
    payloads = dict(images)
    payloads["source.json"] = (json.dumps(provenance, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    targets["source.json"] = manifest_target
    root.mkdir(parents=True, exist_ok=True)
    (root / "textures").mkdir(exist_ok=True)
    # Stage complete files on the destination filesystem. Default commits use
    # exclusive creation; explicit overwrites still require an unchanged manifest/hash.
    with tempfile.TemporaryDirectory(prefix=".armor-extract-", dir=root / "textures") as staging_dir:
        staging = Path(staging_dir)
        for name, data in payloads.items():
            (staging / name).write_bytes(data)
        for name, target in targets.items():
            checked_target(root, name)
            current = file_digest(target) if target.exists() else None
            if current != expected_existing[name]:
                raise ValueError(f"Destination changed during extraction; no replacement made: {target}")
        for name, target in targets.items():
            if expected_existing[name] is None:
                # Hard links preserve exclusive creation even if another process
                # creates the target after preflight. No ZIP path is ever extracted.
                os.link(staging / name, target)
            else:
                os.replace(staging / name, target)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-jar", required=True, type=Path, help="User-owned Minecraft Java 1.20.1 client.jar")
    parser.add_argument("--output", required=True, type=Path, help="Workbench folder; writes only its textures/ subfolder")
    parser.add_argument("--overwrite", action="store_true", help="Replace an earlier tracked extraction; modified/untracked files are rejected")
    args = parser.parse_args()
    try:
        source = args.client_jar.expanduser().resolve(strict=True)
        if not source.is_file():
            raise ValueError("--client-jar must name a regular file")
        root = args.output.expanduser().resolve()
        if root.exists() and not root.is_dir():
            raise ValueError("--output must be a directory")
        images, provenance = read_source(source)
        write_outputs(root, images, provenance, args.overwrite)
    except (OSError, ValueError, BadZipFile, zlib.error, RuntimeError) as exc:
        parser.exit(2, f"error: {exc}\n")
    print(f"Extracted {len(images)} verified armor PNGs to {root / 'textures'}")
    print("Local preview assets only; do not add these Minecraft textures to the distributed skill.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
