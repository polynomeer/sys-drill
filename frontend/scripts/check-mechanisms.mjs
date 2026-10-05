// docs/LEARNING_DEEPENING_PLAN.md L17 (PLAN.md Round E41, ADR-0055) — the mechanism widgets say what
// the Build challenges test. For each widget scenario: (1) the widget's simulation core produces the
// scenario's expected outcome, and (2) the values come from the Build stage tests — the strings that
// pin them are in the Python and TypeScript stage files.
//
// Run: npm run check:mechanisms   (node --experimental-strip-types, no other dependencies)
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  BOUNDARY_BURST,
  RATE_LIMITER_SCENARIOS,
  STAGE1,
  STAGE2,
  STAGE3,
  STAGE4,
  STAGE6,
  maxAllowedWithin,
  raceAllowed,
  sharedStoreAllowed,
  simulate,
} from "../src/lib/mechanisms/rateLimiter.ts";

const repo = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const problems = [];
const expect = (ok, message) => {
  if (!ok) problems.push(message);
};

// (1) The core against each scenario's expected outcome.
{
  const d = simulate("fixed-window", STAGE1.capacity, STAGE1.windowSeconds, STAGE1.requests);
  expect(d.filter((x) => x.key === "user-a" && x.allowed).length === STAGE1.expected.allowedA, "stage1: user-a allowed count");
  expect(d.find((x) => x.key === "user-b")?.allowed === STAGE1.expected.userBAllowed, "stage1: user-b allowed");
}
{
  const d = simulate("fixed-window", STAGE2.capacity, STAGE2.windowSeconds, STAGE2.requests);
  expect(JSON.stringify(d.map((x) => x.allowed)) === JSON.stringify(STAGE2.expected), `stage2: got ${d.map((x) => x.allowed)}`);
}
{
  const atomic = raceAllowed(STAGE3.workers, STAGE3.callsPerWorker, STAGE3.capacity, true);
  const racy = raceAllowed(STAGE3.workers, STAGE3.callsPerWorker, STAGE3.capacity, false);
  expect(atomic.allowed === STAGE3.expected.atomicAllowed, `stage3: atomic allowed ${atomic.allowed}`);
  expect(racy.allowed > STAGE3.capacity, `stage3: the racy version should over-admit, got ${racy.allowed}`);
}
expect(sharedStoreAllowed(STAGE4.instances, STAGE4.requests, STAGE4.capacity, true) === STAGE4.expected.sharedAllowed, "stage4: shared store total");
expect(sharedStoreAllowed(STAGE4.instances, STAGE4.requests, STAGE4.capacity, false) === STAGE4.expected.separateAllowed, "stage4: separate stores total");
{
  const d = simulate("fixed-window", STAGE6.capacity, STAGE6.windowSeconds, STAGE6.requests);
  const allowed = d.filter((x) => x.allowed).length;
  const rejected = d.length - allowed;
  expect(allowed === STAGE6.expected.allowed && rejected === STAGE6.expected.rejected, `stage6: ${allowed}/${rejected}`);
  expect(Math.abs(rejected / d.length - STAGE6.expected.rejectRate) < 0.01, "stage6: reject rate");
}
for (const [algorithm, max] of Object.entries(BOUNDARY_BURST.expected)) {
  const d = simulate(algorithm, BOUNDARY_BURST.capacity, BOUNDARY_BURST.windowSeconds, BOUNDARY_BURST.requests);
  const got = maxAllowedWithin(d, 1);
  expect(got === max, `boundary-burst: ${algorithm} allows ${got} within 1 s, expected ${max}`);
}

// (2) The values are the Build stages' own.
let pinned = 0;
for (const scenario of RATE_LIMITER_SCENARIOS) {
  for (const file of scenario.build?.files ?? []) {
    const text = readFileSync(resolve(repo, file.path), "utf8");
    for (const needle of file.contains) {
      pinned++;
      expect(text.includes(needle), `${scenario.id}: ${file.path} no longer contains ${JSON.stringify(needle)}`);
    }
  }
}

if (problems.length > 0) {
  console.error(`check:mechanisms — ${problems.length} problem(s):\n- ${problems.join("\n- ")}`);
  process.exit(1);
}
console.log(`check:mechanisms — OK (${RATE_LIMITER_SCENARIOS.length} rate limiter scenarios, ${pinned} values pinned to Build stage tests)`);
