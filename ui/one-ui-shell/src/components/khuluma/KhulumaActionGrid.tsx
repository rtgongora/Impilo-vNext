"use client";

import Link from "next/link";
import type { ReactNode } from "react";

export interface KhulumaActionItem {
  title: string;
  description?: string;
  href?: string;
  onClick?: () => void;
  icon?: ReactNode;
  /** Honest availability signal. */
  access?: "open" | "coming";
}

/**
 * The Khuluma quick-actions grid — "what would you like to do?" as real launchers, so the
 * hub reads as a place to initiate communication, not a channel viewer. Unbuilt destinations
 * are marked "Coming soon" honestly rather than hidden or faked.
 */
export function KhulumaActionGrid({ title = "Quick actions", actions }: { title?: string; actions: KhulumaActionItem[] }) {
  return (
    <section aria-labelledby="khuluma-actions">
      <h2 id="khuluma-actions" className="text-sm font-semibold text-foreground">
        {title}
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {actions.map((a) => {
          const coming = a.access === "coming";
          const inner = (
            <>
              <div className="flex items-center gap-2">
                {a.icon ? <span className="text-emerald-700">{a.icon}</span> : null}
                <span className="text-sm font-semibold text-foreground group-hover:text-emerald-800">{a.title}</span>
                {coming ? (
                  <span className="ml-auto rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">Coming soon</span>
                ) : null}
              </div>
              {a.description ? <p className="mt-1 text-xs text-muted-foreground">{a.description}</p> : null}
            </>
          );
          const cls = `group block rounded-xl border border-border bg-card p-4 shadow-sm transition ${
            coming ? "opacity-70" : "hover:border-emerald-300 hover:shadow"
          }`;
          if (coming || (!a.href && !a.onClick)) {
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
