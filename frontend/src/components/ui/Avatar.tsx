/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.9 — nickname initial on a hue derived
 * from the nickname, so the same person looks the same everywhere without
 * storing or uploading images.
 */
function hueOf(name: string): number {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.codePointAt(0)!) >>> 0;
  return hash % 360;
}

export function Avatar({ name, size = 28, className = "" }: { name: string | null | undefined; size?: number; className?: string }) {
  const label = name?.trim() || "?";
  const hue = hueOf(label);
  return (
    <span
      aria-hidden
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-medium ${className}`}
      style={{
        width: size,
        height: size,
        fontSize: Math.round(size * 0.45),
        background: `hsl(${hue} 55% 30% / 0.55)`,
        color: `hsl(${hue} 80% 85%)`,
      }}
    >
      {label.slice(0, 1).toUpperCase()}
    </span>
  );
}
