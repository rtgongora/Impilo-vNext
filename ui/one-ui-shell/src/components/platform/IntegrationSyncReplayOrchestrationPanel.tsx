"use client";

import Link from "next/link";
import { useState } from "react";
import { AlertCircle, CheckCircle2, Loader2, RefreshCw, Route } from "lucide-react";
import {
  useIntegrationHubDeadLetters,
  useIntegrationHubRoutes,
  useReplayIntegrationDeadLetter,
} from "@/hooks/queries/useIntegrationHub";

function deadLetterRows(payload: unknown): Array<{ id: string; summary: string }> {
  if (!payload || typeof payload !== "object") return [];
  const raw = payload as Record<string, unknown>;
  const list = Array.isArray(raw.content)
    ? raw.content
    : Array.isArray(raw.items)
      ? raw.items
      : Array.isArray(raw)
        ? raw
        : [];
  return list
    .filter((x) => x && typeof x === "object")
    .map((x) => {
      const row = x as Record<string, unknown>;
      const id = String(row.id ?? row.deadLetterId ?? row.messageId ?? "");
      const summary = String(row.routeId ?? row.error ?? row.reason ?? row.topic ?? "dead letter");
      return { id, summary };
    })
    .filter((r) => r.id.length > 0)
    .slice(0, 5);
}

function routeCount(payload: unknown): number {
  if (Array.isArray(payload)) return payload.length;
  if (payload && typeof payload === "object") return 1;
  return 0;
}

type ReplayFeedback = { id: string; status: "success" | "error" };

function deadLetterEmptyMessage(
  routesLoading: boolean,
  routesError: boolean,
  deadLoading: boolean,
  deadError: boolean,
  routeTotal: number,
  deadTotal: number,
): string {
  if (routesLoading || deadLoading) return "Loading integration hub state…";
  if (routesError && deadError) {
    return "Integration hub unreachable — routes and dead-letter feeds both failed.";
  }
  if (routesError) {
    return "Routes feed unavailable — dead-letter replay may still work if ids are known.";
  }
  if (deadError) {
    return "Dead-letter feed unavailable — hub routes may still be visible when routes recover.";
  }
  if (routeTotal === 0 && deadTotal === 0) {
    return "Empty hub — no routes registered and no dead letters with replay ids yet.";
  }
  if (deadTotal === 0) {
    return "No dead letters with replay ids on this page — dispatch queue is clear for the current slice.";
  }
  return "No replay candidates on this page.";
}

export function IntegrationSyncReplayOrchestrationPanel() {
  const routesQ = useIntegrationHubRoutes();
  const deadQ = useIntegrationHubDeadLetters(0, 10);
  const replay = useReplayIntegrationDeadLetter();
  const [replayFeedback, setReplayFeedback] = useState<ReplayFeedback | null>(null);
  const [replayingId, setReplayingId] = useState<string | null>(null);

  const routeTotal = routeCount(routesQ.data?.data);
  const deadRows = deadLetterRows(deadQ.data?.data);
  const emptyMessage = deadLetterEmptyMessage(
    routesQ.isLoading,
    routesQ.isError,
    deadQ.isLoading,
    deadQ.isError,
    routeTotal,
    deadRows.length,
  );

  return (
    <section
      className="rounded-xl border border-cyan-200 bg-cyan-50/50 px-4 py-4"
      data-testid="integration-sync-replay-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2 text-sm text-cyan-950">
          <Route className="mt-0.5 h-4 w-4 shrink-0 text-cyan-700" />
          <div>
            <p className="font-medium">Integration sync &amp; replay</p>
            <p>
              Monitor hub routes, inspect dead letters, and replay failed dispatches when upstream is healthy.
            </p>
            <p className="mt-1 text-xs text-cyan-800" data-testid="integration-hub-summary">
              {routesQ.isLoading
                ? "Loading routes…"
                : routesQ.isError
                  ? "Routes: unavailable"
                  : `${routeTotal} route(s) registered`}{" "}
              ·{" "}
              {deadQ.isLoading
                ? "loading dead letters…"
                : deadQ.isError
                  ? "dead letters: unavailable"
                  : `${deadRows.length} replay candidate(s) on this page`}
            </p>
            {replayFeedback ? (
              <p
                className={`mt-1 flex items-center gap-1 text-xs ${
                  replayFeedback.status === "success" ? "text-emerald-800" : "text-amber-800"
                }`}
                data-testid="integration-replay-feedback"
              >
                {replayFeedback.status === "success" ? (
                  <CheckCircle2 className="h-3 w-3" />
                ) : (
                  <AlertCircle className="h-3 w-3" />
                )}
                Replay {replayFeedback.status === "success" ? "queued" : "failed"} for{" "}
                <span className="font-mono">{replayFeedback.id}</span>
              </p>
            ) : null}
          </div>
        </div>
        <Link
          href="/admin/integration-templates"
          className="inline-flex shrink-0 items-center gap-1 rounded-lg border border-cyan-300 bg-white px-3 py-1.5 text-xs font-medium text-cyan-900 hover:bg-cyan-50"
        >
          Mapping templates
        </Link>
      </div>

      {deadRows.length > 0 ? (
        <ul className="mt-3 space-y-2">
          {deadRows.map((row) => (
            <li
              key={row.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-cyan-100 bg-white px-3 py-2 text-xs"
            >
              <span className="font-mono text-cyan-900">{row.id}</span>
              <span className="text-cyan-800">{row.summary}</span>
              <button
                type="button"
                disabled={replay.isPending}
                onClick={() => {
                  setReplayingId(row.id);
                  replay.mutate(row.id, {
                    onSuccess: () => setReplayFeedback({ id: row.id, status: "success" }),
                    onError: () => setReplayFeedback({ id: row.id, status: "error" }),
                    onSettled: () => setReplayingId(null),
                  });
                }}
                className="inline-flex items-center gap-1 rounded bg-cyan-700 px-2 py-1 text-[11px] font-medium text-white hover:bg-cyan-800 disabled:opacity-50"
              >
                {replay.isPending && replayingId === row.id ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  <RefreshCw className="h-3 w-3" />
                )}
                Replay
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-3 text-xs text-cyan-800" data-testid="integration-deadletter-empty">
          {emptyMessage}
        </p>
      )}
    </section>
  );
}
