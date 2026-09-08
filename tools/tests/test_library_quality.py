import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('library_contract', ROOT / 'tools/check_library_contract.py')
contract = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(contract)


class LibraryQualityContractTest(unittest.TestCase):
    def test_production_sources_meet_contract(self):
        self.assertEqual([], contract.check(ROOT))

    def test_rejects_hardcoded_search_and_labels(self):
        tree = ET.fromstring('<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
                             'xmlns:app="http://schemas.android.com/apk/res-auto" '
                             'android:text="Literal" app:queryHint="Literal"/>')
        self.assertEqual(2, len(contract.layout_problems(tree, 'negative-fixture')))

    def test_rejects_truncated_and_unscalable_text(self):
        tree = ET.fromstring('<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
                             'android:ellipsize="end" android:textSize="12dp"/>')
        self.assertEqual(2, len(contract.layout_problems(tree, 'negative-fixture')))

    def test_rejects_unnamed_image_actions(self):
        self.assertTrue(contract.layout_problems(ET.fromstring('<ImageButton/>'), 'negative-fixture'))

    def test_accepts_resource_based_scalable_text(self):
        tree = ET.fromstring('<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
                             'android:text="@string/library_title" android:textSize="16sp"/>')
        self.assertEqual([], contract.layout_problems(tree, 'positive-fixture'))


if __name__ == '__main__':
    unittest.main()
