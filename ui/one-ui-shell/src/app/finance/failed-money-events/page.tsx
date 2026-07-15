"use client";

/**
 * Failed money events — dead-letter replay.
 * Route: /finance/failed-money-events
 *
 * When a money event (settlement, refund, payout) fails all delivery retries
 * it is dead-lettered into COSTA rather than silently dropped. A finance-ops
 * user reviews the dead letters here and replays them: the original payload is
 * re-published onto its original topic. Replay is safe because every money
 * consumer is idempotent — a replayed event that already processed is skipped.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, RefreshCw, Loader2, AlertTriangle, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFailedMoneyEvents,
  useReplayFailedMoneyEvent,
  type FailedMoneyEvent,
} from "@/hooks/queries/useFailedMoneyEvents";

function asList(v: unknown): FailedMoneyEvent[] {
  if (!v) return [];
  const r = v as Record<string, unknown>;
  if (Array.isArray(r.data)) return r.data as FailedMoneyEvent[];
  if (Array.isArray(v)) return v as FailedMoneyEvent[];
  return [];
}

function statusBadge(status: string) {
  const dead = status === "DEAD";
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
        dead ? "bg-danger-soft text-danger" : "bg-success-soft text-primary-hover"
      }`}
    >
      {status}
    </span>
  );
}

const FILTERS = ["DEAD", "REPLAYED", "ALL"] as const;

export default function FailedMoneyEventsPage() {
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("DEAD");
  const statusParam = filter === "ALL" ? undefined : filter;
  const q = useFailedMoneyEvents(statusParam);
  const replay = useReplayFailedMoneyEvent();
  const [replayingId, setReplayingId] = useState<number | null>(null);

  const rows = useMemo(() => asList(q.data), [q.data]);

  const doReplay = (id: number) => {
    setReplayingId(id);
    replay.mutate(id, { onSettled: () => setReplayingId(null) });
  };

  return (
    <AppLayout>
      <PageShell
        title="Failed money events"
        subtitle="Review and replay dead-lettered settlement, refund, and payout events"
        icon={<AlertTriangle className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to finance
          </Link>
        </div>

        {/* Safety note */}
        <div className="mb-4 flex items-start gap-2 rounded-lg border border-border bg-success-soft/40 px-4 py-3 text-sm">
          <ShieldCheck className="mt-0.5 h-4 w-4 text-primary-hover" />
          <p className="text-muted-foreground">
            Replay re-publishes the original payload onto its original topic. This is safe —
            every money consumer is idempotent, so an event that already processed is skipped.
          </p>
        </div>

        {/* Filters */}
        <div className="mb-4 flex items-center gap-2">
          {FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                filter === f
                  ? "bg-primary text-primary-foreground"
                  : "border border-border text-muted-foreground hover:text-foreground"
              }`}
            >
              {f}
            </button>
          ))}
          <button
            type="button"
            onClick={() => void q.refetch()}
            className="ml-auto inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-4 w-4" /> Refresh
          </button>
        </div>

        {/* Table */}
        <section className="rounded-xl border border-border bg-card shadow-sm">
          <div className="p-4">
            {q.isLoading && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading dead letters…
              </p>
            )}
            {q.isError && (
              <p className="text-sm text-danger">Unable to load failed money events.</p>
            )}
            {!q.isLoading && !q.isError && rows.length === 0 && (
              <p className="text-sm text-muted-foreground">
                No {filter === "ALL" ? "" : `${filter.toLowerCase()} `}money events. The money rails are clear.
              </p>
            )}
            {rows.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-muted-foreground">
                      <th className="pb-2 pr-3 font-medium">ID</th>
                      <th className="pb-2 pr-3 font-medium">Original topic</th>
                      <th className="pb-2 pr-3 font-medium">Error</th>
                      <th className="pb-2 pr-3 font-medium">Status</th>
                      <th className="pb-2 pr-3 font-medium">Created</th>
                      <th className="pb-2 font-medium">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.id} className="border-t border-border align-top">
                        <td className="py-2 pr-3 font-mono text-xs">{r.id}</td>
                        <td className="py-2 pr-3 font-mono text-xs">{r.originalTopic}</td>
                        <td className="py-2 pr-3 text-xs text-muted-foreground max-w-xs truncate" title={r.errorMessage ?? ""}>
                          {r.errorMessage ?? "—"}
                        </td>
                        <td className="py-2 pr-3">{statusBadge(r.status)}</td>
                        <td className="py-2 pr-3 text-xs text-muted-foreground">{r.createdAt}</td>
                        <td className="py-2">
                          {r.status === "DEAD" ? (
                            <button
                              type="button"
                              onClick={() => doReplay(r.id)}
                              disabled={replayingId === r.id}
                              className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                            >
                              {replayingId === r.id ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <RefreshCw className="h-3.5 w-3.5" />
                              )}
                              Replay
                            </button>
                          ) : (
                            <span className="text-xs text-muted-foreground">
                              {r.replayedBy ? `by ${r.replayedBy}` : "—"}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {replay.isError && (
              <p className="mt-3 text-xs text-danger">Replay failed. Please try again.</p>
            )}
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}
