"use client";

import Link from "next/link";
import type { ReactNode } from "react";

export interface KhulumaActionItem {
  title: string;
  description?: string;
  href?: string;
  onClick?: () => void;
  icon?: ReactNode;
  access?: "open";
}

/**
 * The Khuluma quick-actions grid — "what would you like to do?" as real launchers, so the
 * hub reads as a place to initiate communication, not a channel viewer.
 */
export function KhulumaActionGrid({ title = "Quick actions", actions }: { title?: string; actions: KhulumaActionItem[] }) {
  return (
    <section aria-labelledby="khuluma-actions">
      <h2 id="khuluma-actions" className="text-sm font-semibold text-foreground">
        {title}
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {actions.map((a) => {
          const inner = (
            <>
              <div className="flex items-center gap-2">
                {a.icon ? <span className="text-emerald-700">{a.icon}</span> : null}
                <span className="text-sm font-semibold text-foreground group-hover:text-emerald-800">{a.title}</span>
              </div>
              {a.description ? <p className="mt-1 text-xs text-muted-foreground">{a.description}</p> : null}
            </>
          );
          const cls =
            "group block rounded-xl border border-border bg-card p-4 shadow-sm transition hover:border-emerald-300 hover:shadow";
          if (!a.href && !a.onClick) {
            return <div key={a.title} className={cls} aria-disabled>{inner}</div>;
          }
          return a.href ? (
            <Link key={a.title} href={a.href} className={cls}>{inner}</Link>
          ) : (
            <button key={a.title} type="button" onClick={a.onClick} className={`${cls} w-full text-left`}>{inner}</button>
          );
        })}
      </div>
    </section>
  );
}
