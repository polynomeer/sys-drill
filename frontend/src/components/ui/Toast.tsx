"use client";

import { createContext, useCallback, useContext, useState } from "react";

type ToastItem = { id: number; message: string; tone: "info" | "success" | "danger" };
type ShowToast = (message: string, tone?: ToastItem["tone"]) => void;

const ToastContext = createContext<ShowToast>(() => undefined);

const TONE_CLASSES: Record<ToastItem["tone"], string> = {
  info: "border-border",
  success: "border-success/50",
  danger: "border-danger/50",
};

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.9 — short, self-dismissing confirmations
 * ("링크를 복사했습니다", "저장했습니다") instead of each button growing its own
 * inline "복사됨" state. Announced politely to screen readers.
 */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);

  const show = useCallback<ShowToast>((message, tone = "info") => {
    const id = Date.now() + Math.random();
    setItems((current) => [...current.slice(-2), { id, message, tone }]);
    setTimeout(() => setItems((current) => current.filter((t) => t.id !== id)), 2500);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col items-end gap-2">
        {items.map((t) => (
          <div key={t.id} className={`rounded-lg border bg-surface-elevated px-4 py-2 text-sm text-foreground shadow-lg ${TONE_CLASSES[t.tone]}`}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ShowToast {
  return useContext(ToastContext);
}
