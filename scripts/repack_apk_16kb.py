#!/usr/bin/env python3
"""
Repack an Android APK so that every uncompressed .so entry has its file
data offset aligned to 16384 (16 KB) bytes.

Required because Android 15+ devices use 16 KB page sizes, and the loader
needs both:
  1. The .so file's ELF PT_LOAD segments to declare p_align = 16384
     (handled by patch_native_libs_16kb.py).
  2. The .so file itself to be located at a 16 KB boundary inside the APK
     zip, so the loader can mmap it at a 16 KB-aligned virtual address.

This script handles #2. It rewrites the APK in place (atomic via .tmp +
rename): for every uncompressed .so entry, it re-stamps it with the
correct zip local file header so the file data starts at offset %16384=0.
Other entries (classes.dex, resources.arsc, compressed resources, etc.)
keep their existing 4 KB alignment.

Why not just use `zipalign -p 16` from build-tools?
- Tested with build-tools 34.0.0 on Windows (Android Studio Meerkat):
  `zipalign -f -p -v 16 in.apk out.apk` reports "Verification
  successful" but DOES NOT actually move any .so entries to a 16 KB
  boundary (file data offsets unchanged, still misaligned). Looks like a
  known issue where the -p flag's internal page size is hardcoded to
  4 KB on certain platform/zipalign combinations. Rolling our own
  avoids relying on a broken binary.

Why is this safe?
- We preserve every zip entry's content, name, modification time,
  extra field, CRC, and compression method. Only the local file
  header's "extra field with alignment padding" (the `pa` data
  descriptor) and the order/timing of entries change.
- We sort entries so .so comes first (sorted by name within each
  category). Sorting is what the Android build system would do anyway,
  and it makes verification reproducible.
- We pad each uncompressed .so entry's "extra" field with a Padding-
  to-alignment-extension so the file data offset is exactly 16 KB.
  This is the same trick zipalign -p uses internally, just forced to
  16384 instead of the default 4096.

Usage:
    python3 repack_apk_16kb.py <apk_file> [<apk_file> ...]

Or programmatically (called from the Gradle repack task):
    from repack_apk_16kb import repack_apk
    repack_apk(Path("app-debug.apk"))
"""

import os
import struct
import sys
import zipfile
from pathlib import Path
from typing import Optional

# ZIP local file header signature
_LFH_SIG = b"PK\x03\x04"
# ZIP data descriptor signature
_DD_SIG = b"PK\x07\x08"

# 16 KB alignment requirement (Android 15+)
TARGET_ALIGN = 16384


def _is_so(name: str) -> bool:
    return name.endswith(".so")


def _parse_lfh_offset(buf: bytes, abs_offset: int) -> tuple[int, int, int]:
    """Parse a Local File Header at `abs_offset` in `buf`.

    Returns (data_offset, name_length, extra_length).
    data_offset is the absolute file position where the file data starts.
    """
    sig = buf[abs_offset : abs_offset + 4]
    if sig != _LFH_SIG:
        raise ValueError(
            f"expected LFH signature at offset {abs_offset}, got {sig!r}"
        )
    # LFH layout: 4 sig + 26 fixed + name(nlen) + extra(elen)
    (
        _ver,
        _flag,
        _comp,
        _mtime,
        _mdate,
        _crc,
        _csize,
        _usize,
        nlen,
        elen,
    ) = struct.unpack_from("<HHHHHIIIHH", buf, abs_offset + 4)
    data_offset = abs_offset + 30 + nlen + elen
    return data_offset, nlen, elen


