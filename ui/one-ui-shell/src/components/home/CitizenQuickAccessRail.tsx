"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import {
  Activity,
  ArrowRight,
  Award,
  Calendar,
  Droplet,
  FileText,
  Globe,
  Heart,
  PanelLeftClose,
  PanelLeftOpen,
  Pill,
  QrCode,
  Shield,
  ShoppingCart,
  Stethoscope,
  User,
  Users,
} from "lucide-react";

interface QuickAccessItem {
  href: string;
  label: string;
  icon: LucideIcon;
  badge?: string | null;
}

const MY_HEALTH_LINKS: QuickAccessItem[] = [
  { href: "/home/profile", label: "Profile", icon: User },
  { href: "/home/medications", label: "Medications", icon: Pill },
  { href: "/home/documents", label: "Documents", icon: FileText },
  { href: "/home/bookings", label: "My Bookings", icon: Calendar },
  { href: "/home/appointments", label: "My Appointments", icon: Calendar },
  { href: "/madi/donor", label: "Blood donation", icon: Droplet },
  { href: "/monitoring", label: "Monitoring", icon: Activity },
];

const MY_CARE_LINKS: QuickAccessItem[] = [
  { href: "/home/care-team", label: "Care Team", icon: Users },
  { href: "/coverage", label: "Coverage", icon: Shield },
  { href: "/home/referrals", label: "Referrals", icon: ArrowRight },
];

const EXPLORE_LINKS: QuickAccessItem[] = [
  { href: "/wellness", label: "Wellness", icon: Heart },
  { href: "/discover", label: "Find services", icon: Globe },
  { href: "/marketplace", label: "Marketplace", icon: ShoppingCart },
  { href: "/caregiving", label: "Caregiving", icon: Users },
  { href: "/wallet", label: "My Wallet", icon: Award },
];

// RJ-2: self-service provider access for a Health-ID person (Provider ID is secondary).
const PROVIDER_ACCESS_LINKS: QuickAccessItem[] = [
  { href: "/citizen/provider-claim", label: "Request Provider Access", icon: Stethoscope },
  { href: "/citizen/wallet/trust", label: "Trust Profile", icon: Shield },
];

interface CitizenQuickAccessRailProps {
  className?: string;
  collapsible?: boolean;
}

function matchesPath(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`);
}

function QuickAccessSection({
  title,
  items,
  collapsed,
}: {
  title: string;
  items: QuickAccessItem[];
  collapsed: boolean;
}) {
  const pathname = usePathname();

  if (collapsed) {
    return (
      <nav className="space-y-1" aria-label={title}>
        {items.map((item) => {
          const Icon = item.icon;
          const active = matchesPath(pathname, item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              title={item.label}
              className={[
                "flex h-9 w-9 items-center justify-center rounded-lg transition-colors",
                active ? "bg-primary-soft text-primary-hover" : "text-muted-foreground hover:bg-neutral-100 hover:text-foreground",
              ].join(" ")}
            >
              <Icon className="h-4 w-4" />
            </Link>
          );
        })}
      </nav>
    );
  }

  return (
    <nav className="space-y-0.5" aria-label={title}>
      <p className="mb-1 px-2 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">{title}</p>
      {items.map((item) => {
        const Icon = item.icon;
        const active = matchesPath(pathname, item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={[
              "flex items-center justify-between rounded-lg px-2 py-2 text-sm transition-colors",
              active ? "bg-primary-soft text-primary-hover" : "text-foreground hover:bg-neutral-100",
            ].join(" ")}
          >
            <span className="flex items-center gap-2.5">
              <Icon className="h-4 w-4 text-muted-foreground" />
              {item.label}
            </span>
            {item.badge ? (
              <span className="rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium text-warning-foreground">
                {item.badge}
              </span>
            ) : null}
          </Link>
        );
      })}
    </nav>
  );
}

export function CitizenQuickAccessRail({ className = "", collapsible = false }: CitizenQuickAccessRailProps) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside
      className={[
        "space-y-4 transition-[width] duration-200",
        collapsed ? "w-11" : "w-full lg:w-[220px]",
        className,
      ].join(" ")}
      aria-label="Home quick access"
    >
      {collapsible ? (
        <button
          type="button"
          onClick={() => setCollapsed((current) => !current)}
          aria-label={collapsed ? "Open quick access sidebar" : "Close quick access sidebar"}
          aria-pressed={!collapsed}
          className="flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-card text-muted-foreground transition hover:bg-neutral-100 hover:text-foreground"
        >
          {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
        </button>
      ) : null}

      <Link
        href="/citizen/health-id/qr"
        className={[
          "flex items-center gap-3 rounded-xl border border-primary/25 bg-gradient-to-br from-primary-soft to-impilo-100 p-3 transition-colors hover:border-impilo-300",
          collapsed ? "h-11 w-11 justify-center p-0" : "",
        ].join(" ")}
        title="My Health ID"
      >
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary">
          <QrCode className="h-5 w-5 text-white" />
        </span>
        {!collapsed ? (
          <span>
            <span className="block text-sm font-semibold text-primary-hover">My Health ID</span>
            <span className="block text-[11px] text-primary">Tap to show QR</span>
          </span>
        ) : null}
      </Link>

      <QuickAccessSection title="My Health" items={MY_HEALTH_LINKS} collapsed={collapsed} />
      <hr className="border-border" />
      <QuickAccessSection title="My Care" items={MY_CARE_LINKS} collapsed={collapsed} />
      <hr className="border-border" />
      <QuickAccessSection title="Explore" items={EXPLORE_LINKS} collapsed={collapsed} />
      <hr className="border-border" />
      <QuickAccessSection title="Provider access" items={PROVIDER_ACCESS_LINKS} collapsed={collapsed} />
    </aside>
  );
}
