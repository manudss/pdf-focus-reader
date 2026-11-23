#!/usr/bin/env python3
"""Static 16 KB page-size readiness check for native libraries in built outputs.

Google Play requires 16 KB page-size compatibility for native code. A shared
library is compatible when every ELF LOAD segment is aligned to at least 16384
bytes. This script inspects every .so inside the given APKs (zip archives),
and fails if any LOAD segment has p_align below 16384.

Static check only: it does not replace a runtime test on a 16 KB device image.

Usage: check-16kb-alignment.py APK [APK ...]
"""
import io, struct, sys, zipfile

REQUIRED = 16384

def load_alignments(data, name):
    """Return the p_align of every PT_LOAD segment of a 64-bit ELF."""
    if data[:4] != b'\x7fELF':
        raise ValueError(f'{name}: not an ELF file')
    if data[4] != 2:
        raise ValueError(f'{name}: 32-bit ELF, expected 64-bit')
    e_phoff = struct.unpack_from('<Q', data, 32)[0]
    e_phentsize = struct.unpack_from('<H', data, 54)[0]
    e_phnum = struct.unpack_from('<H', data, 56)[0]
    aligns = []
    for i in range(e_phnum):
        p_type = struct.unpack_from('<I', data, e_phoff + i * e_phentsize)[0]
        if p_type == 1:  # PT_LOAD
            aligns.append(struct.unpack_from('<Q', data, e_phoff + i * e_phentsize + 48)[0])
    if not aligns:
        raise ValueError(f'{name}: no LOAD segment')
    return aligns

def check_apk(path):
    failures = []
    with zipfile.ZipFile(path) as zf:
        libs = [n for n in zf.namelist() if n.endswith('.so')]
        if not libs:
            print(f'{path}: no native library')
        for entry in libs:
            aligns = load_alignments(zf.read(entry), entry)
            worst = min(aligns)
            status = 'OK  ' if worst >= REQUIRED else 'FAIL'
            print(f'{status} {path} :: {entry} (min LOAD p_align={worst})')
            if worst < REQUIRED:
                failures.append(entry)
    return failures

def main():
    if len(sys.argv) < 2:
        raise SystemExit('Usage: check-16kb-alignment.py APK [APK ...]')
    failures = []
    for path in sys.argv[1:]:
        failures.extend(check_apk(path))
    if failures:
        raise SystemExit(f'{len(failures)} native librar(ies) below {REQUIRED}-byte LOAD alignment')
    print(f'All native libraries align to at least {REQUIRED} bytes.')

if __name__ == '__main__':
    main()
