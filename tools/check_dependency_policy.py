#!/usr/bin/env python3
"""Check resolved runtime support policy. Not a comprehensive CVE/security scanner."""
import argparse
import json
import re
from pathlib import Path

CONFIGURATIONS = {'debugRuntimeClasspath', 'releaseRuntimeClasspath'}
BANNED_GROUPS = {'androidx.localbroadcastmanager', 'io.reactivex.rxjava2', 'com.android.support'}
MINIMUM = {
    'com.google.android.gms:play-services-ads': (25, 4, 0),
    'com.google.android.ump:user-messaging-platform': (4, 0, 0),
    'com.google.ads.mediation:facebook': (6, 22, 0, 0),
}
PREVIEW = re.compile(r'(?:^|[.\-])(alpha|beta|rc\d*|snapshot|dev|eap)(?:[.\-\d]|$)', re.I)

# The current stable ads SDK resolves this beta-only AndroidX pair. Do not
# exclude its runtime classes or accept the whole group. Keep this exception
# tied to the exact reviewed versions and SDK; see NON_UI_MODERNIZATION.md.
REVIEWED_PREVIEWS = {
    'androidx.privacysandbox.ads:ads-adservices': '1.0.0-beta05',
    'androidx.privacysandbox.ads:ads-adservices-java': '1.0.0-beta05',
}
REVIEWED_ADS_SDK = ('com.google.android.gms:play-services-ads', '25.4.0')


def numeric_version(value):
    if not re.fullmatch(r'\d+(?:\.\d+){1,3}', value):
        raise ValueError('Expected a stable numeric SDK version: ' + value)
    parts = tuple(int(part) for part in value.split('.'))
    return parts + (0,) * (4 - len(parts))


def validate(data):
    if not isinstance(data, dict) or data.get('schemaVersion') != 1:
        raise ValueError('Missing or unsupported dependency inventory schema')
    if set(data.get('configurations', [])) != CONFIGURATIONS:
        raise ValueError('Both debug and release runtime configurations must be verified')
    modules = data.get('modules')
    if not isinstance(modules, list) or not modules:
        raise ValueError('Dependency inventory is empty')
    errors = []
    seen = {configuration: {} for configuration in CONFIGURATIONS}
    for entry in modules:
        if not isinstance(entry, dict) or not all(isinstance(entry.get(key), str) and entry[key]
                for key in ('configuration', 'group', 'name', 'version')):
            raise ValueError('Malformed dependency inventory entry')
        configuration = entry['configuration']
        if configuration not in seen:
            raise ValueError('Unexpected runtime configuration: ' + configuration)
        coordinate = entry['group'] + ':' + entry['name']
        version = entry['version']
        if coordinate in seen[configuration]:
            errors.append(configuration + ': duplicate module ' + coordinate)
        seen[configuration][coordinate] = version
        if entry['group'] in BANNED_GROUPS:
            errors.append(configuration + ': removed legacy dependency returned: ' + coordinate)
        unreviewed_preview = PREVIEW.search(version) and REVIEWED_PREVIEWS.get(coordinate) != version
        if unreviewed_preview or re.search(r'[+\[\]()]|latest', version, re.I):
            errors.append(configuration + ': unstable/dynamic version: ' + coordinate + ':' + version)
        if coordinate in MINIMUM:
            try:
                if numeric_version(version) < MINIMUM[coordinate] + (0,) * (4 - len(MINIMUM[coordinate])):
                    errors.append(configuration + ': unsupported SDK baseline: ' + coordinate + ':' + version)
            except ValueError as error:
                errors.append(configuration + ': ' + str(error))
    for configuration, selected in seen.items():
        if any(PREVIEW.search(selected.get(coordinate, '')) for coordinate in REVIEWED_PREVIEWS):
            sdk, reviewed_version = REVIEWED_ADS_SDK
            if selected.get(sdk) != reviewed_version:
                errors.append(configuration + ': Privacy Sandbox preview exception requires reviewed ads SDK: '
                              + sdk + ':' + reviewed_version)
            if any(selected.get(coordinate) != version for coordinate, version in REVIEWED_PREVIEWS.items()):
                errors.append(configuration + ': both modules of the reviewed Privacy Sandbox pair '
                              'must resolve together at their exact reviewed versions')
        for required in MINIMUM:
            if required not in selected:
                errors.append(configuration + ': missing required SDK: ' + required)
    if errors:
        raise ValueError('\n'.join(errors))
    return len(modules)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory', type=Path)
    args = parser.parse_args()
    try:
        inventory = json.loads(args.inventory.read_text())
        count = validate(inventory)
    except (OSError, ValueError, TypeError) as error:
        details = 'Runtime dependency policy failed:\n' + str(error)
        parser.exit(1, ''.join('Error: ' + line + '\n' for line in details.splitlines()))
    print(f'Runtime dependency policy passed: {count} module entries across debug and release.')
    retained = {entry['group'] + ':' + entry['name'] + ':' + entry['version']
                for entry in inventory['modules']
                if REVIEWED_PREVIEWS.get(entry['group'] + ':' + entry['name']) == entry['version']}
    for coordinate in sorted(retained):
        print('Reviewed vendor preview retained: ' + coordinate)


if __name__ == '__main__':
    main()
