"use client";

/**
 * FeedScopeTabs — Horizontal pill tabs that switch between feed scopes.
 */

import type { FeedScope } from "@/hooks/queries/useSocial";

interface Tab {
  id: FeedScope;
  label: string;
  description?: string;
}

const ALL_TABS: Tab[] = [
  { id: "home", label: "Home" },
  { id: "following", label: "Following" },
  { id: "wellness", label: "Wellness" },
  { id: "learning", label: "Learning" },
  { id: "public_health", label: "Public health" },
  { id: "facility", label: "Facility" },
  { id: "announcements", label: "Announcements" },
  { id: "saved", label: "Saved" },
  { id: "my_posts", label: "My posts" },
  { id: "drafts", label: "Drafts" },
];

interface Props {
  active: FeedScope;
  onChange: (scope: FeedScope) => void;
  /** Override which tabs are shown. */
  scopes?: FeedScope[];
}

export function FeedScopeTabs({ active, onChange, scopes }: Props) {
  const tabs = scopes ? ALL_TABS.filter((t) => scopes.includes(t.id)) : ALL_TABS;
  return (
    <div className="impilo-surface-card flex gap-1 overflow-x-auto p-1.5">
      {tabs.map((t) => (
        <button
          key={t.id}
          type="button"
          onClick={() => onChange(t.id)}
          className={`whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
            active === t.id
              ? "bg-[color:var(--primary)] text-white"
              : "text-[color:var(--text-secondary)] hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)]"
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
