#!/usr/bin/env python3
"""Check packaged 64-bit ELF LOAD alignment; run SDK zipalign separately for APK ZIP alignment.

References: https://developer.android.com/guide/practices/page-sizes
            https://developer.android.com/tools/zipalign
This is a binary compatibility gate, not a substitute for running on a 16 KB device.
"""
import argparse
import json
from pathlib import Path
import struct
import zipfile

PAGE = 16384
MACHINES = {'arm64-v8a': 183, 'x86_64': 62}
MAX_LIBRARY = 128 * 1024 * 1024


def elf_errors(data, machine):
    problems = []
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
        return ['Not a complete little-endian ELF64 header']
    header = struct.unpack_from('<16sHHIQQQIHHHHHH', data)
    if header[1] != 3 or header[2] != machine or header[3] != 1:
        return ['Wrong ELF type, architecture or version for the packaged ABI']
    offset, size, count = header[5], header[9], header[10]
    if header[8] != 64 or size < 56 or count == 0 or offset < 64 or offset + size * count > len(data):
        return ['Missing or truncated ELF program headers']
    loads = 0
    for index in range(count):
        kind, flags, position, address, physical, file_size, memory_size, alignment = struct.unpack_from('<IIQQQQQQ', data, offset + index * size)
        if kind != 1:
            continue
        loads += 1
        if alignment < PAGE or alignment & (alignment - 1):
            problems.append(f'LOAD[{index}] alignment {alignment} is not a power-of-two >= {PAGE}')
        if (address - position) % PAGE:
            problems.append(f'LOAD[{index}] file/virtual offsets are not congruent modulo {PAGE}')
        if file_size > memory_size or (file_size and position + file_size > len(data)):
            problems.append(f'LOAD[{index}] has an invalid file extent')
    if not loads:
        problems.append('No ELF LOAD segments found')
    return problems


def inspect_apk(path):
    result = {'apk': str(path), 'checked_libraries': [], 'errors': []}
    try:
        with zipfile.ZipFile(path) as apk:
            seen = set()
            for member in apk.infolist():
                parts = member.filename.split('/')
                if len(parts) != 3 or parts[0] != 'lib' or parts[1] not in MACHINES or not parts[2].endswith('.so'):
                    continue
                if member.filename in seen:
                    result['errors'].append(member.filename + ': duplicate native entry')
                    continue
                seen.add(member.filename)
                result['checked_libraries'].append(member.filename)
                if member.file_size > MAX_LIBRARY:
                    result['errors'].append(member.filename + ': exceeds the 128 MiB inspection limit; inspect explicitly')
                    continue
                for problem in elf_errors(apk.read(member), MACHINES[parts[1]]):
                    result['errors'].append(member.filename + ': ' + problem)
    except (OSError, ValueError, struct.error, zipfile.BadZipFile, RuntimeError) as error:
        result['errors'].append('Unable to inspect APK: ' + str(error))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', nargs='+', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    results = [inspect_apk(path) for path in args.apks]
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(results, indent=2) + '\n')
    for result in results:
        print(f"{result['apk']}: inspected {len(result['checked_libraries'])} 64-bit native libraries")
        for problem in result['errors']:
            print('error: native alignment: ' + problem)
    return int(any(result['errors'] for result in results))

if __name__ == '__main__':
    raise SystemExit(main())
