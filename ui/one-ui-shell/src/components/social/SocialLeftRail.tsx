"use client";

/**
 * SocialLeftRail — Left column for the timeline shell. Provides shortcut
 * navigation between feed scopes, communities, groups, pages, wellness, and
 * learning spaces. Role-agnostic, but composes from real BFF data where
 * available (suggested communities, joined pages, etc.).
 */

import Link from "next/link";
import {
  Bookmark,
  Compass,
  FileEdit,
  Flag,
  Home,
  Hospital,
  Layers,
  LucideIcon,
  Megaphone,
  Newspaper,
  Pin,
  Sparkles,
  Stethoscope,
  Users,
} from "lucide-react";

interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  badge?: string;
}

const PRIMARY: NavItem[] = [
  { label: "Home feed", href: "/social", icon: Home },
  { label: "Communities", href: "/communities", icon: Users },
  { label: "Groups", href: "/groups", icon: Layers },
  { label: "Pages", href: "/pages", icon: Newspaper },
  { label: "Saved", href: "/social/saved", icon: Bookmark },
  { label: "Drafts", href: "/social/drafts", icon: FileEdit },
];

const SPACES: NavItem[] = [
  { label: "Wellness", href: "/wellness", icon: Sparkles },
  { label: "Public Health", href: "/social?scope=public_health", icon: Megaphone },
  { label: "Learning (Fundo)", href: "/social?scope=learning", icon: Compass },
  { label: "Facility feed", href: "/social?scope=facility", icon: Hospital },
  { label: "Clinical tools", href: "/clinical", icon: Stethoscope },
];

const ADMIN: NavItem[] = [
  { label: "Moderation queue", href: "/social/moderation", icon: Flag },
  { label: "Pinned announcements", href: "/social?scope=announcements", icon: Pin },
];

interface Props {
  /** When true, expose moderator/admin shortcuts. */
  showModeration?: boolean;
  /** Currently-active scope (for highlight). */
  activeScope?: string;
}

export function SocialLeftRail({ showModeration = false, activeScope }: Props) {
  return (
    <div className="space-y-4">
      <NavBlock title="Timeline" items={PRIMARY} activeScope={activeScope} />
      <NavBlock title="Spaces" items={SPACES} activeScope={activeScope} />
      {showModeration && <NavBlock title="Admin" items={ADMIN} activeScope={activeScope} />}
    </div>
  );
}

function NavBlock({ title, items, activeScope }: { title: string; items: NavItem[]; activeScope?: string }) {
  return (
    <nav className="impilo-surface-card p-3">
      <p className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wider text-[color:var(--text-muted)]">
        {title}
      </p>
      <ul className="space-y-0.5">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = activeScope && item.href.includes(`scope=${activeScope}`);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                className={`flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition-colors ${
                  isActive
                    ? "bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]"
                    : "text-[color:var(--text-secondary)] hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)]"
                }`}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="truncate">{item.label}</span>
                {item.badge && (
                  <span className="ml-auto rounded-full bg-[color:var(--primary)] px-1.5 py-0.5 text-[10px] font-semibold text-white">
                    {item.badge}
                  </span>
                )}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
