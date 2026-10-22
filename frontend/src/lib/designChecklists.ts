"use client";

import { useEffect, useState } from "react";
import { type ChecklistItem, getDesignChecklists } from "@/lib/api";

/**
 * Each domain's design checklist, served from the design guides (backend `GET /learning/guides/checklists`).
 * It used to be a frontend constant copied into the guides; now the guides are the one source.
 * Fetched once per page load and shared by every caller; `null` while loading, `{}` if it failed.
 */
let request: Promise<Record<string, ChecklistItem[]>> | null = null;

export function useDesignChecklists(): Record<string, ChecklistItem[]> | null {
  const [checklists, setChecklists] = useState<Record<string, ChecklistItem[]> | null>(null);
  useEffect(() => {
    let alive = true;
    request ??= getDesignChecklists().catch((e) => {
      request = null; // let the next mount retry
      throw e;
    });
    request.then((c) => alive && setChecklists(c)).catch(() => alive && setChecklists({}));
    return () => {
      alive = false;
    };
  }, []);
  return checklists;
}

/** A checklist item's `where` as the guide page's anchor id (`step-N` as is, `requirements` → `section-requirements`). */
export const guideAnchor = (where: string) => (where.startsWith("step-") ? where : `section-${where}`);
