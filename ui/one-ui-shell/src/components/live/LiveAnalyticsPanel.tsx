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
    <section className="rounded-2xl border border-gray-200 bg-white p-5">
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2">
          <BarChart3 className="h-5 w-5 text-violet-600" />
          <h3 className="font-semibold text-gray-900">Live analytics</h3>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => refetch()}
            disabled={isFetching}
            className="inline-flex items-center gap-1 rounded-lg border border-gray-300 px-2.5 py-1.5 text-xs text-gray-700 hover:bg-gray-50"
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
        <div className="flex items-center gap-2 py-8 text-sm text-gray-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading metrics…
        </div>
      ) : isError ? (
        <p className="text-sm text-amber-700">Analytics unavailable for this event.</p>
      ) : Object.keys(metrics).length === 0 ? (
        <p className="text-sm text-gray-500">No metrics captured yet. Start the session or capture a snapshot.</p>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
          {Object.entries(metrics).map(([key, value]) => (
            <div key={key} className="rounded-xl border border-gray-100 bg-gray-50 p-3">
              <p className="text-xs uppercase tracking-wide text-gray-500">
                {key.replace(/_/g, " ")}
              </p>
              <p className="mt-1 text-lg font-semibold text-gray-900">{formatMetricValue(value)}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
