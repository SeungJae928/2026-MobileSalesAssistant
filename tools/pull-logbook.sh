#!/usr/bin/env bash
# 에뮬레이터 다운로드 폴더에 내보낸 운행기록부 CSV를 PC로 가져온다.
# 앱이 user 10에서 돌아 /storage/emulated/10은 adb로 바로 못 읽으므로 MediaStore를 거쳐 복사한다.
#
# 사용: tools/pull-logbook.sh [YYYY-MM] [저장 폴더]
#   tools/pull-logbook.sh                → 이번 달, 바탕화면
#   tools/pull-logbook.sh 2026-11 ./out
set -euo pipefail
export MSYS_NO_PATHCONV=1

MONTH=${1:-$(date +%Y-%m)}
OUT_DIR=${2:-"$HOME/Desktop"}
NAME="운행기록부_${MONTH}.csv"

ID=$(adb shell content query --user 10 --uri content://media/external/downloads --projection _id:_display_name \
  | grep "_display_name=${NAME}" | sed -E 's/.*_id=([0-9]+).*/\1/' | tail -1)
if [ -z "$ID" ]; then
  echo "에뮬레이터에 ${NAME} 이 없습니다. 운행기록부 화면에서 CSV 내보내기를 먼저 누르세요." >&2
  exit 1
fi

adb shell "content read --user 10 --uri content://media/external/downloads/${ID} > /data/local/tmp/logbook.csv"
mkdir -p "$OUT_DIR"
LOCAL="$OUT_DIR/$NAME"
command -v cygpath >/dev/null && LOCAL=$(cygpath -m "$LOCAL")  # Windows adb는 C:/ 경로만 안다
adb pull /data/local/tmp/logbook.csv "$LOCAL" >/dev/null
adb shell rm -f /data/local/tmp/logbook.csv
echo "저장: $OUT_DIR/$NAME"
