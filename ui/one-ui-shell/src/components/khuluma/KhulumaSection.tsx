"use client";

import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";

/**
 * A Khuluma hub section with built-in loading / error / empty isolation. Each section
 * owns an independent query, so one failing panel (e.g. a notification-service timeout)
 * shows a scoped, retryable notice instead of collapsing the whole hub.
 */
export function KhulumaSection({
  title,
  icon,
  actions,
  isLoading,
  isError,
  onRetry,
  isEmpty,
  emptyState,
  children,
}: {
  title: string;
  icon?: ReactNode;
  actions?: ReactNode;
  isLoading?: boolean;
  isError?: boolean;
  onRetry?: () => void;
  isEmpty?: boolean;
  emptyState?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
      <div className="flex items-center justify-between gap-2 border-b border-border bg-emerald-50/60 px-4 py-3">
        <div className="flex items-center gap-2">
          {icon}
          <h2 className="text-sm font-semibold text-foreground">{title}</h2>
        </div>
        {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
      </div>
      <div className="p-4">
        {isLoading ? (
          <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" aria-hidden /> Loading…
          </div>
        ) : isError ? (
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-red-100 bg-danger-soft px-3 py-2">
            <p className="text-sm text-danger">Could not load this section.</p>
            {onRetry ? (
              <button
                type="button"
                onClick={onRetry}
                className="rounded-md border border-red-200 bg-background px-2.5 py-1 text-xs font-medium text-danger hover:bg-red-50"
              >
                Try again
              </button>
            ) : null}
          </div>
        ) : isEmpty && emptyState ? (
          emptyState
        ) : (
          children
        )}
      </div>
    </section>
  );
}
