#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$APK"
adb shell am start -W -n com.vpe.hyundai/.MainActivity
sleep 5
if ! adb shell pidof com.vpe.hyundai | grep -q '[0-9]'; then
  echo "ERROR: VPE process did not start"
  exit 1
fi

dump_ui() {
  adb shell uiautomator dump /sdcard/vpe_ui.xml >/dev/null
  adb exec-out cat /sdcard/vpe_ui.xml > /tmp/vpe_ui.xml
}

tap_label() {
  local wanted="$1"
  dump_ui
  local coords
  coords=$(python3 - "$wanted" <<'PY'
import sys, re, xml.etree.ElementTree as ET
wanted=sys.argv[1]
root=ET.parse('/tmp/vpe_ui.xml').getroot()
for node in root.iter():
    txt=node.attrib.get('text','')
    if wanted in txt:
        bounds=node.attrib.get('bounds','')
        vals=[int(x) for x in re.findall(r'\d+',bounds)]
        if len(vals)==4:
            print((vals[0]+vals[2])//2,(vals[1]+vals[3])//2)
            sys.exit(0)
print(f'Element not visible: {wanted}',file=sys.stderr)
sys.exit(1)
PY
  )
  adb shell input tap $coords
  sleep 1
}

dump_ui
grep -q '차가, 나를 닮아가다' /tmp/vpe_ui.xml
echo "PASS: Home loaded"

# A waveform frame must visibly change over time.
adb exec-out screencap -p > /tmp/vpe_frame_1.png
sleep 1
adb exec-out screencap -p > /tmp/vpe_frame_2.png
if cmp -s /tmp/vpe_frame_1.png /tmp/vpe_frame_2.png; then
  echo "ERROR: Animated home rendered identical frames"
  exit 1
fi
echo "PASS: Animated home changed pixels"

tap_label "시연용 데모 체험"
dump_ui
grep -q '데모 페르소나가 준비됐어요' /tmp/vpe_ui.xml
echo "PASS: Demo mode produced visible persona"

tap_label "대화"
dump_ui
grep -q 'VPE와 대화' /tmp/vpe_ui.xml
tap_label "오늘 기분은?"
dump_ui
grep -q '오늘 기분은' /tmp/vpe_ui.xml
echo "PASS: Chat navigation and interaction"

tap_label "진단"
dump_ui
grep -q '연결 진단' /tmp/vpe_ui.xml
echo "PASS: Separate diagnostics screen"

adb logcat -d -s AndroidRuntime:E | tail -70 > /tmp/vpe_runtime_errors.txt
if grep -q 'FATAL EXCEPTION.*com.vpe.hyundai' /tmp/vpe_runtime_errors.txt; then
  cat /tmp/vpe_runtime_errors.txt
  exit 1
fi
echo "PASS: No observed VPE fatal runtime crash"
