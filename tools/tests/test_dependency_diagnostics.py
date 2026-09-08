import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check_dependency_policy.py'


class DependencyDiagnosticsTest(unittest.TestCase):
    def test_every_failure_line_is_visible_to_ci_reporter(self):
        data = {'schemaVersion': 1, 'configurations': ['debugRuntimeClasspath', 'releaseRuntimeClasspath'],
                'modules': [{'configuration': 'debugRuntimeClasspath', 'group': 'io.reactivex.rxjava2',
                             'name': 'rxjava', 'version': '2.2.21'}]}
        with tempfile.TemporaryDirectory() as folder:
            inventory = Path(folder) / 'inventory.json'
            inventory.write_text(json.dumps(data))
            result = subprocess.run([sys.executable, str(SCRIPT), str(inventory)],
                                    text=True, capture_output=True, timeout=10)
        self.assertEqual(1, result.returncode)
        lines = result.stderr.splitlines()
        self.assertGreater(len(lines), 2)
        self.assertTrue(all(line.startswith('Error: ') for line in lines))
        self.assertIn('removed legacy dependency returned: io.reactivex.rxjava2:rxjava', result.stderr)
        self.assertIn('releaseRuntimeClasspath: missing required SDK:', result.stderr)

    def test_success_reports_exact_retained_vendor_previews(self):
        from test_dependency_policy import ads_preview_inventory
        with tempfile.TemporaryDirectory() as folder:
            inventory = Path(folder) / 'inventory.json'
            inventory.write_text(json.dumps(ads_preview_inventory()))
            result = subprocess.run([sys.executable, str(SCRIPT), str(inventory)],
                                    text=True, capture_output=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Runtime dependency policy passed:', result.stdout)
        notices = [line for line in result.stdout.splitlines()
                   if line.startswith('Reviewed vendor preview retained: ')]
        self.assertEqual([
            'Reviewed vendor preview retained: androidx.privacysandbox.ads:ads-adservices-java:1.0.0-beta05',
            'Reviewed vendor preview retained: androidx.privacysandbox.ads:ads-adservices:1.0.0-beta05',
        ], notices)


if __name__ == '__main__':
    unittest.main()
