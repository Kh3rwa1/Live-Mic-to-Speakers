import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('dependency_policy', Path(__file__).resolve().parents[1] / 'check_dependency_policy.py')
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


def valid_inventory():
    return {'schemaVersion': 1, 'configurations': sorted(policy.CONFIGURATIONS), 'modules': [
        {'configuration': configuration, 'group': coordinate.split(':')[0],
         'name': coordinate.split(':')[1], 'version': '.'.join(map(str, version))}
        for configuration in sorted(policy.CONFIGURATIONS)
        for coordinate, version in policy.MINIMUM.items()
    ]}


def ads_preview_inventory():
    # Exact transitive pair reported by PR #7's resolved runtime inventory.
    data = valid_inventory()
    for configuration in sorted(policy.CONFIGURATIONS):
        for name in ('ads-adservices', 'ads-adservices-java'):
            data['modules'].append({'configuration': configuration,
                                    'group': 'androidx.privacysandbox.ads',
                                    'name': name, 'version': '1.0.0-beta05'})
    return data


class DependencyPolicyTest(unittest.TestCase):
    def test_supported_debug_and_release(self):
        self.assertEqual(6, policy.validate(valid_inventory()))

    def test_missing_release_is_rejected(self):
        data = valid_inventory()
        data['configurations'] = ['debugRuntimeClasspath']
        with self.assertRaisesRegex(ValueError, 'Both debug and release'):
            policy.validate(data)

    def test_empty_inventory_is_rejected(self):
        data = valid_inventory()
        data['modules'] = []
        with self.assertRaisesRegex(ValueError, 'empty'):
            policy.validate(data)

    def test_missing_sdk_in_one_configuration_is_rejected(self):
        data = valid_inventory()
        data['modules'].pop()
        with self.assertRaisesRegex(ValueError, 'missing required SDK'):
            policy.validate(data)

    def test_legacy_runtime_dependencies_are_rejected(self):
        for group in sorted(policy.BANNED_GROUPS):
            with self.subTest(group=group):
                data = valid_inventory()
                data['modules'].append({'configuration': 'releaseRuntimeClasspath', 'group': group, 'name': 'legacy', 'version': '1.1.0'})
                with self.assertRaisesRegex(ValueError, 'legacy dependency'):
                    policy.validate(data)

    def test_old_ads_sdk_is_rejected(self):
        data = valid_inventory()
        data['modules'][0]['version'] = '23.3.0'
        with self.assertRaisesRegex(ValueError, 'unsupported SDK'):
            policy.validate(data)

    def test_prerelease_and_dynamic_versions_are_rejected(self):
        for version in ('1.0.0-alpha01', '1.0-beta02', '1.0.0-rc01', '1.0-SNAPSHOT', '1.+', 'latest.release', '[1.0,2.0)'):
            with self.subTest(version=version):
                data = valid_inventory()
                data['modules'].append({'configuration': 'debugRuntimeClasspath', 'group': 'example', 'name': 'example', 'version': version})
                with self.assertRaisesRegex(ValueError, 'unstable/dynamic'):
                    policy.validate(data)

    def test_exact_reviewed_ads_preview_pair_is_supported(self):
        self.assertEqual(10, policy.validate(ads_preview_inventory()))

    def test_viewpager_preview_still_fails_with_reviewed_ads_pair(self):
        data = ads_preview_inventory()
        for configuration in sorted(policy.CONFIGURATIONS):
            data['modules'].append({'configuration': configuration,
                                    'group': 'androidx.viewpager2', 'name': 'viewpager2',
                                    'version': '1.1.0-beta02'})
        with self.assertRaisesRegex(ValueError, 'unstable/dynamic version: androidx.viewpager2:viewpager2'):
            policy.validate(data)
        for entry in data['modules']:
            if entry['group'] == 'androidx.viewpager2':
                entry['version'] = '1.1.0'
        self.assertEqual(12, policy.validate(data))

    def test_reviewed_preview_versions_cannot_drift(self):
        for configuration in sorted(policy.CONFIGURATIONS):
            for name in ('ads-adservices', 'ads-adservices-java'):
                for version in ('1.0.0-beta04', '1.1.0-beta13', '1.0.0-rc01', '1.+',
                                'latest.release', '[1.0,2.0)', '1.0.0-beta05-SNAPSHOT'):
                    with self.subTest(configuration=configuration, name=name, version=version):
                        data = ads_preview_inventory()
                        for entry in data['modules']:
                            if entry['configuration'] == configuration and entry['name'] == name:
                                entry['version'] = version
                        with self.assertRaisesRegex(ValueError, 'unstable/dynamic'):
                            policy.validate(data)

    def test_preview_exception_is_not_group_wide(self):
        data = ads_preview_inventory()
        data['modules'].append({'configuration': 'debugRuntimeClasspath',
                                'group': 'androidx.privacysandbox.ads', 'name': 'unreviewed',
                                'version': '1.0.0-beta05'})
        with self.assertRaisesRegex(ValueError, 'unstable/dynamic'):
            policy.validate(data)

    def test_preview_pair_must_be_complete_in_each_configuration(self):
        for configuration in sorted(policy.CONFIGURATIONS):
            for name in ('ads-adservices', 'ads-adservices-java'):
                with self.subTest(configuration=configuration, name=name):
                    data = ads_preview_inventory()
                    data['modules'] = [entry for entry in data['modules']
                                       if not (entry['configuration'] == configuration
                                               and entry['name'] == name)]
                    with self.assertRaisesRegex(ValueError, 'reviewed Privacy Sandbox pair'):
                        policy.validate(data)

    def test_reviewed_preview_pair_requires_exact_ads_sdk(self):
        for configuration in sorted(policy.CONFIGURATIONS):
            for version in (None, '25.4.1', '25.5.0', '26.0.0'):
                with self.subTest(configuration=configuration, version=version):
                    data = ads_preview_inventory()
                    for entry in data['modules']:
                        if entry['configuration'] == configuration and entry['name'] == 'play-services-ads':
                            entry['version'] = version
                    data['modules'] = [entry for entry in data['modules'] if entry['version'] is not None]
                    with self.assertRaisesRegex(ValueError, 'requires reviewed ads SDK'):
                        policy.validate(data)

    def test_stable_and_preview_siblings_cannot_be_mixed(self):
        data = ads_preview_inventory()
        data['modules'][-1]['version'] = '1.0.0'
        with self.assertRaisesRegex(ValueError, 'reviewed Privacy Sandbox pair'):
            policy.validate(data)

    def test_stable_numeric_comparison_is_not_lexical(self):
        self.assertGreater(policy.numeric_version('25.10.0'), policy.numeric_version('25.4.0'))
        self.assertEqual(policy.numeric_version('4.0'), policy.numeric_version('4.0.0.0'))

    def test_duplicate_records_are_rejected(self):
        data = valid_inventory()
        data['modules'].append(copy.deepcopy(data['modules'][0]))
        with self.assertRaisesRegex(ValueError, 'duplicate module'):
            policy.validate(data)

    def test_unknown_schema_and_malformed_entry_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'schema'):
            policy.validate({})
        data = valid_inventory()
        data['modules'][0].pop('name')
        with self.assertRaisesRegex(ValueError, 'Malformed'):
            policy.validate(data)

    def test_unexpected_configuration_is_rejected(self):
        data = valid_inventory()
        data['modules'][0]['configuration'] = 'testRuntimeClasspath'
        with self.assertRaisesRegex(ValueError, 'Unexpected'):
            policy.validate(data)


if __name__ == '__main__':
    unittest.main()
