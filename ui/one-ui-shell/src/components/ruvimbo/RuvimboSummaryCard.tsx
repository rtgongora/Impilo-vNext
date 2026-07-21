"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";

type Tone = "neutral" | "positive" | "warning" | "danger" | "brand";

const TONE_ACCENT: Record<Tone, string> = {
  neutral: "text-foreground",
  positive: "text-green-700",
  warning: "text-warning-foreground",
  danger: "text-danger",
  brand: "text-violet-700",
};

const TONE_ICON_BG: Record<Tone, string> = {
  neutral: "bg-muted text-muted-foreground",
  positive: "bg-green-50 text-green-700",
  warning: "bg-amber-50 text-warning-foreground",
  danger: "bg-red-50 text-danger",
  brand: "bg-violet-50 text-violet-700",
};

/**
 * A compact dashboard summary card: a headline count/value plus a status line and a
 * "next action" affordance. Cards open the underlying workspace/section rather than
 * being oversized empty containers.
 */
export function RuvimboSummaryCard({
  icon,
  label,
  value,
  hint,
  tone = "neutral",
  href,
  onClick,
  actionLabel,
  isLoading,
}: {
  icon?: ReactNode;
  label: string;
  value: ReactNode;
  hint?: string;
  tone?: Tone;
  href?: string;
  onClick?: () => void;
  actionLabel?: string;
  isLoading?: boolean;
}) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        {icon ? (
          <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${TONE_ICON_BG[tone]}`}>
            {icon}
          </span>
        ) : null}
      </div>
      <div className={`mt-2 text-2xl font-bold ${TONE_ACCENT[tone]}`}>
        {isLoading ? <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-hidden /> : value}
      </div>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
      {href && actionLabel ? (
        <p className="mt-3 text-xs font-medium text-violet-700 group-hover:text-violet-900">{actionLabel} →</p>
      ) : null}
    </>
  );

  const cardCls =
    "group block rounded-xl border border-border bg-card p-4 shadow-sm transition hover:border-violet-300 hover:shadow";

  if (href) {
    return (
      <Link href={href} className={cardCls}>
        {body}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={`${cardCls} w-full text-left`}>
        {body}
      </button>
    );
  }
  return <div className={cardCls}>{body}</div>;
}
