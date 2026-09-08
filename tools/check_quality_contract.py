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
for name in ['RecordAudioActivity', 'HoldToSpeakActivity', 'LiveMicrophoneActivity', 'Setting_Activity', 'MusicActivity']:
    text = (source / (name + '.java')).read_text()
    if re.search(r'\.set(?:Text|ContentDescription)\("', text):
        problems.append(f'{name}: hard-coded UI label')
if problems:
    raise SystemExit('\n'.join(problems))
print('Quality contract passed: resource-based labels, scalable text, named controls, scrollable layouts, collapsible ad space.')
