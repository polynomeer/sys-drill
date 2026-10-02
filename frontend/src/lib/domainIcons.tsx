import { Boxes, CalendarCheck, CreditCard, Bell, Receipt, Rocket, ShoppingBag, Sparkles, Ticket, type LucideIcon } from "lucide-react";

/** docs/CODECRAFTERS_BENCHMARK.md §3.9 — one monochrome icon per simulation
 * domain (CodeCrafters gives every challenge its own mark), shared by
 * catalog cards and overview headers. Community scenarios use free-text
 * domains, so anything unmapped falls back to a neutral icon. */
const DOMAIN_ICONS: Record<string, LucideIcon> = {
  coupon: Ticket,
  notification: Bell,
  "product-browsing": ShoppingBag,
  payment: CreditCard,
  reservation: CalendarCheck,
  "batch-settlement": Receipt,
  autoscaling: Sparkles,
  deployment: Rocket,
};

export function DomainIcon({ domain, className = "h-5 w-5" }: { domain: string; className?: string }) {
  const Icon = DOMAIN_ICONS[domain] ?? Boxes;
  return <Icon className={className} aria-hidden strokeWidth={1.75} />;
}
