/** Drill workspaces (Build `/bridge`, a Design/Incident session) run edge to edge; the header and footer follow so their edges line up with the page. */
export function isFullBleed(pathname: string): boolean {
  return pathname.startsWith("/bridge") || /^\/design\/[^/]+$/.test(pathname);
}
