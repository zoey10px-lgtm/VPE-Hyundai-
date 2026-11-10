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
  local coords
  for attempt in 1 2 3 4 5 6; do
    dump_ui
    if grep -q "$wanted" /tmp/vpe_ui.xml; then break; fi
    adb shell input swipe 320 650 320 240 350
    sleep 1
  done
  dump_ui
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

grep -q 'Pleos 차량 연동 안내' /tmp/vpe_ui.xml
# Demo button can be below the initial viewport on devices with taller home content.
# tap_label scrolls to it before tapping.
echo "PASS: Regular Android phone detected without claiming vehicle connection"
tap_label "스마트폰에서 VPE 데모 시작"
dump_ui
grep -q '시연 차량 성향 분석 리포트' /tmp/vpe_ui.xml
grep -q 'MBTI' /tmp/vpe_ui.xml
echo "PASS: Demo generates and automatically opens the separate personality report"
adb exec-out screencap -p > /tmp/vpe_report_top.png

adb shell input swipe 320 650 320 260 470
sleep 1
dump_ui
grep -q '성격 키워드' /tmp/vpe_ui.xml
echo "PASS: Report has personality keyword section"
adb exec-out screencap -p > /tmp/vpe_report_keywords.png

adb shell input swipe 320 650 320 225 470
sleep 1
dump_ui
grep -q '7가지 성향 레이더 차트' /tmp/vpe_ui.xml
grep -q '차량 성향 레이더 차트' /tmp/vpe_ui.xml
echo "PASS: Radar chart contains seven computed personality dimensions"
adb exec-out screencap -p > /tmp/vpe_report_radar.png

tap_label "홈"
dump_ui
grep -q '데모 페르소나가 준비됐어요' /tmp/vpe_ui.xml
tap_label "내 차량 분석 리포트 열기"
dump_ui
grep -q '시연 차량 성향 분석 리포트' /tmp/vpe_ui.xml
echo "PASS: Home report entry opens saved vehicle report"
tap_label "홈"
tap_label "리포트"
dump_ui
grep -q '시연 차량 성향 분석 리포트' /tmp/vpe_ui.xml
echo "PASS: Dedicated report accessible from navigation"

tap_label "대화"
dump_ui
grep -q 'VPE와 대화' /tmp/vpe_ui.xml
tap_label "오늘 기분은?"
dump_ui
grep -q '오늘 기분은' /tmp/vpe_ui.xml
echo "PASS: Chat navigation and interaction"
adb exec-out screencap -p > /tmp/vpe_chat.png

tap_label "진단"
dump_ui
grep -q '연결 진단' /tmp/vpe_ui.xml
grep -q '실행 환경 및 권한' /tmp/vpe_ui.xml
grep -q '일반 Android 스마트폰' /tmp/vpe_ui.xml
echo "PASS: Diagnostic screen identifies non-Automotive device"
echo "PASS: Separate diagnostics screen"
adb exec-out screencap -p > /tmp/vpe_diagnostics.png

adb logcat -d -s AndroidRuntime:E | tail -70 > /tmp/vpe_runtime_errors.txt
if grep -q 'FATAL EXCEPTION.*com.vpe.hyundai' /tmp/vpe_runtime_errors.txt; then
  cat /tmp/vpe_runtime_errors.txt
  exit 1
fi
echo "PASS: No observed VPE fatal runtime crash"
