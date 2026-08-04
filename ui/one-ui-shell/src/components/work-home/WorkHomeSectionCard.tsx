"use client";

/**
 * Renders one Work Home section. Items with href are links into the workflow the BFF
 * named — a title-only preview is not Work Home.
 */

import Link from "next/link";
import { AlertCircle, Loader2, RefreshCw } from "lucide-react";
import { useState } from "react";
import type { WorkHomeItem, WorkHomeSection } from "@/hooks/queries/useWorkHome";
import { WORK_HOME_BUCKET_LABELS } from "@/lib/work-home/section-registry";
import { workHomeSectionMeta } from "@/lib/work-home/section-registry";

interface WorkHomeSectionCardProps {
  section: WorkHomeSection;
  onRetry?: (sectionId: string) => Promise<void>;
}

function formatDue(dueAt: string | null | undefined): string | null {
  if (!dueAt) return null;
  const d = new Date(dueAt);
  if (Number.isNaN(d.getTime())) return dueAt;
  return d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function ItemRow({ item }: { item: WorkHomeItem }) {
  const due = formatDue(item.due_at);
  const body = (
    <>
      <span className="min-w-0 flex-1">
        <span className="block text-foreground">{item.title ?? item.id}</span>
        {item.description && (
          <span className="mt-0.5 block text-xs text-muted-foreground">{item.description}</span>
        )}
        {due && <span className="mt-0.5 block text-xs text-muted-foreground">Due {due}</span>}
      </span>
      <span className="flex shrink-0 flex-col items-end gap-0.5 text-xs text-muted-foreground">
        {item.priority && <span>{item.priority}</span>}
        {item.status && <span>{item.status}</span>}
      </span>
    </>
  );

  if (item.href) {
    return (
      <li>
        <Link
          href={item.href}
          className="flex items-start justify-between gap-2 rounded-md px-1 py-1 text-sm hover:bg-background"
        >
          {body}
        </Link>
      </li>
    );
  }

  return <li className="flex items-start justify-between gap-2 px-1 py-1 text-sm">{body}</li>;
}

export function WorkHomeSectionCard({ section, onRetry }: WorkHomeSectionCardProps) {
  const [retrying, setRetrying] = useState(false);
  const meta = workHomeSectionMeta(section.sectionId);
  const Icon = meta.icon;

  const nonEmptyBuckets = Object.entries(section.buckets ?? {}).filter(([, items]) => items.length > 0);
  // Prefer buckets when the BFF sent them; otherwise fall back to the flat items list so a
  // section that only populated `items` is still actionable.
  const hasBuckets = nonEmptyBuckets.length > 0;
  const flatItems = !hasBuckets ? (section.items ?? []).slice(0, 12) : [];

  async function handleRetry() {
    if (!onRetry) return;
    setRetrying(true);
    try {
      await onRetry(section.sectionId);
    } finally {
      setRetrying(false);
    }
  }

  return (
    <div
      className="flex flex-col rounded-2xl border border-border bg-card p-4 shadow-[0_14px_34px_-24px_rgba(2,30,26,.45)]"
      data-section-id={section.sectionId}
    >
      <div className="mb-2 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Icon className={`h-4 w-4 ${meta.accentClassName}`} />
          <h3 className="text-sm font-semibold text-foreground">{section.title}</h3>
        </div>
        {section.status === "DEGRADED" && onRetry && (
          <button
            type="button"
            onClick={handleRetry}
            disabled={retrying}
            className="flex items-center gap-1 rounded border border-border px-2 py-0.5 text-xs text-muted-foreground hover:bg-background disabled:opacity-50"
          >
            {retrying ? <Loader2 className="h-3 w-3 animate-spin" /> : <RefreshCw className="h-3 w-3" />}
            Retry
          </button>
        )}
      </div>

      {section.status === "DEGRADED" && (
        <div className="flex items-center gap-2 rounded border border-amber-300 bg-amber-50 px-2 py-1.5 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" />
          <span>{section.note ?? "This section is temporarily unavailable."}</span>
        </div>
      )}

      {section.status === "EMPTY" && (
        <p className="text-sm text-muted-foreground">{section.note ?? "Nothing here right now."}</p>
      )}

      {section.status === "OK" && (
        <div className="space-y-3">
          {hasBuckets
            ? nonEmptyBuckets.map(([bucketKey, items]) => (
                <div key={bucketKey}>
                  <p className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
                    {WORK_HOME_BUCKET_LABELS[bucketKey] ?? bucketKey} ({items.length})
                  </p>
                  <ul className="space-y-0.5">
                    {items.slice(0, 6).map((item) => (
                      <ItemRow key={item.id} item={item} />
                    ))}
                  </ul>
                  {items.length > 6 && (
                    <p className="mt-1 text-xs text-muted-foreground">+{items.length - 6} more</p>
                  )}
                </div>
              ))
            : (
              <ul className="space-y-0.5">
                {flatItems.map((item) => (
                  <ItemRow key={item.id} item={item} />
                ))}
              </ul>
            )}
        </div>
      )}
    </div>
  );
}
