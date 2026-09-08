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


if __name__ == '__main__':
    unittest.main()
