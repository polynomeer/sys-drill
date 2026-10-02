import { ACTIONS_BY_DOMAIN } from "@/app/design/[sessionId]/WargameLive";

/**
 * Every domain's action code → the label the Wargame shows ("INCREASE_DB_POOL" → "DB Pool 증가").
 * Built on first use, not at import: WargameLive itself imports components that use these helpers
 * (RunbookPanel), and an eager table would read ACTIONS_BY_DOMAIN before that module finished loading.
 */
let labels: Record<string, string> | null = null;
function table(): Record<string, string> {
  labels ??= Object.fromEntries(
    Object.values(ACTIONS_BY_DOMAIN)
      .flat()
      .map((a) => [a.type, a.label]),
  );
  return labels;
}

export function actionLabel(code: string): string {
  return table()[code] ?? code;
}

/** Replaces action codes inside free text (timeline anchors like "+2분 14초 INCREASE_DB_POOL"). */
export function withActionLabels(text: string | null): string {
  return (text ?? "").replace(/\b[A-Z][A-Z_]{3,}\b/g, (code) => table()[code] ?? code);
}