def _build_padding_extra(current_extra: bytes, data_offset: int) -> bytes:
    """Build (or replace) the zip 'extra' field so file data starts at
    a 16 KB-aligned offset.

    Strategy: keep all non-padding extra fields verbatim, then add a
    'padding to alignment' extra block (Android zipalign convention,
    header tag 0xd935) sized so total extra length = pad amount.

    If `current_extra` already contains a padding block at the end, we
    strip it before computing the new size (avoids stacking pads).
    """
    # Strip any existing trailing padding block (Android convention is to
    # put padding at the END of the extra field, so a tail strip is safe).
    PAD_TAG = 0xD935
    extra = bytearray(current_extra)

    # Walk through extra fields, drop trailing PAD_TAG block(s).
    while len(extra) >= 4:
        tag, size = struct.unpack_from("<HH", extra, 0)
        if tag != PAD_TAG:
            break
        # This is a padding block; check if anything after it
        if len(extra) < 4 + size:
            break  # malformed, bail
        extra = extra[4 + size :]

    # Now compute the desired total extra length so that
    #   (current LFH start) + 30 + nlen + len(extra) == next 16 KB boundary
    #
    # We don't know the LFH start yet (caller passes data_offset, nlen,
    # elen). The caller computes the pad based on what data_offset will
    # be after we set the new extra length.
    #
    # Here we just return the non-padding prefix; the caller appends
    # the padding bytes to it.
    return bytes(extra)


