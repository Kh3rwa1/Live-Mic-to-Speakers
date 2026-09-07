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
        if PREVIEW.search(version) or re.search(r'[+\[\]()]|latest', version, re.I):
            errors.append(configuration + ': unstable/dynamic version: ' + coordinate + ':' + version)
        if coordinate in MINIMUM:
            try:
                if numeric_version(version) < MINIMUM[coordinate] + (0,) * (4 - len(MINIMUM[coordinate])):
                    errors.append(configuration + ': unsupported SDK baseline: ' + coordinate + ':' + version)
            except ValueError as error:
                errors.append(configuration + ': ' + str(error))
    for configuration, selected in seen.items():
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
        count = validate(json.loads(args.inventory.read_text()))
    except (OSError, ValueError, TypeError) as error:
        parser.exit(1, 'Runtime dependency policy failed:\n' + str(error) + '\n')
    print(f'Runtime dependency policy passed: {count} module entries across debug and release.')


if __name__ == '__main__':
    main()
