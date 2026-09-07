import struct
import sys
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_native_alignment import elf_errors, inspect_apk, PAGE


def elf(alignments=(PAGE,), machine=183, address=0, kind=1, oversized=False):
    length = 64 + 56 * len(alignments)
    ident = b'\x7fELF\x02\x01\x01' + b'\x00' * 9
    header = struct.pack('<16sHHIQQQIHHHHHH', ident, 3, machine, 1, 0, 64, 0, 0, 64, 56, len(alignments), 0, 0, 0)
    extent = length + 1 if oversized else length
    return header + b''.join(struct.pack('<IIQQQQQQ', kind, 5, 0, address, 0, extent, extent, alignment) for alignment in alignments)


class NativeAlignmentTest(unittest.TestCase):
    def apk(self, entries, compression=zipfile.ZIP_STORED):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'test.apk'
            with warnings.catch_warnings():
                warnings.simplefilter('ignore', UserWarning)
                with zipfile.ZipFile(path, 'w', compression=compression) as archive:
                    for name, data in entries:
                        archive.writestr(name, data)
            return inspect_apk(path)

    def test_accepts_16k_load_alignment(self):
        self.assertEqual([], elf_errors(elf(), 183))

    def test_accepts_larger_power_of_two(self):
        self.assertEqual([], elf_errors(elf((65536,)), 183))

    def test_rejects_4k_and_8k(self):
        for alignment in (4096, 8192):
            with self.subTest(alignment=alignment):
                self.assertTrue(elf_errors(elf((alignment,)), 183))

    def test_checks_every_load_segment(self):
        errors = elf_errors(elf((PAGE, PAGE, 4096)), 183)
        self.assertEqual(1, len(errors)); self.assertIn('LOAD[2]', errors[0])

    def test_rejects_incongruent_file_virtual_offsets(self):
        self.assertIn('congruent', '\n'.join(elf_errors(elf(address=4096), 183)))

    def test_rejects_non_power_of_two_alignment(self):
        self.assertTrue(elf_errors(elf((24576,)), 183))

    def test_rejects_truncated_header(self):
        self.assertTrue(elf_errors(elf()[:20], 183))

    def test_rejects_truncated_program_headers(self):
        self.assertTrue(elf_errors(elf()[:-1], 183))

    def test_rejects_invalid_load_extent(self):
        self.assertIn('extent', '\n'.join(elf_errors(elf(oversized=True), 183)))

    def test_rejects_wrong_packaged_architecture(self):
        self.assertTrue(elf_errors(elf(), 62))

    def test_rejects_elf32_disguised_as_64_bit_library(self):
        data = bytearray(elf()); data[4] = 1
        self.assertTrue(elf_errors(data, 183))

    def test_requires_a_load_segment(self):
        self.assertIn('No ELF LOAD', '\n'.join(elf_errors(elf(kind=4), 183)))

    def test_apk_with_no_native_code_is_reported_explicitly(self):
        result = self.apk([('classes.dex', b'fixture')])
        self.assertEqual([], result['errors']); self.assertEqual([], result['checked_libraries'])

    def test_valid_arm64_and_x86_64_libraries(self):
        result = self.apk([('lib/arm64-v8a/liba.so', elf()), ('lib/x86_64/liba.so', elf(machine=62))])
        self.assertEqual([], result['errors']); self.assertEqual(2, len(result['checked_libraries']))

    def test_compression_cannot_hide_bad_elf_alignment(self):
        result = self.apk([('lib/arm64-v8a/libbad.so', elf((4096,)))], zipfile.ZIP_DEFLATED)
        self.assertTrue(result['errors']); self.assertIn('libbad.so', result['errors'][0])

    def test_duplicate_native_entries_are_rejected(self):
        result = self.apk([('lib/arm64-v8a/liba.so', elf()), ('lib/arm64-v8a/liba.so', elf())])
        self.assertTrue(any('duplicate' in error for error in result['errors']))

    def test_invalid_archive_is_a_failure_not_a_false_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bad.apk'; path.write_text('not a ZIP archive')
            self.assertTrue(inspect_apk(path)['errors'])

if __name__ == '__main__': unittest.main()
