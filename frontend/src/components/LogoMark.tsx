/**
 * The SysDrill mark (logo concept L1 "Loop"): the Train → Break → Fix → Repeat
 * loop with one piece knocked out of it. Drawn in currentColor so it follows
 * whichever app theme's accent it's placed in. src/app/icon.svg is the same
 * geometry on a fixed tile for the browser tab.
 */
export function LogoMark({ size = 24, className = "" }: { size?: number; className?: string }) {
  return (
    <svg viewBox="0 0 64 64" width={size} height={size} className={className} aria-hidden>
      <path d="M44.86 16.68A20 20 0 1 1 25.16 13.21" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" />
      <rect x="32.2" y="4.4" width="8" height="8" rx="1.5" fill="currentColor" transform="rotate(18 36.2 8.4)" />
    </svg>
  );
}
