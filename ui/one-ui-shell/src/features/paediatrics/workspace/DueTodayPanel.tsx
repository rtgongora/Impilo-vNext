"use client";

import React from "react";
import Link from "next/link";
import type { DueItem, DueUrgency } from "./due-today";

/**
 * "What is due today" — the panel that turns any contact into an opportunity.
 *
 * The urgency of an item is carried by a word as well as a colour: colour alone excludes
 * anyone who cannot distinguish it, and this panel is read under time pressure.
 */
const URGENCY_STYLE: Record<DueUrgency, { label: string; className: string }> = {
  overdue: { label: "Overdue", className: "border-red-500 bg-red-50 text-red-900" },
  due: { label: "Due", className: "border-amber-500 bg-amber-50 text-amber-900" },
  unknown: { label: "Cannot determine", className: "border-slate-400 bg-slate-50 text-slate-800" },
  watch: { label: "Watch", className: "border-slate-300 bg-background text-foreground" },
};

export interface DueTodayPanelProps {
  items: DueItem[];
  className?: string;
}

export function DueTodayPanel({ items, className = "" }: DueTodayPanelProps) {
  return (
    <section
      className={`rounded-lg border border-border bg-background p-4 ${className}`}
      aria-labelledby="due-today-heading"
      data-testid="due-today-panel"
    >
      <h2 id="due-today-heading" className="text-sm font-semibold text-foreground">
        What is due today
      </h2>

      {items.length === 0 ? (
        // Reachable only when every check ran and found nothing — the compute step emits an
        // explicit "cannot determine" item rather than silence whenever it could not tell.
        <p className="mt-2 text-sm text-muted-foreground" data-testid="due-today-empty">
          Nothing outstanding from what the record holds.
        </p>
      ) : (
        <ul className="mt-3 space-y-2">
          {items.map((item) => {
            const style = URGENCY_STYLE[item.urgency];
            const body = (
              <>
                <div className="flex items-center gap-2">
                  <span className="rounded px-1.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide">
                    {style.label}
                  </span>
                  <span className="text-sm font-medium">{item.label}</span>
                </div>
                <p className="mt-1 text-xs">{item.detail}</p>
              </>
            );

            return (
              <li key={item.id} data-testid={`due-item-${item.id}`}>
                {item.href ? (
                  <Link
                    href={item.href}
                    className={`block rounded-md border-l-4 px-3 py-2 transition-colors hover:brightness-95 ${style.className}`}
                  >
                    {body}
                  </Link>
                ) : (
                  <div className={`rounded-md border-l-4 px-3 py-2 ${style.className}`}>{body}</div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
