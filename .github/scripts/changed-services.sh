#!/usr/bin/env bash
# 변경 파일을 보고 테스트/배포 대상을 판별한다.
# 사용법: changed-services.sh <base> <head> <test|deploy>
# 결과: $GITHUB_OUTPUT 에 services=<JSON 배열>
# 모놀리스(루트 src)는 사용 중단 예정이라 판별 대상에서 제외
set -euo pipefail

BASE="$1"; HEAD="$2"; MODE="$3"
OUT="${GITHUB_OUTPUT:-/dev/stdout}"   # 로컬 실행 시 화면 출력

# 1) 후보 목록: 디렉터리에서 수집 (서비스 목록을 따로 관리하지 않음)
CANDIDATES=""
for dir in services/*/; do
  s=$(basename "$dir")
  if [ "$MODE" = "deploy" ] && [ ! -f "k8s/app/$s.yaml" ]; then
    continue                      # 배포는 매니페스트가 있는 서비스만
  fi
  CANDIDATES="$CANDIDATES $s"
done

# 2) 변경 파일. 비교 기준이 없으면(force push, before=000… 등) 안전하게 전체
if CHANGED=$(git diff --name-only "$BASE" "$HEAD" 2>/dev/null); then
  FORCE_ALL=false
else
  echo "::warning::$BASE 와 비교할 수 없어 전체를 대상으로 합니다"
  FORCE_ALL=true
fi

BUILD_RE='^(gradle/|settings\.gradle\.kts$|build\.gradle\.kts$|gradlew$)'
ALL_SERVICES_RE="^(libs/|\.github/scripts/changed-services\.sh$)|$BUILD_RE"

# 3) 서비스 대상
if [ "$FORCE_ALL" = true ] || grep -qE "$ALL_SERVICES_RE" <<< "$CHANGED"; then
  TARGETS="$CANDIDATES"
else
  TARGETS=""
  for s in $CANDIDATES; do
    PATTERN="^services/$s/"
    [ "$MODE" = "deploy" ] && PATTERN="^(services/$s/|k8s/app/$s\.yaml$)"
    if grep -qE "$PATTERN" <<< "$CHANGED"; then
      TARGETS="$TARGETS $s"
    fi
  done
fi

JSON=$(printf '%s\n' $TARGETS | jq -R -s -c 'split("\n") | map(select(. != ""))')
echo "services=$JSON" >> "$OUT"
echo "[$MODE] 대상: $JSON"
