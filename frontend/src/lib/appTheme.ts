/**
 * docs/UX_STRATEGY.md "앱 테마" — a per-viewer display preference, like the
 * canvas theme: colors, radii, display font and panel decoration only. Layout,
 * wording and features are identical across themes. Not stored on the server.
 */
export const APP_THEMES = [
  { id: "navy", label: "기본" },
  { id: "quest", label: "Quest" },
  { id: "arcade", label: "Arcade" },
  { id: "rpg", label: "RPG" },
  { id: "tactical", label: "Tactical" },
] as const;

export type AppThemeId = (typeof APP_THEMES)[number]["id"];

export const DEFAULT_APP_THEME: AppThemeId = "navy";
export const APP_THEME_KEY = "sysdrill:app-theme";

export function isAppTheme(value: string | null | undefined): value is AppThemeId {
  return APP_THEMES.some((t) => t.id === value);
}

export function currentAppTheme(): AppThemeId {
  const value = document.documentElement.dataset.theme;
  return isAppTheme(value) ? value : DEFAULT_APP_THEME;
}

export function applyAppTheme(themeId: AppThemeId): void {
  document.documentElement.dataset.theme = themeId;
  try {
    window.localStorage.setItem(APP_THEME_KEY, themeId);
  } catch {
    // storage unavailable (private mode) — the theme still applies for this page view
  }
}

/** Runs in <head> before first paint so a saved theme never flashes the default one. */
export const APP_THEME_BOOT_SCRIPT = `try{var t=localStorage.getItem(${JSON.stringify(APP_THEME_KEY)});if(${JSON.stringify(
  APP_THEMES.map((t) => t.id),
)}.indexOf(t)>=0)document.documentElement.dataset.theme=t}catch(e){}`;
