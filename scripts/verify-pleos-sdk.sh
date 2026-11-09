#!/usr/bin/env bash
# Real Pleos Vehicle Service integration test. Requires a running Pleos Connect 2.x AVD.
# Fails if Android is not Automotive or no real SDK callback values are returned.
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
mkdir -p build/pleos-sdk-verification
OUT="build/pleos-sdk-verification"
adb wait-for-device
adb shell pm list features | tee "$OUT/system-features.txt"
if ! grep -q 'android.hardware.type.automotive' "$OUT/system-features.txt"; then
  echo "FAIL: This is not a Pleos-compatible Automotive image."
  exit 2
fi
echo "Vehicle permissions installed on OS:"
adb shell pm list permissions | tee "$OUT/vehicle-permissions.txt" | grep -E 'pleos.car.permission' || true

adb install -r "$APK"
adb logcat -c
adb shell am force-stop com.vpe.hyundai || true
adb shell am start -W -n com.vpe.hyundai/.MainActivity
sleep 3
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb exec-out cat /sdcard/window.xml > "$OUT/ui-before.xml"
python3 - "$OUT/ui-before.xml" <<'PY'
import re,subprocess,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for node in root.iter():
    title=node.attrib.get("text","")
    if any(x in title for x in ("Pleos 차량 연결하기","차량 데이터 새로고침")):
        n=[int(x) for x in re.findall(r'\d+',node.attrib.get("bounds",""))]
        if len(n)==4:
            subprocess.check_call(["adb","shell","input","tap",
                                   str((n[0]+n[2])//2),str((n[1]+n[3])//2)])
            print("Clicked actual Pleos SDK connect control.")
            break
else:
    raise SystemExit("FAIL: Real Pleos connect control not enabled. Inspect VPE permission diagnostics.")
PY
sleep 15
adb logcat -d -s VPE_PLEOS:I > "$OUT/sdk-callbacks.log"
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb exec-out cat /sdcard/window.xml > "$OUT/ui-after.xml"
echo "SDK callback results:"
grep -E 'ODO_|FUEL_|EV_|VIN_|CRN_|SERVICE_|ENVIRONMENT_' "$OUT/sdk-callbacks.log" || true

if ! grep -Eq 'ODO_OK|ODO_UPDATE' "$OUT/sdk-callbacks.log"; then
    echo "FAIL: No real odometer reading; inspect $OUT/sdk-callbacks.log"
    exit 3
fi
if ! grep -Eq 'VIN_OK|CRN_OK' "$OUT/sdk-callbacks.log"; then
    echo "FAIL: No real vehicle identifier (VIN/CRN); cannot generate vehicle-specific persona."
    exit 4
fi
if ! grep -Eq 'FUEL_OK|FUEL_UPDATE|EV_OK|EV_SOC_UPDATE' "$OUT/sdk-callbacks.log"; then
    echo "WARN: Energy percent missing; VPE can still initialize with neutral mood energy."
fi
echo "PASS: Real Vehicle Service delivered odometer + vehicle identity to the VPE SDK adapter."
echo "For live change validation, use Pleos Virtual Console: adb forward tcp:8088 tcp:8088"
echo "Then open http://127.0.0.1:8088/, change driving distance, and verify ODO_UPDATE in logcat."
