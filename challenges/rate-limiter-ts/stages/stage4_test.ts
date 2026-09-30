// Stage 4 — shared ("distributed") store.
// 학습 포인트: 네트워크·Redis 의존성 (store를 공유하지 않으면 인스턴스마다
// capacity가 따로 놀아서, 총 허용량이 의도한 것보다 훨씬 커진다).
import { InMemoryStore, RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const sharedStore = new InMemoryStore();
  const instanceA = new RateLimiter(5, 5.0, sharedStore);
  const instanceB = new RateLimiter(5, 5.0, sharedStore);
  let totalAllowed = 0;
  for (let i = 0; i < 10; i++) {
    const limiter = i % 2 === 0 ? instanceA : instanceB;
    if (await limiter.allow("shared-key")) totalAllowed++;
  }
  if (totalAllowed !== 5) {
    throw new Error(`two instances sharing a store should still cap at 5 total, got ${totalAllowed}`);
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
