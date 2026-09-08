import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

PATH = Path(__file__).resolve().parents[1] / 'summarize_android_failures.py'
SPEC = importlib.util.spec_from_file_location('android_failure_reports', PATH)
REPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPORTER)


class AndroidFailureReportsTest(unittest.TestCase):
    def report(self, xml):
        with tempfile.TemporaryDirectory() as directory:
            if xml is not None:
                Path(directory, 'results.xml').write_text(xml)
            output = io.StringIO()
            count = REPORTER.summarize(directory, output)
            return count, output.getvalue()

    def test_nested_failures_and_errors_include_names_and_stacks(self):
        count, text = self.report('''<testsuites xmlns="urn:junit"><testsuite>
          <testcase classname="LayoutTest" name="longRoute"><failure message="Text clipped">java.lang.AssertionError\nat LayoutTest.java:30</failure></testcase>
          <testcase classname="CaptureTest" name="start"><error message="Recorder unavailable" /></testcase>
          </testsuite></testsuites>''')
        self.assertEqual(2, count)
        self.assertIn('FAILED LayoutTest#longRoute', text)
        self.assertIn('Text clipped', text)
        self.assertIn('LayoutTest.java:30', text)
        self.assertIn('FAILED CaptureTest#start', text)

    def test_passed_and_skipped_cases_are_not_failures(self):
        count, text = self.report('<testsuite><testcase name="ok"/><testcase name="skip"><skipped/></testcase></testsuite>')
        self.assertEqual(0, count)
        self.assertNotIn('FAILED ', text)

    def test_missing_and_unreadable_reports_are_explicit(self):
        self.assertIn('no JUnit XML reports found', self.report(None)[1])
        self.assertIn('unreadable report results.xml', self.report('<broken>')[1])

    def test_output_is_bounded_without_hiding_failure_count(self):
        case = '<testcase classname="Layout" name="x"><failure>' + '\n'.join(['stack line'] * 40) + '</failure></testcase>'
        count, text = self.report('<testsuite>' + case * 21 + '</testsuite>')
        self.assertEqual(21, count)
        self.assertEqual(20, text.count('FAILED Layout#x'))
        self.assertEqual(600, text.count('stack line'))
        self.assertIn('Only the first 20 failures', text)


if __name__ == '__main__':
    unittest.main()
