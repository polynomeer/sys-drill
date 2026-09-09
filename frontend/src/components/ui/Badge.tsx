type Variant = "neutral" | "success" | "warning" | "danger" | "accent";

const VARIANT_CLASSES: Record<Variant, string> = {
  neutral: "bg-surface-elevated text-foreground-muted",
  success: "bg-success/15 text-success",
  warning: "bg-warning/15 text-warning",
  danger: "bg-danger/15 text-danger",
  accent: "bg-accent/15 text-accent",
};

/**
 * SysDrill_UIUX_Design_Plan.docx §7 — status/difficulty pills. Also doubles
 * as the Drill-type badge: Design=accent(blue), Build=success(green),
 * Incident=danger(red), reusing these same three variants rather than
 * inventing a parallel color set.
 */
export function Badge({ variant = "neutral", className = "", children }: { variant?: Variant; className?: string; children: React.ReactNode }) {
  return <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${VARIANT_CLASSES[variant]} ${className}`}>{children}</span>;
}
