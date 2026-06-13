"use client";

import { BarChart3, Loader2, RefreshCw } from "lucide-react";
import {
  useLiveAnalytics,
  useLiveCaptureAnalyticsSnapshot,
} from "@/hooks/queries/useLive";

interface LiveAnalyticsPanelProps {
  eventId: string;
  showCapture?: boolean;
}

function formatMetricValue(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "number") return value.toLocaleString();
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return String(value);
}

export function LiveAnalyticsPanel({ eventId, showCapture = true }: LiveAnalyticsPanelProps) {
  const { data, isLoading, isError, refetch, isFetching } = useLiveAnalytics(eventId);
  const capture = useLiveCaptureAnalyticsSnapshot();

  const metrics = data?.metrics ?? {};

  return (
    <section className="rounded-2xl border border-border bg-card p-5">
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2">
          <BarChart3 className="h-5 w-5 text-violet-600" />
          <h3 className="font-semibold text-foreground">Live analytics</h3>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => refetch()}
            disabled={isFetching}
            className="inline-flex items-center gap-1 rounded-lg border border-border px-2.5 py-1.5 text-xs text-foreground hover:bg-background"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? "animate-spin" : ""}`} />
            Refresh
          </button>
          {showCapture ? (
            <button
              type="button"
              onClick={() => capture.mutate(eventId)}
              disabled={capture.isPending}
              className="rounded-lg bg-violet-600 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-60"
            >
              {capture.isPending ? "Capturing…" : "Capture snapshot"}
            </button>
          ) : null}
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading metrics…
        </div>
      ) : isError ? (
        <p className="text-sm text-warning-foreground">Analytics unavailable for this event.</p>
      ) : Object.keys(metrics).length === 0 ? (
        <p className="text-sm text-muted-foreground">No metrics captured yet. Start the session or capture a snapshot.</p>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
          {Object.entries(metrics).map(([key, value]) => (
            <div key={key} className="rounded-xl border border-border bg-background p-3">
              <p className="text-xs uppercase tracking-wide text-muted-foreground">
                {key.replace(/_/g, " ")}
              </p>
              <p className="mt-1 text-lg font-semibold text-foreground">{formatMetricValue(value)}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
