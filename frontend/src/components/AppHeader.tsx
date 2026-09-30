"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Bell, Menu } from "lucide-react";
import { clearStoredUser, getStoredNickname, getStoredToken } from "@/lib/localSession";

/** SysDrill_UIUX_Design_Plan.docx §4 — Home/Drills/Learning/Community IA.
 * Learning/Community route to minimal placeholder pages (no backend content
 * model exists yet — see PLAN.md UI/UX 리뉴얼 Round 1). URL paths themselves
 * stay on the existing routes (/dashboard, /marketplace) so bookmarks and
 * deep links (invitations, verification) don't break; only the nav labels
 * and in-app terminology change to the new IA. */
const NAV_LINKS = [
  { href: "/dashboard", label: "Home" },
  { href: "/marketplace", label: "Drills" },
  { href: "/learning", label: "Learning" },
  { href: "/community", label: "Community" },
];

/** docs/CODECRAFTERS_BENCHMARK.md §3.9 — pages that existed but had no
 * entry point anywhere in the app. They live in the account menu rather
 * than the top nav: each is a secondary destination, not a daily loop. */
const ACCOUNT_LINKS = [
  { href: "/profile", label: "프로필" },
  { href: "/certifications", label: "인증" },
  { href: "/organizations", label: "조직" },
  { href: "/architecture-analysis", label: "아키텍처 분석" },
];

function isActive(pathname: string, href: string): boolean {
  // Drill overview pages belong to the Drills tab.
  if (href === "/marketplace") return pathname.startsWith("/marketplace") || pathname.startsWith("/drills");
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const [loggedIn, setLoggedIn] = useState(false);
  const [nickname, setNickname] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [searchValue, setSearchValue] = useState("");
  const profileRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setLoggedIn(!!getStoredToken());
    setNickname(getStoredNickname());
    setMenuOpen(false);
    setProfileOpen(false);
  }, [pathname]);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  function handleLogout() {
    clearStoredUser();
    router.replace("/login");
  }

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!searchValue.trim()) return;
    router.push(`/marketplace?q=${encodeURIComponent(searchValue.trim())}`);
  }

  return (
    <header className="border-b border-border bg-background">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-6 py-3">
        <Link href={loggedIn ? "/dashboard" : "/"} className="flex shrink-0 items-center gap-2 font-semibold">
          <svg viewBox="0 0 32 32" width="22" height="22" className="rounded" aria-hidden>
            <rect width="32" height="32" rx="7" fill="#2f80ff" />
            <path
              d="M21 11.5c-1-1.4-2.8-2.2-5-2.2-3 0-4.8 1.3-4.8 3.2 0 2 1.9 2.6 4.5 3.1 3.4 0.7 6.3 1.5 6.3 4.7 0 3-2.6 4.9-6.4 4.9-2.9 0-5.1-1-6.6-2.8"
              stroke="#f5f7fa"
              strokeWidth="2.6"
              fill="none"
              strokeLinecap="round"
            />
          </svg>
          SysDrill
        </Link>

        {loggedIn && (
          <>
            {/* Desktop nav — hidden below md, each link keeps to one line regardless of container width. */}
            <nav className="hidden items-center gap-5 text-sm text-foreground-muted md:flex">
              {NAV_LINKS.map((link) => {
                const active = isActive(pathname, link.href);
                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    aria-current={active ? "page" : undefined}
                    className={`whitespace-nowrap hover:text-foreground ${active ? "font-medium text-foreground" : ""}`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </nav>

            <form onSubmit={handleSearchSubmit} className="hidden min-w-0 flex-1 md:block">
              <input
                value={searchValue}
                onChange={(e) => setSearchValue(e.target.value)}
                placeholder="시나리오, 기술 스택, 키워드 검색..."
                className="w-full max-w-xs rounded-lg border border-border bg-surface px-3 py-1.5 text-sm text-foreground placeholder:text-foreground-muted focus:border-accent focus:outline-none"
              />
            </form>

            <div className="hidden items-center gap-3 md:flex">
              <span className="text-foreground-muted" title="알림 (준비 중)">
                <Bell className="h-4 w-4" aria-hidden strokeWidth={1.75} />
              </span>
              <div ref={profileRef} className="relative">
                <button
                  onClick={() => setProfileOpen((open) => !open)}
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-accent/15 text-sm font-medium text-accent"
                  aria-label="프로필 메뉴"
                  aria-expanded={profileOpen}
                >
                  {nickname ? nickname.slice(0, 1).toUpperCase() : "?"}
                </button>
                {profileOpen && (
                  <div className="absolute right-0 top-10 z-10 flex w-40 flex-col gap-1 rounded-lg border border-border bg-surface p-2 text-sm shadow-lg">
                    {nickname && <span className="px-2 py-1 text-foreground-muted">{nickname}</span>}
                    {ACCOUNT_LINKS.map((link) => (
                      <Link key={link.href} href={link.href} className="rounded px-2 py-1 hover:bg-surface-elevated">
                        {link.label}
                      </Link>
                    ))}
                    <button onClick={handleLogout} className="rounded px-2 py-1 text-left hover:bg-surface-elevated">
                      로그아웃
                    </button>
                  </div>
                )}
              </div>
            </div>

            <button
              onClick={() => setMenuOpen((open) => !open)}
              className="rounded-lg border border-border p-1.5 md:hidden"
              aria-label="메뉴 열기"
              aria-expanded={menuOpen}
            >
              <Menu className="h-4 w-4" aria-hidden />
            </button>
          </>
        )}
      </div>

      {loggedIn && menuOpen && (
        <nav className="flex flex-col gap-1 border-t border-border px-6 py-3 text-sm md:hidden">
          {NAV_LINKS.map((link) => {
            const active = isActive(pathname, link.href);
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={active ? "page" : undefined}
                className={`py-1.5 ${active ? "font-medium text-foreground" : "text-foreground-muted"}`}
              >
                {link.label}
              </Link>
            );
          })}
          <div className="my-1 border-t border-border" />
          {nickname && <span className="py-1.5 text-foreground-muted">{nickname}</span>}
          {ACCOUNT_LINKS.map((link) => (
            <Link key={link.href} href={link.href} className="py-1.5 text-foreground-muted">
              {link.label}
            </Link>
          ))}
          <button onClick={handleLogout} className="py-1.5 text-left underline">
            로그아웃
          </button>
        </nav>
      )}
    </header>
  );
}