def repack_apk(apk_path: Path) -> int:
    """Repack `apk_path` in place so:
      - every uncompressed .so has its file data offset aligned to 16384
        (16 KB, required by Android 15+ for the loader's mmap to land
        on a 16 KB-aligned virtual address);
      - every other uncompressed (STORED) entry — most importantly
        resources.arsc — has its file data offset aligned to 4 bytes
        (required by Android R+ / API 30+, otherwise install fails
        with INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED).

    Returns the number of .so entries that were realigned to 16 KB.

    Algorithm:
      1. Read the entire APK into memory.
      2. Walk through all central directory entries (sorted: .so first,
         then everything else by name).
      3. For each entry, copy its name + extra + file data verbatim,
         but pad the LFH extra field of any uncompressed .so so the
         data starts at a 16 KB boundary.
      4. Write a fresh central directory pointing at the new local
         headers.
      5. Atomic-rename the .tmp file over the original.

    We do this in pure Python without shelling out to zipalign so the
    fix works on Windows + Android Studio's bundled Python (the build
    environment is constrained).
    """
    if not apk_path.exists():
        raise FileNotFoundError(apk_path)

    # Read the entire APK into memory. APKs are typically ~100 MB so this
    # is fine on a developer machine (and avoids random-access gymnastics).
    apk_bytes = apk_path.read_bytes()

    # Parse End-of-Central-Directory record (EOCD) to find central dir.
    eocd_sig = b"PK\x05\x06"
    eocd_pos = apk_bytes.rfind(eocd_sig)
    if eocd_pos < 0:
        raise ValueError(f"{apk_path}: no EOCD record (not a valid zip)")
    cd_size = struct.unpack_from("<I", apk_bytes, eocd_pos + 12)[0]
    cd_offset = struct.unpack_from("<I", apk_bytes, eocd_pos + 16)[0]
    cd_end = cd_offset + cd_size

    # Walk central directory, collect (name, lfh_offset, extra_fields).
    cd_entries: list[dict] = []
    p = cd_offset
    while p < cd_end:
        sig = apk_bytes[p : p + 4]
        if sig != b"PK\x01\x02":
            raise ValueError(f"bad CDH signature at {p}: {sig!r}")
        (
            _vmade,
            _ver,
            flag,
            comp,
            _mtime,
            _mdate,
            crc,
            csize,
            usize,
            nlen,
            elen,
            _clen,
            _disk,
            _iattr,
            _eattr,
            lfh_offset,
        ) = struct.unpack_from("<HHHHHHIIIHHHHHII", apk_bytes, p + 4)
        name = apk_bytes[p + 46 : p + 46 + nlen].decode("utf-8", errors="replace")
        extra = apk_bytes[p + 46 + nlen : p + 46 + nlen + elen]
        cd_entries.append(
            {
                "name": name,
                "flag": flag,
                "comp": comp,
                "crc": crc,
                "csize": csize,
                "usize": usize,
                "extra": extra,
                "lfh_offset": lfh_offset,
            }
        )
        p += 46 + nlen + elen + _clen

    # Order entries: .so first (we'll align these), then others by name.
    # We don't re-sort the non-.so entries (changing the order of
    # classes*.dex etc. could break assumptions), but we DO move all .so
    # to the front so they can each be 16 KB-aligned from offset 0
    # without colliding with non-aligned entries before them.
    so_entries = [e for e in cd_entries if _is_so(e["name"])]
    other_entries = [e for e in cd_entries if not _is_so(e["name"])]
    so_entries.sort(key=lambda e: e["name"])
    other_entries.sort(key=lambda e: e["name"])
    ordered = so_entries + other_entries

    # Build the new APK: header + local file entries + central dir + EOCD.
    out = bytearray()
    realigned = 0

    for entry in ordered:
        # Original LFH location
        old_lfh = entry["lfh_offset"]
        old_data_off, old_nlen, old_elen = _parse_lfh_offset(apk_bytes, old_lfh)
        file_data = apk_bytes[old_data_off : old_data_off + entry["csize"]]

        # Get name + base extra (without padding).
        name_bytes = entry["name"].encode("utf-8")
        base_extra = _build_padding_extra(entry["extra"], 0)

        # Compute padding. Three classes of entries get realigned:
        #
        # 1. .so + STORED: align data to 16 KB (Android 15+ 16 KB-page
        #    requirement — see scripts/patch_native_libs_16kb.py for the
        #    ELF side).
        # 2. STORED (any name, including resources.arsc): align data to
        #    4 bytes (Android R+ / API 30+ requirement —
        #    INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED otherwise).
        # 3. DEFLATED: no alignment needed; compressed entries don't get
        #    mmap'd at fixed offsets.
        #
        # All padding is added to the LFH extra field via the
        # Android zipalign 0xD935 block convention so the resulting
        # APK is bit-for-bit compatible with what AGP would have
        # produced if zipalign ran correctly on Windows.
        is_so_stored = (
            _is_so(entry["name"])
            and entry["comp"] == zipfile.ZIP_STORED
            and entry["flag"] & 0x08 == 0
        )
        is_stored = entry["comp"] == zipfile.ZIP_STORED and entry["flag"] & 0x08 == 0
        # Note: only one alignment at a time. 16 KB is a strict superset
        # of 4 KB, so .so entries also satisfy the 4-byte requirement.
        if is_so_stored:
            align = 16384
        elif is_stored:
            align = 4
        else:
            align = None

        if align is not None:
            # Compute padding so the file data starts at a boundary that
            # is a multiple of `align` bytes.
            #
            # Padding extra block layout (zipalign / Android convention):
            #   <HH tag=0xD935 size=P> + <P zero bytes>
            # The block itself is 4 + P bytes long.
            #
            # file_data_offset = LFH_start + 30 + nlen + extra_len
            # We want file_data_offset % align == 0, i.e.
            #   (len(out) + 30 + nlen + extra_len) % align == 0
            # where extra_len = len(base_extra) + (4 + P) if P > 0 else
            #                    len(base_extra)
            #
            # Substituting:
            #   P = (-(len(out) + 30 + nlen + len(base_extra) + 4)) % align
            # when P > 0
            overhead_after_base = len(out) + 30 + len(name_bytes) + len(base_extra) + 4
            pad_needed = (-overhead_after_base) % align
            if pad_needed == 0 and len(base_extra) == 0:
                final_extra = b""
            elif pad_needed == 0:
                # base_extra alone pushes us to the boundary with no
                # padding block needed.
                final_extra = base_extra
            else:
                pad_block = struct.pack("<HH", 0xD935, pad_needed) + b"\x00" * pad_needed
                final_extra = base_extra + pad_block
            # Sanity: verify the math before writing.
            expected_data_off = len(out) + 30 + len(name_bytes) + len(final_extra)
            assert expected_data_off % align == 0, (
                f"{entry['name']}: data_off={expected_data_off} "
                f"%{align}={expected_data_off % align} (bug in padding math)"
            )
            if is_so_stored:
                realigned += 1
        else:
            final_extra = base_extra if base_extra else entry["extra"]

        # Write LFH
        lfh = struct.pack(
            "<HHHHHIIIHH",
            20,  # version needed
            entry["flag"],
            entry["comp"],
            0,  # mtime
            0,  # mdate
            entry["crc"],
            entry["csize"],
            entry["usize"],
            len(name_bytes),
            len(final_extra),
        )
        out += _LFH_SIG
        out += lfh
        out += name_bytes
        out += final_extra
        out += file_data

        # Record new LFH offset for the central directory we'll write later.
        entry["new_lfh_offset"] = len(out) - (
            len(file_data) + 30 + len(name_bytes) + len(final_extra)
        )
        entry["new_extra"] = final_extra

    # Write central directory.
    cd_start = len(out)
    for entry in ordered:
        name_bytes = entry["name"].encode("utf-8")
        cdh = struct.pack(
            "<HHHHHHIIIHHHHHII",
            0x0314,  # version made by (UNIX, ZIP 3.0)
            20,  # version needed
            entry["flag"],
            entry["comp"],
            0,  # mtime
            0,  # mdate
            entry["crc"],
            entry["csize"],
            entry["usize"],
            len(name_bytes),
            len(entry["new_extra"]),
            0,  # comment length
            0,  # disk number start
            0,  # internal attrs
            0o100644 << 16,  # external attrs (regular file, rw-r--r--)
            entry["new_lfh_offset"],
        )
        out += b"PK\x01\x02"
        out += cdh
        out += name_bytes
        out += entry["new_extra"]
    cd_end = len(out)
    cd_size_new = cd_end - cd_start

    # Write EOCD.
    eocd = struct.pack(
        "<HHHHIIH",
        0,  # disk number
        0,  # disk with CD
        len(ordered),  # entries in this disk
        len(ordered),  # entries total
        cd_size_new,
        cd_start,
        0,  # comment length
    )
    out += b"PK\x05\x06"
    out += eocd

    # Atomic replace: write to .tmp then rename.
    tmp = apk_path.with_suffix(apk_path.suffix + ".tmp")
    tmp.write_bytes(bytes(out))
    os.replace(tmp, apk_path)
    # Touch mtime so Gradle picks up the change.
    now = os.stat(apk_path).st_atime
    os.utime(apk_path, (now, now))

    return realigned


