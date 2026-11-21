#!/usr/bin/env bash
# GitHub-hosted Android emulator. Tests assistant only; NO user's game account.
set -Eeuo pipefail
APK="${1:-cloud-artifacts/app-debug.apk}"
OUT="${GITHUB_WORKSPACE:-.}/cloud-android-report"
mkdir -p "$OUT"
save_debug(){
  set +e
  adb exec-out screencap -p > "$OUT/final-screen.png"
  adb shell uiautomator dump /sdcard/benben-ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/benben-ui.xml > "$OUT/final-ui.xml"
  adb logcat -d -s AndroidRuntime:E > "$OUT/crashes.txt"
}
trap save_debug EXIT
test -s "$APK"
adb wait-for-device
adb shell wm size 720x1280
adb shell wm density 320
adb install -r "$APK"
adb shell am start -W -n com.xnjtg.benben/.MainActivity | tee "$OUT/launch.txt"
sleep 5
adb shell pidof com.xnjtg.benben > "$OUT/pid.txt"
test -s "$OUT/pid.txt"
adb shell uiautomator dump /sdcard/benben-ui.xml >/dev/null
adb exec-out cat /sdcard/benben-ui.xml > "$OUT/home-ui.xml"
python3 - "$OUT/home-ui.xml" "$OUT" <<'PY'
import pathlib,re,sys,xml.etree.ElementTree as ET
doc=ET.fromstring(pathlib.Path(sys.argv[1]).read_text(encoding="utf8"))
nodes=list(doc.iter("node"))
text=[n.attrib.get("text","") for n in nodes]
for label in ["奔奔王国助手","采集资源","Root 权限检测"]:
    assert any(label in t for t in text),f"Missing assistant UI: {label}"
spinner=next((n for n in nodes if n.attrib.get("class")=="android.widget.Spinner"),None)
assert spinner is not None,"Resource Spinner not found"
m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",spinner.attrib["bounds"])
assert m,"Spinner bounds missing"
pathlib.Path(sys.argv[2],"spinner-point.txt").write_text(
  f"{(int(m[1])+int(m[3]))//2} {(int(m[2])+int(m[4]))//2}")
print("MainActivity and Chinese controls accessible")
PY
# Popup-window UIAutomator dumps are unreliable on headless Android.
# Resource selection is now asserted INSIDE Android using ActivityScenario.
# Here we verify the installed APK renders 石材 visibly in its selector.
python3 - "$OUT/home-ui.xml" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
nodes=list(ET.fromstring(pathlib.Path(sys.argv[1]).read_text()).iter("node"))
assert any(n.attrib.get("text")=="石材" for n in nodes), \
    "Resource Spinner exists but its selected 石材 text is not visible"
print("Installed APK resource picker visible. On-device ActivityScenario tests selection.")
PY
adb exec-out screencap -p > "$OUT/screenshot.png"
python3 - "$OUT/screenshot.png" <<'PY'
import pathlib,struct,sys
b=pathlib.Path(sys.argv[1]).read_bytes()
assert len(b)>10000 and b[:8]==b'\x89PNG\r\n\x1a\n'
w,h=struct.unpack(">II",b[16:24])
assert (w,h)==(720,1280),f"wrong screenshot size {w}x{h}"
print("720x1280 emulator screenshot verified")
PY
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes.txt"
if grep -q 'FATAL EXCEPTION' "$OUT/crashes.txt";then
  echo "AndroidRuntime crash detected";exit 1
fi
printf '%s\n' 'CLOUD ANDROID SMOKE PASSED' 'APK installed, launched, resource visible, screenshot valid; selection tested via ActivityScenario.' 'No game installed: real gameplay is NOT tested.' | tee "$OUT/summary.txt"
