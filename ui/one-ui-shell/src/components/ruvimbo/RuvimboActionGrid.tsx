"use client";

import Link from "next/link";
import type { ReactNode } from "react";

export interface RuvimboActionItem {
  title: string;
  description?: string;
  /** Route destination. Omit when using onClick for an in-page action (e.g. a tab jump). */
  href?: string;
  onClick?: () => void;
  icon?: ReactNode;
  /** Availability signal, mirroring the public gateway grammar. */
  access?: "open" | "sign-in";
}

const ACCESS_BADGE: Record<NonNullable<RuvimboActionItem["access"]>, { label: string; cls: string }> = {
  open: { label: "Ready", cls: "bg-emerald-50 text-emerald-700" },
  "sign-in": { label: "Needs sign-in", cls: "bg-slate-100 text-slate-600" },
};

/**
 * The "What would you like to do?" action grid — exposes the real breadth of a Ruvimbo
 * workspace as a grid of task launchers, so the surface reads as a place to act rather
 * than a stack of database tables. Every tile routes to a real destination.
 */
export function RuvimboActionGrid({
  title = "What would you like to do?",
  actions,
}: {
  title?: string;
  actions: RuvimboActionItem[];
}) {
  return (
    <section aria-labelledby="ruvimbo-actions-heading">
      <h2 id="ruvimbo-actions-heading" className="text-sm font-semibold text-foreground">
        {title}
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {actions.map((a) => {
          const badge = a.access ? ACCESS_BADGE[a.access] : null;
          const inner: ReactNode = (
            <>
              <div className="flex items-start justify-between gap-2">
                <div className="flex items-center gap-2">
                  {a.icon ? <span className="text-violet-700">{a.icon}</span> : null}
                  <span className="text-sm font-semibold text-foreground group-hover:text-violet-800">
                    {a.title}
                  </span>
                </div>
                {badge ? (
                  <span className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ${badge.cls}`}>
                    {badge.label}
                  </span>
                ) : null}
              </div>
              {a.description ? (
                <p className="mt-1 text-xs text-muted-foreground">{a.description}</p>
              ) : null}
            </>
          );
          const cls =
            "group block rounded-xl border border-border bg-card p-4 shadow-sm transition hover:border-violet-300 hover:shadow";
          if (!a.href && !a.onClick) {
            return (
              <div key={a.title} className={cls} aria-disabled>
                {inner}
              </div>
            );
          }
          if (a.href) {
            return (
              <Link key={a.title} href={a.href} className={cls}>
                {inner}
              </Link>
            );
          }
          return (
            <button key={a.title} type="button" onClick={a.onClick} className={`${cls} w-full text-left`}>
              {inner}
            </button>
          );
        })}
      </div>
    </section>
  );
}
