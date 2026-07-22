"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutDashboard, Inbox, Phone, Video, Bell, Hash, LifeBuoy, Radio } from "lucide-react";

/**
 * Khuluma workspace sub-navigation, shared across all /khuluma/* pages. Impilo Live is a
 * deliberate link-OUT to /live (opens in the same tab) — Khuluma coordinates, Impilo Live
 * hosts the live room; Khuluma never wraps the sovereign live stage.
 */
const TABS: { href: string; label: string; icon: React.ReactNode; external?: boolean }[] = [
  { href: "/khuluma", label: "Home", icon: <LayoutDashboard className="h-4 w-4" /> },
  { href: "/khuluma/inbox", label: "Inbox", icon: <Inbox className="h-4 w-4" /> },
  { href: "/khuluma/calls", label: "Calls", icon: <Phone className="h-4 w-4" /> },
  { href: "/khuluma/meetings", label: "Meetings", icon: <Video className="h-4 w-4" /> },
  { href: "/live", label: "Impilo Live ↗", icon: <Radio className="h-4 w-4" />, external: true },
  { href: "/khuluma/updates", label: "Updates", icon: <Bell className="h-4 w-4" /> },
  { href: "/khuluma/channels", label: "Channels", icon: <Hash className="h-4 w-4" /> },
  { href: "/khuluma/feedback", label: "Feedback", icon: <LifeBuoy className="h-4 w-4" /> },
];

export function KhulumaSubNav() {
  const pathname = usePathname();
  return (
    <div className="border-b border-border">
      <nav className="-mb-px flex gap-1 overflow-x-auto" aria-label="Khuluma workspaces">
        {TABS.map((t) => {
          const active = !t.external && (t.href === "/khuluma" ? pathname === "/khuluma" : pathname?.startsWith(t.href));
          return (
            <Link
              key={t.href}
              href={t.href}
              className={`inline-flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium transition ${
                active
                  ? "border-emerald-600 text-emerald-700"
                  : "border-transparent text-muted-foreground hover:border-border hover:text-foreground"
              }`}
            >
              {t.icon}
              {t.label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