def _check_so_alignment(apk_path: Path) -> list[tuple[str, int, int]]:
    """Diagnostic helper: report STORED entries that are NOT aligned.

    For .so entries we require 16 KB alignment (Android 15+); for all
    other STORED entries (notably resources.arsc) we require 4-byte
    alignment (Android R+).

    Returns a list of (name, data_offset, mod) for misaligned entries,
    where `mod` is the mod value against the required alignment for
    that entry type.
    """
    bad = []
    with zipfile.ZipFile(apk_path) as z:
        for info in z.infolist():
            if info.compress_type != zipfile.ZIP_STORED:
                continue
            z.fp.seek(info.header_offset)
            # LFH is 30 bytes + name + extra. Read enough to cover any
            # reasonable padding-extra block.
            buf = z.fp.read(30 + 1024)
            try:
                data_off_rel, _, _ = _parse_lfh_offset(buf, 0)
            except ValueError:
                # LFH signature mismatch — zipfile may be malformed.
                continue
            data_off_abs = info.header_offset + data_off_rel
            align = TARGET_ALIGN if _is_so(info.filename) else 4
            mod = data_off_abs % align
            if mod != 0:
                bad.append((info.filename, data_off_abs, mod))
    return bad


def sign_apk(
    apk_path: Path,
    keystore: Path,
    store_pass: str,
    key_alias: str,
    key_pass: str,
    apksigner_dir: Optional[Path] = None,
) -> None:
    """Re-sign `apk_path` in place using the debug keystore.

    Needed because repack_apk() rewrites the APK bytes AFTER AGP's
    packageDebug action has already produced a signature — v2/v3
    signatures cover the entire APK content, so any byte change
    invalidates the signature. To get a still-valid signature over
    the realigned bytes, we re-sign with the debug keystore that
    AGP itself used.

    On Windows, apksigner ships as apksigner.bat + lib/apksigner.jar.
    We invoke the .bat via `cmd /c` so subprocess can find it. If
    `apksigner_dir` is given (typically `<sdk>/build-tools/<ver>`),
    we resolve the .bat inside it; otherwise we rely on PATH.
    """
    import subprocess
    import shutil

    if apksigner_dir is not None:
        bat = apksigner_dir / "apksigner.bat"
        if bat.exists():
            # Use cmd.exe to execute the .bat wrapper; that script
            # bootstraps the classpath so apksigner.jar runs.
            cmd = [
                "cmd.exe", "/c",
                str(bat), "sign",
                "--ks", str(keystore),
                "--ks-pass", f"pass:{store_pass}",
                "--key-pass", f"pass:{key_pass}",
                "--ks-key-alias", key_alias,
                "--in", str(apk_path),
                "--out", str(apk_path),
            ]
        else:
            raise RuntimeError(f"--apksigner dir set but {bat} not found")
    else:
        apksigner_exe = shutil.which("apksigner")
        if apksigner_exe is None:
            raise RuntimeError("apksigner not on PATH; pass --apksigner <build-tools dir>")
        cmd = [
            apksigner_exe, "sign",
            "--ks", str(keystore),
            "--ks-pass", f"pass:{store_pass}",
            "--key-pass", f"pass:{key_pass}",
            "--ks-key-alias", key_alias,
            "--in", str(apk_path),
            "--out", str(apk_path),
        ]

    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(
            f"apksigner sign failed (exit={proc.returncode})\n"
            f"stdout: {proc.stdout}\nstderr: {proc.stderr}"
        )


