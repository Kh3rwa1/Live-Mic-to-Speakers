#!/usr/bin/env python3
"""Source safeguards for library layouts. Not a substitute for Android/visual tests."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
APP = '{http://schemas.android.com/apk/res-auto}'
LAYOUTS = ('activity_record_audio_list_new', 'activity_music_list_new',
           'fragment_audio_list_new', 'item_audio_layout_new', 'music_audio_layout_new')


def layout_problems(tree, name):
    problems = []
    for node in tree.iter():
        for namespace, attr in ((ANDROID, 'text'), (ANDROID, 'hint'),
                                (ANDROID, 'contentDescription'), (APP, 'queryHint')):
            value = node.get(namespace + attr)
            if value and not value.startswith('@'):
                problems.append(f'{name}: hard-coded {attr}')
        if node.get(ANDROID + 'ellipsize') or node.get(ANDROID + 'maxLines') or node.get(ANDROID + 'singleLine') == 'true':
            problems.append(f'{name}: library text must remain fully readable')
        size = node.get(ANDROID + 'textSize')
        if size and not size.endswith('sp'):
            problems.append(f'{name}: text must scale with user settings')
        if node.tag == 'ImageButton' and not node.get(ANDROID + 'contentDescription'):
            problems.append(f'{name}: unnamed image action')
    return problems


def check(root):
    problems = []
    res = root / 'app/src/main/res'
    for name in LAYOUTS:
        problems.extend(layout_problems(ET.parse(res / 'layout' / (name + '.xml')).getroot(), name))
    fragment = ET.parse(res / 'layout/fragment_audio_list_new.xml').getroot()
    nodes = {node.get(ANDROID + 'id'): node for node in fragment.iter()}
    if '@+id/search' not in nodes:
        problems.append('Library fragment has no search field')
    for name in ('@+id/library_list_pane', '@+id/mediaPlayerLayout'):
        node = nodes.get(name)
        if node is None or node not in list(fragment) or node.get(ANDROID + 'layout_weight') != '1':
            problems.append('Library list and preview must be separate weighted panes')
    preview = nodes.get('@+id/mediaPlayerLayout')
    if preview is None or not preview.tag.endswith('ScrollView'):
        problems.append('Preview controls must remain scrollable')
    row = ET.parse(res / 'layout/item_audio_layout_new.xml').getroot()
    buttons = {node.get(ANDROID + 'id'): node for node in row.iter('Button')}
    if not {'@+id/play', '@+id/share_recording'}.issubset(buttons):
        problems.append('History needs visible play and share buttons')
    values = ET.parse(res / 'values/library_quality.xml').getroot()
    keys = [(node.tag, node.get('name')) for node in values]
    if len(keys) != len(set(keys)):
        problems.append('Duplicate library resources')
    style = next(node for node in values if node.tag == 'style' and node.get('name') == 'LibraryButton')
    items = {node.get('name'): node.text for node in style}
    if items.get('android:minHeight') != '48dp' or items.get('android:minWidth') != '48dp':
        problems.append('Library actions must retain 48dp minimum touch targets')
    java = root / 'app/src/main/java/com/word/way'
    for path in ('activity/RecordingHistoryActivity.java', 'player/activity/MusicListActivity.java',
                 'player/fragment/LocalAudioPickerFragment.java', 'player/adapter/SongListAdapter.java',
                 'player/adapter/AudioAdapter.java'):
        if re.search(r'\.set(?:Text|ContentDescription|QueryHint)\("', (java / path).read_text()):
            problems.append(f'{path}: hard-coded user-facing label')
    source = (java / 'activity/LiveMicrophoneActivity.java').read_text()
    for reason in ('PERMISSION', 'SERVICE_UNAVAILABLE', 'FOCUS_UNAVAILABLE', 'UNSUPPORTED_CONFIGURATION',
                   'MICROPHONE_UNAVAILABLE', 'READ_FAILED', 'OUTPUT_FAILED', 'CANCELLED'):
        if f'case {reason}:' not in source:
            problems.append(f'Missing live-microphone recovery mapping: {reason}')
    return problems


if __name__ == '__main__':
    errors = check(Path(__file__).resolve().parents[1])
    if errors:
        raise SystemExit('\n'.join(errors))
    print('PASS: library source contracts (not native visual certification)')
