import re
import tempfile
from pathlib import Path
import unittest

def scan_for_test_devices(dir_path):
    found = []
    hex32_pattern = re.compile(r'setTestDeviceIds\s*\([^)]*["\'][0-9A-F]{32}["\']', re.DOTALL)
    for java_file in Path(dir_path).rglob('*.java'):
        content = java_file.read_text(encoding='utf-8')
        if 'F96C3A5E789445DD5896009229E43316' in content:
            found.append(f'{java_file.name}: hardcoded test device ID literal F96C3A5E789445DD5896009229E43316')
        if hex32_pattern.search(content):
            found.append(f'{java_file.name}: hardcoded 32-character hex test device ID near setTestDeviceIds')
    return found

class AdTestDevicesContractTest(unittest.TestCase):
    def test_repo_main_sources_contain_no_hardcoded_test_device_ids(self):
        root = Path(__file__).resolve().parents[2]
        app_main = root / 'app/src/main/java'
        ads_main = root / 'ads/src/main/java'
        self.assertEqual([], scan_for_test_devices(app_main))
        self.assertEqual([], scan_for_test_devices(ads_main))

    def test_rejects_exact_literal(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            file_path = Path(tmpdir) / 'Bad.java'
            file_path.write_text('String testId = "F96C3A5E789445DD5896009229E43316";', encoding='utf-8')
            problems = scan_for_test_devices(tmpdir)
            self.assertTrue(any('F96C3A5E789445DD5896009229E43316' in p for p in problems))

    def test_rejects_32_char_hex_near_setTestDeviceIds(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            file_path = Path(tmpdir) / 'BadConfig.java'
            file_path.write_text(
                'builder.setTestDeviceIds(Arrays.asList("11223344556677889900AABBCCDDEEFF"));',
                encoding='utf-8'
            )
            problems = scan_for_test_devices(tmpdir)
            self.assertTrue(any('32-character hex' in p for p in problems))

    def test_accepts_clean_debug_config(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            file_path = Path(tmpdir) / 'Clean.java'
            file_path.write_text(
                'builder.setTestDeviceIds(AdTestDevices.forBuild(BuildConfig.DEBUG, BuildConfig.TEST_DEVICE_IDS));',
                encoding='utf-8'
            )
            self.assertEqual([], scan_for_test_devices(tmpdir))

if __name__ == '__main__':
    unittest.main()