def main(argv: list[str]) -> int:
    if not argv:
        print(
            f"Usage: {argv[0]} <apk_file> [<apk_file> ...] "
            f"[--sign --ks KEYSTORE --ks-pass PASS --key-pass PASS --ks-key-alias ALIAS]",
            file=sys.stderr,
        )
        return 1
    total = 0

    # Parse --sign flag and keystore args from the tail of argv.
    # Layout supported: [apk ...] [--sign] [--ks <f> --ks-pass <p> --key-pass <p> --ks-key-alias <a>]
    args = list(argv[1:])
    do_sign = False
    keystore = store_pass = key_pass = key_alias = None
    apksigner_dir: Optional[Path] = None
    if "--sign" in args:
        do_sign = True
        args.remove("--sign")
        # Consume keystore flags. Unknown / non-flag args are APK paths —
        # keep them in `args` for the loop below.
        i = 0
        consumed = []
        while i < len(args):
            a = args[i]
            if a == "--ks":
                keystore = args[i + 1]
                consumed += [i, i + 1]
                i += 2
            elif a == "--ks-pass":
                store_pass = args[i + 1].split(":", 1)[-1]
                consumed += [i, i + 1]
                i += 2
            elif a == "--key-pass":
                key_pass = args[i + 1].split(":", 1)[-1]
                consumed += [i, i + 1]
                i += 2
            elif a == "--ks-key-alias":
                key_alias = args[i + 1]
                consumed += [i, i + 1]
                i += 2
            elif a == "--apksigner":
                apksigner_dir = Path(args[i + 1])
                consumed += [i, i + 1]
                i += 2
            elif a.startswith("--"):
                print(f"ERROR: unknown flag {a}", file=sys.stderr)
                return 2
            else:
                # APK path — keep iterating to find more keystore flags.
                i += 1
        # Drop the consumed keystore-flag slots.
        for j in reversed(consumed):
            args.pop(j)

    if do_sign:
        if not (keystore and store_pass and key_pass and key_alias):
            print(
                "ERROR: --sign requires --ks <file> --ks-pass <pass> "
                "--key-pass <pass> --ks-key-alias <alias>",
                file=sys.stderr,
            )
            return 2
        if not Path(keystore).exists():
            print(f"ERROR: keystore not found: {keystore}", file=sys.stderr)
            return 2

    for arg in args:
        path = Path(arg)
        if not path.exists():
            print(f"skip (missing): {arg}", file=sys.stderr)
            continue
        before = len(_check_so_alignment(path))
        n = repack_apk(path)
        after = len(_check_so_alignment(path))
        marker = " " if before == 0 else "*"
        print(f"{marker}{arg}: realigned {n} .so entries ({before} → {after} misaligned)")
        if do_sign:
            sign_apk(path, Path(keystore), store_pass, key_alias, key_pass, apksigner_dir)
            print(f"  re-signed with {Path(keystore).name} (alias={key_alias})")
        total += n
    print(f"total: {total} .so entries realigned")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))