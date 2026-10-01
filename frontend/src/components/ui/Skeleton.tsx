/** docs/CODECRAFTERS_BENCHMARK.md §3.9 — loading placeholder shaped like the content it stands in for. */
export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`animate-pulse rounded-xl bg-surface-elevated ${className}`} />;
}

/** A grid of card-shaped skeletons — catalog, tracks and Home lists use this while loading. */
export function CardGridSkeleton({ count = 4, className = "grid gap-4 md:grid-cols-2" }: { count?: number; className?: string }) {
  return (
    <div className={className} role="status" aria-label="불러오는 중">
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className="h-32" />
      ))}
    </div>
  );
}
