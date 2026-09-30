// SysDrill 앱 자체(공유 인프라)의 실제 트래픽 용량을 재는 k6 스크립트.
// backend/src/main/resources/realinfra/coupon-load.js의 env-driven
// constant-arrival-rate 패턴을 그대로 따르되, 학습 세션 전용 샌드박스
// 엔드포인트가 아니라 앱의 실제 공유 엔드포인트(/scenarios, /sessions,
// /sessions/{id}/submissions)를 때린다. scripts/benchmark-traffic.sh가
// 이 스크립트를 RATE만 바꿔가며 여러 번 호출한다(한 번 호출 = 한 단계).
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.TARGET_URL;
const JWT_TOKEN = __ENV.JWT_TOKEN;
const RATE = Number(__ENV.RATE);
const DURATION = __ENV.DURATION || '30s';

// 세션 상태 전이/평가 큐 경합에서 나오는 409는 "시스템이 깨졌다"가 아니라
// "설계대로 막았다"는 뜻 — coupon-load.js와 같은 이유로 실패로 안 친다.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    app_traffic: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      // coupon-load.js와 같은 공식 — 쓰기 경로(세션 생성+제출)는 읽기보다
      // 훨씬 느리므로(실 DB 커밋 2번 + Redis 큐 push) RATE 대비 넉넉한
      // VU 여유가 없으면 k6 자신이 dropped_iterations로 병목이 된다.
      preAllocatedVUs: Math.min(Math.max(RATE, 2), 300),
      maxVUs: Math.min(Math.max(RATE * 3, 2), 500),
    },
  },
};

const authHeaders = { headers: { Authorization: `Bearer ${JWT_TOKEN}`, 'Content-Type': 'application/json' } };

// 스크립트당 한 번만 실행(VU마다가 아님) — 공식 시나리오 목록만 있으면
// 충분하고, 매 VU가 반복하기엔 낭비.
export function setup() {
  const res = http.get(`${BASE_URL}/scenarios`, { timeout: '5s' });
  if (res.status !== 200) {
    throw new Error(`GET /scenarios failed during setup: ${res.status} ${res.body}`);
  }
  const scenarioIds = JSON.parse(res.body).map((s) => s.id);
  if (scenarioIds.length === 0) {
    throw new Error('No scenarios returned — is the DB seeded?');
  }
  return { scenarioIds };
}

// 60/40 읽기/쓰기 혼합 — coupon-load.js의 READ_RATIO/WRITE_RATIO 관례를
// 그대로 가져오되, "공식 시나리오 목록 조회"(비인증, 순수 읽기)와
// "세션 생성 + 제출"(인증, 실 DB 쓰기 2번 + Redis 큐 push)로 바꿔치기했다.
export default function (data) {
  if (Math.random() < 0.6) {
    const res = http.get(`${BASE_URL}/scenarios`, { timeout: '5s' });
    check(res, { 'GET /scenarios handled': (r) => r.status < 500 });
    return;
  }

  const scenarioId = data.scenarioIds[Math.floor(Math.random() * data.scenarioIds.length)];
  const sessionRes = http.post(`${BASE_URL}/sessions`, JSON.stringify({ scenarioId }), { ...authHeaders, timeout: '5s' });
  const created = check(sessionRes, { 'POST /sessions handled': (r) => r.status < 500 });
  if (!created || sessionRes.status !== 201) return; // 세션이 안 만들어졌으면 제출도 의미 없음

  const sessionId = JSON.parse(sessionRes.body).id;
  const submitRes = http.post(
    `${BASE_URL}/sessions/${sessionId}/submissions`,
    JSON.stringify({ rawText: 'Load Balancer -> API -> Cache -> DB. 벤치마크용 더미 설계안.' }),
    { ...authHeaders, timeout: '5s' }
  );
  check(submitRes, { 'POST /submissions handled': (r) => r.status < 500 });
}
