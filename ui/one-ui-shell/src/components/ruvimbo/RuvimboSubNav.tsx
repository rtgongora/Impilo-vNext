"use client";

import type { ReactNode } from "react";

export interface RuvimboTab {
  key: string;
  label: string;
  icon?: ReactNode;
  /** Optional count badge (e.g. items needing action). */
  count?: number;
}

/**
 * Horizontal workspace sub-navigation. Desktop shows an underlined tab bar; on narrow
 * screens it scrolls horizontally rather than wrapping into the content. Controlled —
 * the parent owns the active key so tab state can be reflected in the URL if desired.
 */
export function RuvimboSubNav({
  tabs,
  active,
  onChange,
  ariaLabel = "Ruvimbo sections",
}: {
  tabs: RuvimboTab[];
  active: string;
  onChange: (key: string) => void;
  ariaLabel?: string;
}) {
  return (
    <div className="border-b border-border">
      <nav
        className="-mb-px flex gap-1 overflow-x-auto"
        aria-label={ariaLabel}
        role="tablist"
      >
        {tabs.map((tab) => {
          const isActive = tab.key === active;
          return (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => onChange(tab.key)}
              className={`inline-flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium transition ${
                isActive
                  ? "border-violet-600 text-violet-700"
                  : "border-transparent text-muted-foreground hover:border-border hover:text-foreground"
              }`}
            >
              {tab.icon}
              {tab.label}
              {typeof tab.count === "number" && tab.count > 0 ? (
                <span className="ml-0.5 rounded-full bg-violet-100 px-1.5 py-0.5 text-[11px] font-semibold text-violet-700">
                  {tab.count}
                </span>
              ) : null}
            </button>
          );
        })}
      </nav>
    </div>
  );
}
