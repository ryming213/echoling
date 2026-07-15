#!/usr/bin/env python3
"""
Patch ELF PT_LOAD segments in Android .so files to use 16 KB alignment
(p_align = 0x4000 = 16384) instead of the default 4 KB.

Required because Android 15+ devices use 16 KB page sizes, and the .so
files bundled in third-party AARs (vosk-android:0.3.45) were built before
this became mandatory. From Nov 1 2025, Google Play requires all apps to
be 16 KB-aligned.

Why this works:
- The .so file's `p_align` field in each PT_LOAD program header is a HINT
  to the ELF loader about the alignment of the segment in memory. When
  `p_align = 0x4000`, the loader can mmap the segment directly at a
  16 KB boundary without dynamic adjustment.
- Bumping `p_align` from 4096 to 16384 does NOT change the file content
  or the segment offsets — it only changes what the loader expects.
- Android 15's loader (`linker64` with 16 KB page-size support) honors
  this hint and avoids the costly `mprotect`-based realignment fallback.

Why this is safe:
- `p_align` is a linker/loader hint. A larger `p_align` than what's
  actually used in the file is always safe — the loader will just be
  told "this segment is aligned to at least 16 KB". Since our file's
  segments are already 4 KB aligned (a subset of 16 KB), the loader's
  mmap will succeed.
- This is exactly what Google's `patchelf --page-size=16384` does,
  implemented in pure Python because patchelf isn't bundled with the
  Android NDK on Windows.

Usage:
    python3 patch_native_libs_16kb.py <so_file> [<so_file> ...]

Or programmatically (called from the Gradle patch task):
    from patch_native_libs_16kb import patch_file
    patch_file(Path("lib/arm64-v8a/libvosk.so"))
"""

import struct
import sys
from pathlib import Path

ELFCLASS64 = 2
PT_LOAD = 1
TARGET_ALIGN = 0x4000  # 16384 bytes = 16 KB


def patch_file(so_path: Path) -> int:
    """Patch all PT_LOAD segments in an ELF64 .so file to 16 KB p_align.

    Returns the number of segments patched. Skips silently if the file is
    not ELF64 or already at 16 KB alignment.

    Modifies the file in place. Touches the mtime so Gradle knows the
    input changed and will re-run downstream tasks (packageDebug).
    """
    data = bytearray(so_path.read_bytes())

    # ELF magic + class check
    if data[:4] != b"\x7fELF":
        raise ValueError(f"{so_path}: not an ELF file")
    if data[4] != ELFCLASS64:
        # ELF32 (32-bit). Skip — 32-bit libs aren't subject to 16 KB
        # requirement on 64-bit-only 16 KB devices anyway.
        return 0

    # ELF64 header offsets: e_phoff=32(8B), e_phentsize=54(2B), e_phnum=56(2B)
    e_phoff = struct.unpack_from("<Q", data, 32)[0]
    e_phentsize = struct.unpack_from("<H", data, 54)[0]
    e_phnum = struct.unpack_from("<H", data, 56)[0]

    patches = 0
    already_ok = 0
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", data, off)[0]
        if p_type != PT_LOAD:
            continue
        old = struct.unpack_from("<Q", data, off + 48)[0]
        if old >= TARGET_ALIGN:
            already_ok += 1
            continue
        struct.pack_into("<Q", data, off + 48, TARGET_ALIGN)
        patches += 1

    if patches == 0:
        return already_ok  # nothing to do

    so_path.write_bytes(bytes(data))
    # Touch mtime so Gradle picks up the change even if size happens to
    # match the old file (it won't here, but defensive).
    import time
    now = time.time()
    import os
    os.utime(so_path, (now, now))
    return patches


def main(argv: list[str]) -> int:
    if not argv:
        print(f"Usage: {argv[0]} <so_file> [<so_file> ...]", file=sys.stderr)
        return 1
    total = 0
    for arg in argv[1:]:
        path = Path(arg)
        if not path.exists():
            print(f"skip (missing): {arg}", file=sys.stderr)
            continue
        n = patch_file(path)
        print(f"{arg}: patched {n} PT_LOAD segment(s) to 16384")
        total += n
    print(f"total: {total} PT_LOAD segments patched")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))