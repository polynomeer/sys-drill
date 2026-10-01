"use client";

import { useEffect, useState } from "react";
import { UserPreferences, getMyPreferences, setMyPreferences } from "@/lib/api";
import { GOAL_OPTIONS, LANGUAGE_OPTIONS } from "@/lib/preferences";
import { PreferenceChips } from "@/components/PreferenceChips";
import { Card } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";

/** docs/CODECRAFTERS_BENCHMARK.md §3.4 — edit the onboarding answers later. Saves on each change. */
export function PreferencesCard() {
  const [prefs, setPrefs] = useState<UserPreferences | null>(null);
  const [saving, setSaving] = useState(false);
  const showToast = useToast();

  useEffect(() => {
    getMyPreferences().then(setPrefs).catch(() => setPrefs(null));
  }, []);

  if (!prefs) return null;

  async function save(next: UserPreferences) {
    setPrefs(next);
    setSaving(true);
    try {
      setPrefs(await setMyPreferences(next));
      showToast("훈련 설정을 저장했습니다", "success");
    } catch {
      showToast("훈련 설정을 저장하지 못했습니다", "danger");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Card as="section">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-foreground-muted">훈련 설정</h2>
        {saving && <span className="text-xs text-foreground-muted">저장 중...</span>}
      </div>
      <div className="flex flex-col gap-4">
        <PreferenceChips label="훈련 목표" options={GOAL_OPTIONS} value={prefs.trainingGoal} onChange={(v) => save({ ...prefs, trainingGoal: v })} />
        <PreferenceChips
          label="Build 과제 언어"
          options={LANGUAGE_OPTIONS}
          value={prefs.preferredLanguage}
          onChange={(v) => save({ ...prefs, preferredLanguage: v })}
        />
      </div>
    </Card>
  );
}
