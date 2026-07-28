"use client";

/**
 * Renders one Work Home section (Phase F1). Five states per the plan: loading is the
 * caller's concern (skeleton before data arrives); this component covers ok / empty /
 * degraded. "Forbidden" never reaches here — a family the caller isn't authorised for
 * simply has no adapter, so the BFF never emits its section at all.
 */

import { AlertCircle, Loader2, RefreshCw } from "lucide-react";
import { useState } from "react";
import type { WorkHomeSection } from "@/hooks/queries/useWorkHome";
import { WORK_HOME_BUCKET_LABELS } from "@/lib/work-home/section-registry";
import { workHomeSectionMeta } from "@/lib/work-home/section-registry";

interface WorkHomeSectionCardProps {
  section: WorkHomeSection;
  onRetry?: (sectionId: string) => Promise<void>;
}

export function WorkHomeSectionCard({ section, onRetry }: WorkHomeSectionCardProps) {
  const [retrying, setRetrying] = useState(false);
  const meta = workHomeSectionMeta(section.sectionId);
  const Icon = meta.icon;

  const nonEmptyBuckets = Object.entries(section.buckets ?? {}).filter(([, items]) => items.length > 0);

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
    <div className="flex flex-col rounded-lg border border-border bg-card p-4" data-section-id={section.sectionId}>
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
          {nonEmptyBuckets.map(([bucketKey, items]) => (
            <div key={bucketKey}>
              <p className="mb-1 text-xs font-semibold uppercase text-muted-foreground">
                {WORK_HOME_BUCKET_LABELS[bucketKey] ?? bucketKey} ({items.length})
              </p>
              <ul className="space-y-1">
                {items.slice(0, 6).map((item) => (
                  <li key={item.id} className="flex items-start justify-between gap-2 text-sm">
                    <span className="text-foreground">{item.title ?? item.id}</span>
                    {item.priority && (
                      <span className="shrink-0 text-xs text-muted-foreground">{item.priority}</span>
                    )}
                  </li>
                ))}
              </ul>
              {items.length > 6 && (
                <p className="mt-1 text-xs text-muted-foreground">+{items.length - 6} more</p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
