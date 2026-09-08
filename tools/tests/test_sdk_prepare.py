import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'prepare_android_sdk.sh'


class SdkPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='android sdk test ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sdk = self.root / 'sdk'
        self.latest = self.sdk / 'cmdline-tools' / 'latest'
        self.latest.mkdir(parents=True)
        (self.latest / 'original.txt').write_text('preserve me')
        (self.root / 'bin').mkdir()
        (self.root / 'runner').mkdir()
        installer = self.root / 'bin' / 'sdkmanager'
        installer.write_text('''#!/usr/bin/env bash
set -eu
[ "$1" = --install ] && [ "$2" = 'cmdline-tools;20.0' ]
[ "${TEST_MODE:-}" != fail ] || exit 23
folder="$ANDROID_HOME/cmdline-tools/20.0"
mkdir -p "$folder/bin"
printf 'Pkg.Revision=%s\\n' "${TEST_REVISION:-20.0}" > "$folder/source.properties"
printf '#!/bin/sh\\nprintf "20.0\\\\n"\\n' > "$folder/bin/sdkmanager"
printf '#!/bin/sh\\nexit 0\\n' > "$folder/bin/avdmanager"
chmod +x "$folder/bin/sdkmanager" "$folder/bin/avdmanager"
[ "${TEST_MODE:-}" != missing ] || rm "$folder/bin/avdmanager"
''')
        installer.chmod(0o755)
        self.env = dict(os.environ, ANDROID_HOME=str(self.sdk), RUNNER_TEMP=str(self.root / 'runner'),
                        GITHUB_PATH=str(self.root / 'github-path'), PATH=str(self.root / 'bin') + os.pathsep + os.environ['PATH'])

    def run_prepare(self, **settings):
        return subprocess.run(['bash', str(SCRIPT)], env=dict(self.env, **settings),
                              text=True, capture_output=True, timeout=10)

    def test_old_tools_are_preserved_and_new_tools_selected(self):
        result = self.run_prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(self.latest.is_symlink())
        self.assertEqual(self.sdk / 'cmdline-tools' / '20.0', self.latest.resolve())
        preserved = list((self.root / 'runner').glob('android-command-line-tools.*/latest/original.txt'))
        self.assertEqual(1, len(preserved))
        self.assertEqual('preserve me', preserved[0].read_text())
        self.assertIn(str(self.latest / 'bin'), (self.root / 'github-path').read_text())

    def test_repeated_preparation_does_not_relocate_selected_tools(self):
        self.assertEqual(0, self.run_prepare().returncode)
        self.assertEqual(0, self.run_prepare().returncode)
        self.assertEqual(1, len(list((self.root / 'runner').iterdir())))
        self.assertTrue((self.latest / 'bin' / 'avdmanager').is_file())

    def test_installer_failure_is_not_hidden(self):
        self.assertEqual(23, self.run_prepare(TEST_MODE='fail').returncode)
        self.assertTrue((self.latest / 'original.txt').is_file())
        self.assertFalse(self.latest.is_symlink())

    def test_incomplete_install_does_not_replace_working_tools(self):
        result = self.run_prepare(TEST_MODE='missing')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('not installed correctly', result.stderr)
        self.assertTrue((self.latest / 'original.txt').is_file())

    def test_incompatible_parser_is_rejected(self):
        result = self.run_prepare(TEST_REVISION='19.0')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('unexpected command-line tools revision', result.stderr)
        self.assertTrue((self.latest / 'original.txt').is_file())

    def test_patch_revision_within_pinned_series_is_supported(self):
        result = self.run_prepare(TEST_REVISION='20.0.1')
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main()
