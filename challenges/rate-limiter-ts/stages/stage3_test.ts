// Stage 3 — concurrency safety.
// 학습 포인트: atomicity — Node는 싱글스레드라 Python의 OS 스레드 경쟁을 그대로
// 옮길 수 없다. 대신 async 호출을 대량으로 동시에 날려(Promise.all) 인터리빙
// 경쟁을 만든다 — rate_limiter.ts가 제공하는 InMemoryStore.incr()는 일부러
// 원자적이지 않으므로(내부에 실제 네트워크 I/O를 흉내낸 await 지점이 있음),
// allow()에 자체 동시성 제어가 없으면 capacity를 넘기게 된다.
import { RateLimiter } from "./rate_limiter.ts";

async function main(): Promise<void> {
  const rl = new RateLimiter(50, 5.0);
  const calls: Promise<boolean>[] = [];
  for (let i = 0; i < 200; i++) calls.push(rl.allow("shared-key"));
  await Promise.all(calls);
  if (rl.metrics.allowed > 50) {
    throw new Error(`concurrent access let ${rl.metrics.allowed} requests through, expected <= 50`);
  }
}

main()
  .then(() => console.log("RESULT:PASS"))
  .catch((e: Error) => {
    console.log(`RESULT:FAIL:${e.message}`);
    process.exit(1);
  });
