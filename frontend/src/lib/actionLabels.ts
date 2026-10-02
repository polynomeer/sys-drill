import { ACTIONS_BY_DOMAIN } from "@/app/design/[sessionId]/WargameLive";

/** Every domain's action code → the label the Wargame shows ("INCREASE_DB_POOL" → "DB Pool 증가"). */
const LABELS: Record<string, string> = Object.fromEntries(
  Object.values(ACTIONS_BY_DOMAIN)
    .flat()
    .map((a) => [a.type, a.label]),
);

export function actionLabel(code: string): string {
  return LABELS[code] ?? code;
}

/** Replaces action codes inside free text (timeline anchors like "+2분 14초 INCREASE_DB_POOL"). */
export function withActionLabels(text: string | null): string {
  return (text ?? "").replace(/\b[A-Z][A-Z_]{3,}\b/g, (code) => LABELS[code] ?? code);
}
