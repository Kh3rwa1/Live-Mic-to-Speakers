#!/usr/bin/env python3
"""Print bounded JUnit failure details; the instrumentation step remains the CI gate."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def summarize(directory, output=None):
    output = sys.stdout if output is None else output
    reports = sorted(Path(directory).rglob('*.xml'))
    if not reports:
        print('Android diagnostics: no JUnit XML reports found; inspect the Gradle log.', file=output)
        return 0
    failures = unreadable = 0
    for report in reports:
        try:
            if report.stat().st_size > 2 * 1024 * 1024:
                raise ValueError('report exceeds the diagnostic size limit')
            root = ET.parse(report).getroot()
        except (OSError, ET.ParseError, ValueError) as error:
            unreadable += 1
            print('Android diagnostics: unreadable report ' + report.name + ': ' + str(error), file=output)
            continue
        for case in root.iter():
            if case.tag.rsplit('}', 1)[-1] != 'testcase':
                continue
            for problem in case:
                if problem.tag.rsplit('}', 1)[-1] not in ('failure', 'error'):
                    continue
                failures += 1
                if failures > 20:
                    continue
                name = case.get('classname', '?') + '#' + case.get('name', '?')
                print('FAILED ' + ' '.join(name.splitlines())[:300], file=output)
                detail = (problem.get('message', '') + '\n' + ''.join(problem.itertext())).strip()
                for line in detail.splitlines()[:30]:
                    print('  ' + line[:500], file=output)
    print(f'Android diagnostics: {failures} failures, {len(reports)} XML reports, {unreadable} unreadable.', file=output)
    if failures > 20:
        print('Only the first 20 failures are printed; full reports are uploaded.', file=output)
    return failures


if __name__ == '__main__':
    summarize(sys.argv[1] if len(sys.argv) > 1 else 'app/build/outputs/androidTest-results')
