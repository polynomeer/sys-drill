export function LoadingState({ label = "불러오는 중...", className = "" }: { label?: string; className?: string }) {
  return (
    <div className={`flex items-center gap-2 text-sm text-foreground-muted ${className}`}>
      <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-border border-t-accent" />
      {label}
    </div>
  );
}
