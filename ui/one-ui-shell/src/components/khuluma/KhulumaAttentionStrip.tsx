"use client";

import Link from "next/link";
import type { ReactNode } from "react";

export interface AttentionChip {
  label: string;
  value: number;
  href: string;
  icon?: ReactNode;
  tone?: "neutral" | "warning" | "danger";
}

const TONE: Record<NonNullable<AttentionChip["tone"]>, string> = {
  neutral: "text-foreground",
  warning: "text-warning-foreground",
  danger: "text-danger",
};

/**
 * The compact "what needs my attention" strip at the top of the Khuluma hub — answers
 * "who is trying to reach me / what is happening next" in one glance. Chips render only
 * with real counts from live queries; a chip whose source hasn't loaded is simply omitted.
 */
export function KhulumaAttentionStrip({ chips }: { chips: AttentionChip[] }) {
  const shown = chips.filter((c) => c.value > 0);
  if (shown.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card px-4 py-3 text-sm text-muted-foreground shadow-sm">
        You&rsquo;re all caught up — no unread messages, pending actions or waiting calls.
      </div>
    );
  }
  return (
    <div className="flex flex-wrap gap-2">
      {shown.map((c) => (
        <Link
          key={c.label}
          href={c.href}
          className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-3 py-1.5 text-sm shadow-sm transition hover:border-emerald-300"
        >
          {c.icon ? <span className="text-emerald-700">{c.icon}</span> : null}
          <span className={`font-semibold ${TONE[c.tone ?? "neutral"]}`}>{c.value}</span>
          <span className="text-muted-foreground">{c.label}</span>
        </Link>
      ))}
    </div>
  );
}
