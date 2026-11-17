#!/usr/bin/env bash
set -euo pipefail
mkdir -p stable-smoke
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell am force-stop com.android.launcher3 || true
adb install signed-release/pdf-focus-reader-1.0.0-x86_64.apk
adb shell am start -W -n com.pdffocus.reader/.MainActivity
adb shell dumpsys activity activities > stable-smoke/activities.txt
grep -E 'com.pdffocus.reader/.*(MainActivity|TutorialActivity)' stable-smoke/activities.txt
for attempt in $(seq 1 45); do
  adb shell uiautomator dump /sdcard/stable-ui.xml
  adb pull /sdcard/stable-ui.xml stable-smoke/ui.xml
  if grep -q 'Quickstep' stable-smoke/ui.xml && grep -q 'Close app' stable-smoke/ui.xml; then
    python3 - <<'PY_UI'
import xml.etree.ElementTree as ET, re, subprocess
for node in ET.parse('stable-smoke/ui.xml').iter('node'):
    if node.get('text') == 'Close app':
        x0,y0,x1,y1=map(int,re.findall(r'\d+',node.get('bounds')))
        subprocess.run(['adb','shell','input','tap',str((x0+x1)//2),str((y0+y1)//2)],check=True)
PY_UI
    adb shell am start -W -n com.pdffocus.reader/.MainActivity
  fi
  grep -q 'Choisir vos blocs' stable-smoke/ui.xml && break
  sleep 1
done
adb shell screencap -p /sdcard/stable-tutorial.png
adb pull /sdcard/stable-tutorial.png stable-smoke/tutorial.png
adb logcat -d > stable-smoke/logcat.txt
grep -q 'Choisir vos blocs' stable-smoke/ui.xml
adb shell am force-stop com.pdffocus.reader
adb shell run-as com.pdffocus.reader ls > stable-smoke/debug-access.txt 2>&1 && exit 1 || grep -q 'not debuggable' stable-smoke/debug-access.txt
adb shell am start -W -n com.pdffocus.reader/.MainActivity
adb logcat -d > stable-smoke/logcat.txt
! grep -q 'FATAL EXCEPTION' stable-smoke/logcat.txt
