"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, Building2, CircleCheck, MessageSquare, Terminal, type LucideIcon } from "lucide-react";
import { NotificationFeed, NotificationType, getNotifications, markNotificationsSeen } from "@/lib/api";

const ICONS: Record<NotificationType, LucideIcon> = {
  EVALUATION_READY: CircleCheck,
  BUILD_GRADED: Terminal,
  ORGANIZATION_INVITATION: Building2,
  DISCUSSION_MESSAGE: MessageSquare,
};

function timeAgo(iso: string): string {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return "방금";
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  return `${Math.round(hours / 24)}일 전`;
}

/**
 * docs/CODECRAFTERS_BENCHMARK.md §3.9 (PLAN.md Round B16) — the header bell.
 * Refreshes on navigation; opening the list marks everything seen on the
 * server (the badge clears) while the items keep their "new" dot until the
 * list closes, so you can still tell what was new.
 */
export function NotificationBell() {
  const pathname = usePathname();
  const [feed, setFeed] = useState<NotificationFeed | null>(null);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    getNotifications().then(setFeed).catch(() => setFeed(null));
  }, [pathname]);

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (next && feed && feed.unseenCount > 0) {
      try {
        await markNotificationsSeen();
        setFeed({ ...feed, unseenCount: 0 });
      } catch {
        // stays unseen; the next refresh will show the same count
      }
    }
  }

  const unseen = feed?.unseenCount ?? 0;

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={toggle}
        aria-label={unseen > 0 ? `알림 ${unseen}개` : "알림"}
        aria-expanded={open}
        className="relative flex h-8 w-8 items-center justify-center rounded-full text-foreground-muted hover:text-foreground"
      >
        <Bell className="h-4 w-4" aria-hidden strokeWidth={1.75} />
        {unseen > 0 && (
          <span className="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-accent px-1 text-center text-[10px] font-semibold leading-4 text-accent-foreground">
            {unseen > 9 ? "9+" : unseen}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 top-10 z-20 w-80 overflow-hidden rounded-lg border border-border bg-surface shadow-lg">
          <p className="border-b border-border px-3 py-2 text-xs font-semibold text-foreground-muted">알림 · 최근 30일</p>
          {!feed || feed.items.length === 0 ? (
            <p className="px-3 py-6 text-center text-sm text-foreground-muted">새 소식이 없습니다.</p>
          ) : (
            <ul className="max-h-96 overflow-y-auto">
              {feed.items.map((item, i) => {
                const Icon = ICONS[item.type];
                return (
                  <li key={`${item.type}-${item.at}-${i}`}>
                    <Link
                      href={item.href}
                      onClick={() => setOpen(false)}
                      className="flex gap-3 px-3 py-2.5 text-sm hover:bg-surface-elevated"
                    >
                      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-foreground-muted" aria-hidden strokeWidth={1.75} />
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-1.5">
                          {item.unseen && <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-accent" aria-label="새 알림" />}
                          <span className="truncate font-medium">{item.title}</span>
                        </span>
                        {item.body && <span className="block truncate text-xs text-foreground-muted">{item.body}</span>}
                        <span className="text-[11px] text-foreground-muted">{timeAgo(item.at)}</span>
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
