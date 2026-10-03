"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { type AppThemeId, DEFAULT_APP_THEME, currentAppTheme } from "@/lib/appTheme";
import type { ThemeChoice } from "@/lib/localSession";

function subscribeAppTheme(onChange: () => void): () => void {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observer.disconnect();
}

/** The live app theme — re-renders when ThemeSelect (or anything else) switches `<html data-theme>`. Kept out of appTheme.ts, which the server layout imports. */
export function useAppTheme(): AppThemeId {
  return useSyncExternalStore(subscribeAppTheme, currentAppTheme, () => DEFAULT_APP_THEME);
}

/**
 * A hand-picked display theme (canvas, code editor) that only holds under the
 * app theme it was picked in — switching app themes drops it and brings back
 * `autoId`, the option that follows the app theme. Returns the effective id and a setter
 * that also persists the pick.
 */
export function useThemeChoice(
  autoId: string,
  load: () => ThemeChoice | null,
  save: (choice: ThemeChoice | null) => void,
): { appTheme: AppThemeId; themeId: string; selectTheme: (id: string) => void } {
  const appTheme = useAppTheme();
  const [choice, setChoice] = useState(load);
  // An actual app-theme switch drops the pick for good, so coming back to the
  // earlier app theme also starts from its default. Listening for the switch
  // itself (rather than comparing during render) keeps hydration — which
  // briefly renders the server's default theme — from discarding a valid pick.
  useEffect(
    () =>
      subscribeAppTheme(() => {
        setChoice(null);
        save(null);
      }),
    [save],
  );
  // Still guard by app theme: a pick saved under another theme (another tab, an older visit) doesn't apply here.
  const themeId = choice && choice.appTheme === appTheme ? choice.id : autoId;
  function selectTheme(id: string) {
    const next = id === autoId ? null : { id, appTheme };
    setChoice(next);
    save(next);
  }
  return { appTheme, themeId, selectTheme };
}
