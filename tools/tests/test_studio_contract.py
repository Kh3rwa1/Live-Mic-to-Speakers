"""Offline contracts for the adaptive audio UI; not a native rendering certificate."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
SCREENS = ['activity_main_new', 'activity_live_microphone_new', 'activity_record_audio_new', 'activity_hold_to_speak_new']


def luminance(value):
    rgb = [int(value[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    rgb = [v / 12.92 if v <= 0.04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(a * b for a, b in zip(rgb, (.2126, .7152, .0722)))


class StudioContractTest(unittest.TestCase):
    def test_screens_keep_scalable_untruncated_labels(self):
        for screen in SCREENS:
            tree = ET.parse(RES / 'layout' / (screen + '.xml'))
            self.assertTrue(any(e.tag.endswith('ScrollView') for e in tree.iter()), screen)
            for element in tree.iter():
                for name in ('text', 'contentDescription', 'hint'):
                    value = element.get(ANDROID + name)
                    self.assertTrue(not value or value.startswith('@'), (screen, name, value))
                size = element.get(ANDROID + 'textSize')
                self.assertTrue(not size or size.endswith('sp'), (screen, size))
                self.assertIsNone(element.get(ANDROID + 'ellipsize'), screen)
                self.assertFalse('LottieAnimationView' in element.tag, 'A decorative animation cannot masquerade as input data')

    def test_primary_action_is_adaptive(self):
        resources = ET.parse(RES / 'values/studio.xml').getroot()
        style = resources.find("style[@name='StudioAction']")
        values = {item.get('name'): item.text for item in style}
        self.assertEqual(values['android:layout_height'], 'wrap_content')
        self.assertEqual(values['android:minHeight'], '64dp')
        self.assertEqual(values['android:focusable'], 'true')
        for screen in SCREENS[1:]:
            tree = ET.parse(RES / 'layout' / (screen + '.xml'))
            actions = [e for e in tree.iter() if e.get(ANDROID + 'id') == '@+id/iv_start_stop_new']
            self.assertEqual(len(actions), 1)
            self.assertEqual(actions[0].get('style'), '@style/StudioAction')

    def test_meter_is_quiet_and_bounded(self):
        tree = ET.parse(RES / 'layout/studio_input_meter.xml')
        meter = next(e for e in tree.iter() if e.get(ANDROID + 'id') == '@+id/studio_input_level')
        self.assertEqual(meter.get(ANDROID + 'max'), '100')
        self.assertEqual(meter.get(ANDROID + 'importantForAccessibility'), 'no')
        value = next(e for e in tree.iter() if e.get(ANDROID + 'id') == '@+id/studio_level_value')
        self.assertEqual(value.get(ANDROID + 'accessibilityLiveRegion'), 'none')

    def test_normal_text_contrast_is_aa(self):
        colors = {e.get('name'): e.text for e in ET.parse(RES / 'values/studio.xml').getroot().findall('color')}
        for foreground, background in [('studio_ink', 'studio_canvas'), ('studio_muted', 'studio_canvas'), ('studio_blue', 'studio_soft')]:
            light, dark = sorted([luminance(colors[foreground]), luminance(colors[background])], reverse=True)
            self.assertGreaterEqual((light + .05) / (dark + .05), 4.5, (foreground, background))


if __name__ == '__main__':
    unittest.main()
