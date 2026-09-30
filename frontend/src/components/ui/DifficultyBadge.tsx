const LEVELS: Record<string, { label: string; bars: number }> = {
  EASY: { label: "EASY", bars: 1 },
  MEDIUM: { label: "MEDIUM", bars: 2 },
  HARD: { label: "HARD", bars: 3 },
};

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.9 — uppercase label + 3-bar signal, in
 * the accent color only. Difficulty is intensity, not status, so it never
 * borrows success/warning/danger (those stay reserved for pass/warn/fail).
 * Community scenarios carry free-text difficulty; those render as the plain
 * label without bars.
 */
export function DifficultyBadge({ difficulty }: { difficulty: string | null | undefined }) {
  if (!difficulty) return null;
  const level = LEVELS[difficulty.toUpperCase()];
  return (
    <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold tracking-wide text-accent">
      {level?.label ?? difficulty}
      {level && (
        <span className="flex items-end gap-[2px]" aria-hidden>
          {[1, 2, 3].map((bar) => (
            <span
              key={bar}
              className={`w-[3px] rounded-sm ${bar <= level.bars ? "bg-accent" : "bg-accent/25"}`}
              style={{ height: 4 + bar * 3 }}
            />
          ))}
        </span>
      )}
    </span>
  );
}
