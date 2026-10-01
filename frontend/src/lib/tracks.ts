import type { ScenarioSummary } from "@/lib/api";
import { DOMAIN_TITLES } from "@/lib/designGuidance";
import { TIER_ORDER } from "@/lib/drillPrereq";

/** docs/CODECRAFTERS_BENCHMARK.md §3.7 — CodeCrafters groups by language;
 * SysDrill groups by simulation domain. One track per official domain, in
 * the same order as DOMAIN_TITLES. */
export const TRACK_DOMAINS = Object.keys(DOMAIN_TITLES);

/** A domain's Drills: official first, then community ones using the same domain label, easiest first. */
export function trackDrills(scenarios: ScenarioSummary[], domain: string): ScenarioSummary[] {
  const tier = (d: string | null) => {
    const i = d ? TIER_ORDER.indexOf(d.toUpperCase()) : -1;
    return i === -1 ? TIER_ORDER.length : i;
  };
  return scenarios
    .filter((s) => s.domain === domain && !s.organizationId)
    .sort(
      (a, b) =>
        Number(!!a.creatorNickname) - Number(!!b.creatorNickname) || tier(a.difficulty) - tier(b.difficulty),
    );
}
