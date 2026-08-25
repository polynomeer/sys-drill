#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${SYSDRILL_API_BASE_URL:-http://localhost:8081}"
CHALLENGE_SLUG="distributed-lock"
SOURCE_FILE="distributed_lock.py"

if [ -z "${SYSDRILL_USER_ID:-}" ]; then
  echo "SYSDRILL_USER_ID 환경변수를 설정하세요 (온보딩에서 발급받은 사용자 ID)." >&2
  exit 1
fi

if [ ! -f "$SOURCE_FILE" ]; then
  echo "$SOURCE_FILE 파일을 찾을 수 없습니다. challenges/distributed-lock 디렉터리에서 실행하세요." >&2
  exit 1
fi

COMMIT_REF=$(git rev-parse --short HEAD 2>/dev/null || date +%s)

python3 - "$API_BASE_URL" "$CHALLENGE_SLUG" "$SYSDRILL_USER_ID" "$SOURCE_FILE" "$COMMIT_REF" << 'PYEOF'
import json
import sys
import urllib.error
import urllib.request

api_base_url, slug, user_id, source_file, commit_ref = sys.argv[1:6]
with open(source_file, "r", encoding="utf-8") as f:
    source_code = f.read()

payload = json.dumps({
    "userId": user_id,
    "sourceCode": source_code,
    "commitRef": commit_ref,
}).encode("utf-8")

req = urllib.request.Request(
    f"{api_base_url}/build-challenges/{slug}/submissions",
    data=payload,
    headers={"Content-Type": "application/json"},
    method="POST",
)

try:
    with urllib.request.urlopen(req) as res:
        body = json.loads(res.read())
except urllib.error.HTTPError as e:
    print(f"제출 실패: HTTP {e.code} {e.read().decode('utf-8', errors='replace')}", file=sys.stderr)
    sys.exit(1)

print(f"제출 완료: submissionId={body['id']} (status={body['status']})")
print(f"결과 확인: {api_base_url}/build-submissions/{body['id']}")
PYEOF
