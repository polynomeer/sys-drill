// Stage 5 — fail-open vs fail-closed.
// 학습 포인트: 가용성과 보호의 trade-off (store가 죽었을 때 통과시킬지 막을지는
// 설계 선택이지 정답이 없다 — 여기서는 두 모드 모두 올바르게 구현하는지 본다).
import { FaultyStore, RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const openLimiter = new RateLimiter(1, 1.0, new FaultyStore(), "open");
  if ((await openLimiter.allow("k")) !== true) {
    throw new Error("failMode=open should admit requests when the store is unavailable");
  }

  const closedLimiter = new RateLimiter(1, 1.0, new FaultyStore(), "closed");
  if ((await closedLimiter.allow("k")) !== false) {
    throw new Error("failMode=closed should reject requests when the store is unavailable");
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
