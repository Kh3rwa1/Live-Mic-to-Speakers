"""Exercise the actual isolated Actions reporter, without network or credentials."""
import json
from pathlib import Path
import subprocess
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]


def report(log, *, failed=True, unavailable=False, old_order=False):
    workflow = (ROOT / '.github/workflows/android.yml').read_text()
    script = textwrap.dedent(workflow.split('          script: |\n            const jobs = ', 1)[1])
    script = 'const jobs = ' + script
    if old_order:
        script = script.replace('.slice(-10000)', '.slice(0, 6500)')
    harness = r'''
const input = JSON.parse(process.argv[1]);
const comments = [];
const github = {
  paginate: async () => input.failed ? [{id: 1, name: 'device-smoke', conclusion: 'failure',
    html_url: 'https://github.com/example/app/actions/runs/1/job/2',
    steps: [{name: 'Run device checks', conclusion: 'failure'}]}] : [],
  request: async () => { if (input.unavailable) throw new Error('unavailable'); return {data: input.log}; },
  rest: {issues: {createComment: async data => comments.push(data.body)}}
};
const context = {repo: {owner: 'example', repo: 'app'}, runId: 1, issue: {number: 14},
  payload: {pull_request: {head: {sha: 'test-sha'}}}};
const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
new AsyncFunction('github', 'context', 'Buffer', input.script)(github, context, Buffer)
  .then(() => process.stdout.write(JSON.stringify(comments)))
  .catch(error => { console.error(error); process.exit(1); });
'''
    result = subprocess.run(['node', '-e', harness, json.dumps(dict(
        log=log, failed=failed, unavailable=unavailable, script=script))],
        capture_output=True, text=True, check=True, timeout=10)
    return json.loads(result.stdout)


class FailureExcerptTest(unittest.TestCase):
    def test_failure_survives_long_sdk_setup_noise(self):
        log = ('Warning: package metadata is repeated ' + 'x' * 180 + '\n') * 100
        log += '\nActiveToolLayoutTest FAILED\njava.lang.AssertionError: essential text was clipped\n'
        body = report(log)[0]
        self.assertIn('ActiveToolLayoutTest FAILED', body)
        self.assertIn('essential text was clipped', body)
        self.assertLess(len(body), 11000)
        # Demonstrate that the old head-only excerpt lost this exact failure.
        self.assertNotIn('essential text was clipped', report(log, old_order=True)[0])

    def test_redacts_tokens_and_untrusted_fences(self):
        body = report('Error: ```ghp_synthetic_not_a_real_secret123```\n')[0]
        self.assertIn('[redacted]', body)
        self.assertNotIn('ghp_synthetic', body)
        self.assertEqual(2, body.count('```'))
        long_token = 'ghp_synthetic_' + 'x' * 12000 + '_synthetic_suffix'
        self.assertNotIn('_synthetic_suffix', report('Error: ' + long_token)[0])

    def test_success_does_not_create_comment(self):
        self.assertEqual([], report('BUILD SUCCESSFUL', failed=False))

    def test_unavailable_logs_keep_job_link(self):
        body = report('', unavailable=True)[0]
        self.assertIn('Log retrieval unavailable', body)
        self.assertIn('[Job details](https://github.com/example/app/actions/runs/1/job/2)', body)


if __name__ == '__main__':
    unittest.main()
