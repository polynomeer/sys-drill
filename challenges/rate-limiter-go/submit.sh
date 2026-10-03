#!/usr/bin/env bash
# Submit rate_limiter.go to SysDrill and stream the stage results here (PLAN.md Round B12).
# The same results appear in the web editor's test log (/bridge) automatically.
set -euo pipefail

API_BASE_URL="${SYSDRILL_API_BASE_URL:-http://localhost:8081}"
CHALLENGE_SLUG="rate-limiter-go"
SOURCE_FILE="rate_limiter.go"

if [ -z "${SYSDRILL_TOKEN:-}" ]; then
  echo "SYSDRILL_TOKEN 환경변수를 설정하세요 — /bridge 화면의 \"로컬에서 풀기\"에서 복사할 수 있습니다." >&2
  exit 1
fi

if [ ! -f "$SOURCE_FILE" ]; then
  echo "$SOURCE_FILE 파일을 찾을 수 없습니다. challenges/rate-limiter-go 디렉터리에서 실행하세요." >&2
  exit 1
fi

COMMIT_REF=$(git rev-parse --short HEAD 2>/dev/null || date +%s)

python3 - "$API_BASE_URL" "$CHALLENGE_SLUG" "$SYSDRILL_TOKEN" "$SOURCE_FILE" "$COMMIT_REF" << 'PYEOF'
import json
import sys
import time
import urllib.error
import urllib.request

api_base_url, slug, token, source_file, commit_ref = sys.argv[1:6]
use_color = sys.stdout.isatty()

def color(text, code):
    return f"\033[{code}m{text}\033[0m" if use_color else text

def call(method, path, body=None):
    req = urllib.request.Request(
        f"{api_base_url}{path}",
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
        method=method,
    )
    with urllib.request.urlopen(req) as res:
        return json.loads(res.read())

with open(source_file, "r", encoding="utf-8") as f:
    source_code = f.read()

try:
    submission = call("POST", f"/build-challenges/{slug}/submissions", {"sourceCode": source_code, "commitRef": commit_ref})
except urllib.error.HTTPError as e:
    detail = e.read().decode("utf-8", errors="replace")
    hint = " — 토큰이 만료됐을 수 있습니다. /bridge에서 다시 복사하세요." if e.code == 401 else ""
    print(f"제출 실패: HTTP {e.code} {detail}{hint}", file=sys.stderr)
    sys.exit(1)

print(color(f"[runner]    제출 {submission['id'][:8]} — 샌드박스(네트워크 차단)에서 테스트를 실행합니다", "2"))

printed = set()
deadline = time.time() + 600
while time.time() < deadline:
    for stage in sorted(submission["stages"], key=lambda s: s["stageOrder"]):
        order, status = stage["stageOrder"], stage.get("status")
        if not status or order in printed:
            continue
        printed.add(order)
        seconds = f" ({stage['durationMs'] / 1000:.1f}s)" if stage.get("durationMs") is not None else ""
        if status == "PASSED":
            print(color(f"[stage-{order}]   Stage #{order}: {stage['title']} — 통과{seconds}", "32"))
        else:
            print(color(f"[stage-{order}]   Stage #{order}: {stage['title']} — 실패{seconds}", "31"))
            for line in (stage.get("output") or stage.get("feedback") or "").splitlines():
                print(f"[your_code] {line}")
    if submission["status"] in ("COMPLETED", "ERROR"):
        break
    time.sleep(1)
    submission = call("GET", f"/build-submissions/{submission['id']}")

if submission["status"] == "ERROR":
    print("채점 중 오류가 났습니다. 잠시 후 다시 제출하세요.", file=sys.stderr)
    sys.exit(1)
print(f"\n{submission.get('score') or 0} / {submission['totalStages']} 통과 — 자세한 지시문과 다음 단계는 /bridge 화면에서 확인하세요.")
PYEOF
