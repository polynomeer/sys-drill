"use client";

import { useEffect, useState } from "react";
import { APP_THEMES, type AppThemeId, DEFAULT_APP_THEME, applyAppTheme, currentAppTheme, isAppTheme } from "@/lib/appTheme";

/** docs/UX_STRATEGY.md "앱 테마" — the footer's theme picker. */
export function ThemeSelect() {
  const [theme, setTheme] = useState<AppThemeId>(DEFAULT_APP_THEME);

  useEffect(() => {
    // The boot script already applied the saved theme; mirror it into the control.
    setTheme(currentAppTheme());
  }, []);

  return (
    <label className="flex items-center gap-1.5">
      테마
      <select
        value={theme}
        onChange={(e) => {
          if (!isAppTheme(e.target.value)) return;
          setTheme(e.target.value);
          applyAppTheme(e.target.value);
        }}
        className="rounded border border-border bg-surface px-1.5 py-0.5 text-xs text-foreground focus:border-accent focus:outline-none"
      >
        {APP_THEMES.map((t) => (
          <option key={t.id} value={t.id}>
            {t.label}
          </option>
        ))}
      </select>
    </label>
  );
}
