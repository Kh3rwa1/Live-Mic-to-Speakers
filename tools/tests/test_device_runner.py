"""Exercise the CI shell runner without touching an emulator or real device."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[1] / "run_device_checks.sh"
FAKE_ADB = r'''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
p=Path(os.environ['FAKE_STATE']); state=json.loads(p.read_text()); a=sys.argv[1:]
if a[:2]==['shell','settings']:
    action,group,key=a[2:5]; k=group+'/'+key
    if action=='get': print(state.get(k,'null'))
    elif action=='delete': state.pop(k,None)
    elif action=='put':
        if not (os.environ.get('IGNORE_FONT') and key=='font_scale' and a[5]=='1.0'): state[k]=a[5]
    else: sys.exit(2)
    p.write_text(json.dumps(state))
elif a and a[0]=='pull':
    if os.environ.get('FAIL_PULL'): sys.exit(1)
    out=Path(a[-1]); out.mkdir(parents=True,exist_ok=True)
    for name in ['record','hold','live','settings','settings-rtl','player','keyboard']:
        if name!=os.environ.get('MISSING_SCREEN'): (out/(name+'.png')).write_bytes(b'PNG fixture')
else: sys.exit(2)
'''

class DeviceRunnerTest(unittest.TestCase):
    def invoke(self, overrides=None, initial=None, font='1.0', stale=False):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); binary=root/'bin'; binary.mkdir()
            adb=binary/'adb'; adb.write_text(FAKE_ADB); adb.chmod(0o755)
            original={'system/font_scale':'1.5','secure/show_ime_with_hard_keyboard':'0'} if initial is None else initial
            state=root/'state.json'; state.write_text(json.dumps(original))
            (root/'gradlew').write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$@" > gradle-args\nexit "${TEST_EXIT:-0}"\n')
            if stale:
                images=root/'app/build/quality-screenshots'; images.mkdir(parents=True)
                (images/'keyboard.png').write_bytes(b'old screenshot')
            env={**os.environ,'PATH':str(binary)+os.pathsep+os.environ['PATH'],'FAKE_STATE':str(state),**(overrides or {})}
            result=subprocess.run(['bash',str(RUNNER),font],cwd=root,env=env,capture_output=True,text=True,timeout=20)
            final=json.loads(state.read_text())
            args=(root/'gradle-args').read_text() if (root/'gradle-args').exists() else ''
            return result,final,original,args

    def test_success_restores_both_device_preferences(self):
        result,final,initial,args=self.invoke()
        self.assertEqual(0,result.returncode,result.stdout+result.stderr); self.assertEqual(initial,final)
        self.assertIn('leaveApksInstalledAfterRun=true',args)
        self.assertRegex(args,r'qualityOutputDir=quality-screenshots-\d+-\d+')

    def test_large_font_run_restores_original_scale(self):
        result,final,initial,_=self.invoke(font='2.0')
        self.assertEqual(0,result.returncode); self.assertEqual(initial,final)

    def test_failed_tests_remain_failed_and_restore_settings(self):
        result,final,initial,_=self.invoke({'TEST_EXIT':'17'})
        self.assertEqual(17,result.returncode); self.assertEqual(initial,final)

    def test_missing_screenshot_cannot_pass(self):
        result,final,initial,_=self.invoke({'MISSING_SCREEN':'keyboard'})
        self.assertNotEqual(0,result.returncode); self.assertIn('Missing native screenshot: keyboard.png',result.stdout)
        self.assertEqual(initial,final)

    def test_stale_screenshot_cannot_mask_missing_capture(self):
        result,_,_,_=self.invoke({'MISSING_SCREEN':'keyboard'},stale=True)
        self.assertNotEqual(0,result.returncode)

    def test_failed_artifact_pull_cannot_pass(self):
        result,final,initial,_=self.invoke({'FAIL_PULL':'1'})
        self.assertNotEqual(0,result.returncode); self.assertEqual(initial,final)

    def test_ignored_font_setting_blocks_tests(self):
        result,final,initial,args=self.invoke({'IGNORE_FONT':'1'})
        self.assertNotEqual(0,result.returncode); self.assertEqual('',args); self.assertEqual(initial,final)

    def test_unset_preferences_are_deleted_on_restore(self):
        result,final,initial,_=self.invoke(initial={})
        self.assertEqual(0,result.returncode); self.assertEqual(initial,final)

    def test_invalid_scale_does_not_touch_device_or_run_tests(self):
        result,final,initial,args=self.invoke(font='3.0')
        self.assertEqual(2,result.returncode); self.assertEqual('',args); self.assertEqual(initial,final)

if __name__=='__main__': unittest.main()
