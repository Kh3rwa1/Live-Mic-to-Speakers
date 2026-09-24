#!/usr/bin/env python3
"""Dependency-free checks for the newly migrated core screens, not visual certification."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
a = '{http://schemas.android.com/apk/res/android}'
layouts = ['activity_record_audio_new', 'activity_hold_to_speak_new',
           'activity_live_microphone_new', 'activity_music_new', 'activity_setting_new']
problems = []
for name in layouts:
    path = root / 'app/src/main/res/layout' / (name + '.xml')
    tree = ET.parse(path)
    for element in tree.iter():
        for attr in ['text', 'contentDescription', 'hint']:
            value = element.get(a + attr)
            if value and not value.startswith('@'):
                problems.append(f'{path.name}: hard-coded {attr}')
        size = element.get(a + 'textSize')
        if size and not size.endswith('sp'):
            problems.append(f'{path.name}: text size must use sp')
        if element.tag == 'ImageButton' and not element.get(a + 'contentDescription'):
            problems.append(f'{path.name}: unnamed image button')
        if element.get(a + 'id') == '@+id/nativeLay' and element.get(a + 'minHeight'):
            problems.append(f'{path.name}: ad container reserves empty space')
    if not any(e.tag.endswith('ScrollView') for e in tree.iter()):
        problems.append(f'{path.name}: tools must remain scrollable at large font scales')

styles = ET.parse(root / 'app/src/main/res/values/styles_tools.xml')
for element in styles.iter('item'):
    if element.get('name') == 'android:textSize' and not element.text.endswith('sp'):
        problems.append('Tool text styles must use sp')
strings = ET.parse(root / 'app/src/main/res/values/strings_tools.xml')
keys = [e.get('name') for e in strings.getroot()]
if len(keys) != len(set(keys)):
    problems.append('Duplicate tool string resources')
source = root / 'app/src/main/java/com/word/way/activity'
for name in ['RecordAudioActivity', 'HoldToSpeakActivity', 'LiveMicrophoneActivity', 'SettingsActivity', 'MusicActivity']:
    text = (source / (name + '.java')).read_text()
    if re.search(r'\.set(?:Text|ContentDescription)\("', text):
        problems.append(f'{name}: hard-coded UI label')

def check_test_device_ids(repo_root):
    found = []
    main_dirs = [repo_root / 'app/src/main/java', repo_root / 'ads/src/main/java']
    hex32_pattern = re.compile(r'setTestDeviceIds\s*\([^)]*["\'][0-9A-F]{32}["\']', re.DOTALL)
    for src_dir in main_dirs:
        if not src_dir.exists():
            continue
        for java_file in src_dir.rglob('*.java'):
            content = java_file.read_text(encoding='utf-8')
            if 'F96C3A5E789445DD5896009229E43316' in content:
                found.append(f'{java_file.name}: hardcoded test device ID literal F96C3A5E789445DD5896009229E43316')
            if hex32_pattern.search(content):
                found.append(f'{java_file.name}: hardcoded 32-character hex test device ID near setTestDeviceIds')
    return found

problems.extend(check_test_device_ids(root))

if problems:
    raise SystemExit('\n'.join(problems))
print('Quality contract passed: resource-based labels, scalable text, named controls, scrollable layouts, collapsible ad space, no hardcoded test devices.')
