#!/usr/bin/env bash
# Pleos Connect Virtual Console HTTP API로 주행 한 사이클을 재생한다.
# 출발(D, 60km/h) → 주행거리 +DIST km → 정차 → P단 유지 → 도착 확정(30초 후 ASKING)
#
# 사용: tools/vc-drive.sh [시작 km] [주행 km]
# 전제: Pleos 에뮬레이터 실행 중, adb 연결됨
# 콘솔은 브라우저 세션 heartbeat가 있어야 값 변경을 받으므로(없으면 503 emulator_inactive) 매초 보낸다.
set -euo pipefail

START_KM=${1:-1000}
DIST_KM=${2:-12}
VC=http://127.0.0.1:8088
CLIENT=vc-drive-$$

adb forward tcp:8088 tcp:8088 >/dev/null

heartbeat() { curl -s -m 3 -X POST "$VC/browser/session?client=$CLIENT" -o /dev/null; }
put() {
  heartbeat
  local code
  code=$(curl -s -m 5 -X PUT "$VC/vehicle/$1" -o /dev/null -w "%{http_code}")
  echo "$1 -> $code"
  [ "$code" = 200 ]
}
hold() { for _ in $(seq 1 "$1"); do heartbeat; sleep 1; done; }
trap 'curl -s -m 3 -X POST "$VC/browser/session?client=$CLIENT&closing=1" -o /dev/null' EXIT

put "gear?gear=Park"
put "odometer?traveled_distance_km=$START_KM"; hold 3
put "gear?gear=Drive"
put "controls?speed=60&accelerator=30&brake=0"; hold 5
put "odometer?traveled_distance_km=$((START_KM + DIST_KM))"; hold 3
put "controls?speed=0&accelerator=0&brake=50"; hold 3
put "gear?gear=Park"; hold 35

echo "완료. 상태 전이 확인: adb logcat -d -s SignalCheck:I"
