/** SysDrill_UIUX_Design_Plan.docx §7 — Dark Surface + 1px border, hover only nudges border/background. */
export function Card({
  className = "",
  children,
  as: Component = "div",
}: {
  className?: string;
  children: React.ReactNode;
  as?: "div" | "section" | "li";
}) {
  return (
    <Component
      className={`rounded-xl border border-border bg-surface p-4 transition-colors hover:border-accent/30 hover:bg-surface-elevated ${className}`}
    >
      {children}
    </Component>
  );
}
