import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.TARGET_URL;
const SESSION_ID = __ENV.SESSION_ID;
const RATE = Number(__ENV.RATE);
const DURATION = __ENV.DURATION || '3s';

// 409 (sold out) and 429 (rate limited) are expected outcomes under load, not
// system errors — without this, k6's http_req_failed would conflate "the
// design is working as intended" with "the system broke".
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409, 429));

export const options = {
  scenarios: {
    coupon_traffic: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      // PLAN.md step 23 — a Toxiproxy-injected latency floor (~300ms) means each
      // iteration now takes much longer than pre-toxic requests did, so VUs need
      // more headroom per unit of arrival rate than a low-latency system would
      // (in-flight iterations ~= rate * avg latency) — otherwise k6 itself
      // becomes the bottleneck (dropped_iterations) rather than the app.
      preAllocatedVUs: Math.min(Math.max(RATE, 2), 300),
      maxVUs: Math.min(Math.max(RATE * 3, 2), 500),
    },
  },
};

// 70/30 read/write mix, mirroring RuleBasedSimulationEngine's Coupon.READ_RATIO/WRITE_RATIO.
export default function () {
  const base = `${BASE_URL}/sessions/${SESSION_ID}/simulation/realinfra/coupon`;
  if (Math.random() < 0.7) {
    const res = http.get(`${base}/remaining`, { timeout: '5s' });
    check(res, { handled: (r) => r.status < 500 });
  } else {
    const res = http.post(`${base}/claim`, null, { timeout: '5s' });
    check(res, { handled: (r) => r.status < 500 });
  }
}
