import { API_BASE_URL } from "@/lib/api";

/** Allow-listed on the server (ProductEventService.ALLOWED); anything else is a 400. */
export type ProductEvent =
  | "drill_overview_view"
  | "drill_overview_start"
  | "certifications_view"
  | "organizations_view"
  | "architecture_analysis_view"
  | "report_view"
  | "feedback_concept_click"
  | "observe_tab_map"
  | "observe_tab_metrics"
  | "observe_tab_logs"
  | "observe_tab_changes";

/**
 * docs/CODECRAFTERS_BENCHMARK.md §6 (PLAN.md Round B17) — bump an anonymous
 * daily counter. No token, no identifiers, fire-and-forget: a lost event only
 * makes a metric slightly low, never breaks the page.
 */
export function trackEvent(name: ProductEvent): void {
  try {
    void fetch(`${API_BASE_URL}/events`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
      keepalive: true,
    }).catch(() => undefined);
  } catch {
    // never let measurement break the page
  }
}
