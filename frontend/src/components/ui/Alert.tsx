type Variant = "warning" | "danger";

const VARIANT_CLASSES: Record<Variant, string> = {
  warning: "border-warning/40 bg-warning/10 text-warning",
  danger: "border-danger/40 bg-danger/10 text-danger",
};

export function Alert({ variant = "warning", className = "", children }: { variant?: Variant; className?: string; children: React.ReactNode }) {
  return <div className={`rounded-xl border p-4 text-sm ${VARIANT_CLASSES[variant]} ${className}`}>{children}</div>;
}
