#!/usr/bin/env bash
# 에뮬레이터 다운로드 폴더에 내보낸 운행기록부 CSV를 PC로 가져온다.
# 기록은 차량 프로필(안드로이드 사용자)마다 따로라, 지금 화면에 켜진 프로필의 파일을 가져온다.
# 그 프로필의 /storage/emulated/<번호>는 adb로 바로 못 읽으므로 MediaStore를 거쳐 복사한다.
#
# 사용: tools/pull-logbook.sh [YYYY-MM] [저장 폴더]
#   tools/pull-logbook.sh                → 이번 달, 바탕화면
#   tools/pull-logbook.sh 2026-11 ./out
#   PROFILE_USER=0 tools/pull-logbook.sh  → 다른 차량 프로필(사용자 번호) 지정
set -euo pipefail
export MSYS_NO_PATHCONV=1

MONTH=${1:-$(date +%Y-%m)}
OUT_DIR=${2:-"$HOME/Desktop"}
NAME="운행기록부_${MONTH}.csv"
USER_ID=${PROFILE_USER:-$(adb shell am get-current-user | tr -d '')}

ID=$(adb shell content query --user "$USER_ID" --uri content://media/external/downloads --projection _id:_display_name \
  | grep -E "_display_name=운행기록부_${MONTH}( \([0-9]+\))?\.csv(,|$)" | sed -E 's/.*_id=([0-9]+).*/\1/' | sort -n | tail -1 || true)
# 예전 버전은 다시 내보낼 때 "(1)" 사본을 만들었으므로, 같은 달 파일 중 가장 최근 것을 가져온다
if [ -z "$ID" ]; then
  echo "차량 프로필 ${USER_ID}에 ${NAME} 이 없습니다. 운행기록부 화면에서 CSV 내보내기를 먼저 누르세요." >&2
  exit 1
fi

adb shell "content read --user $USER_ID --uri content://media/external/downloads/${ID} > /data/local/tmp/logbook.csv"
mkdir -p "$OUT_DIR"
LOCAL="$OUT_DIR/$NAME"
command -v cygpath >/dev/null && LOCAL=$(cygpath -m "$LOCAL")  # Windows adb는 C:/ 경로만 안다
adb pull /data/local/tmp/logbook.csv "$LOCAL" >/dev/null
adb shell rm -f /data/local/tmp/logbook.csv
echo "저장: $OUT_DIR/$NAME (차량 프로필 $USER_ID)"
