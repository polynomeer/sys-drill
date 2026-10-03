"use client";

import { useSyncExternalStore } from "react";
import { type AppThemeId, DEFAULT_APP_THEME, currentAppTheme } from "@/lib/appTheme";

function subscribeAppTheme(onChange: () => void): () => void {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observer.disconnect();
}

/** The live app theme — re-renders when ThemeSelect (or anything else) switches `<html data-theme>`. Kept out of appTheme.ts, which the server layout imports. */
export function useAppTheme(): AppThemeId {
  return useSyncExternalStore(subscribeAppTheme, currentAppTheme, () => DEFAULT_APP_THEME);
}
