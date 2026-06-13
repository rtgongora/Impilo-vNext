"use client";

import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { GitMerge, ChevronLeft, ChevronRight, Search } from "lucide-react";
import {
  usePendingMatches,
  useFindMatches,
  useResolveMatch,
  type MatchResult,
} from "@/hooks/queries/useVitoMatch";

const PAGE_SIZE = 20;

function StatusBadge({ status }: { status: MatchResult["status"] }) {
  const map: Record<MatchResult["status"], string> = {
    PENDING: "bg-warning-soft text-warning-foreground border-warning/35",
    CONFIRMED: "bg-success-soft text-primary-hover border-success/25",
    REJECTED: "bg-danger-soft text-danger border-danger/28",
  };
  return (
    <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${map[status]}`}>
      {status}
    </span>
  );
}

function MatchRow({
  match,
  onConfirm,
  onReject,
  isPending,
}: {
  match: MatchResult;
  onConfirm: () => void;
  onReject: () => void;
  isPending: boolean;
}) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl border border-border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-sm font-medium text-foreground">{match.sourceHealthId}</span>
          <span className="text-muted-foreground">↔</span>
          <span className="font-mono text-sm font-medium text-foreground">{match.candidateHealthId}</span>
          <StatusBadge status={match.status} />
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Score <span className="font-semibold text-foreground">{match.score.toFixed(4)}</span>
          {" · "}
          Matched {new Date(match.matchedAt).toLocaleString()}
        </p>
      </div>
      {match.status === "PENDING" && (
        <div className="flex shrink-0 gap-2">
          <button
            type="button"
            disabled={isPending}
            onClick={onConfirm}
            className="rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            Confirm
          </button>
          <button
            type="button"
            disabled={isPending}
            onClick={onReject}
            className="rounded-xl border border-red-300 px-3 py-1.5 text-xs font-medium text-danger hover:border-red-400 hover:bg-danger-soft disabled:opacity-50"
          >
            Reject
          </button>
        </div>
      )}
    </div>
  );
}

export default function VitoMatchPage() {
  const [page, setPage] = useState(0);
  const [triggerHealthId, setTriggerHealthId] = useState("");

  const pendingMatches = usePendingMatches(page, PAGE_SIZE);
  const findMatches = useFindMatches();
  const resolveMatch = useResolveMatch();

  const matches = pendingMatches.data?.data?.items ?? [];
  const isResolving = resolveMatch.isPending;

  return (
    <AppLayout>
      <PageShell
        title="Match Review Queue"
        subtitle="Pending identity match candidates awaiting stewardship confirmation or rejection"
        icon={<GitMerge className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-muted-foreground hover:text-foreground underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-sm font-semibold text-foreground">Find matches by Health ID</h2>
            </div>
            <p className="mt-1 text-xs text-muted-foreground">
              Trigger a match search for a specific person. Results are added to the pending queue below.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                type="text"
                value={triggerHealthId}
                onChange={(e) => setTriggerHealthId(e.target.value)}
                placeholder="Enter Health ID…"
                className="rounded-lg border border-border px-3 py-1.5 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-primary/40"
              />
              <button
                type="button"
                disabled={!triggerHealthId.trim() || findMatches.isPending}
                onClick={() => {
                  if (triggerHealthId.trim()) {
                    findMatches.mutate(triggerHealthId.trim(), {
                      onSuccess: () => setTriggerHealthId(""),
                    });
                  }
                }}
                className="inline-flex items-center gap-2 rounded-xl bg-primary-hover px-4 py-1.5 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              >
                {findMatches.isPending ? "Searching…" : "Find matches"}
              </button>
            </div>
            {findMatches.isError && (
              <p className="mt-2 text-sm text-red-600">Search failed. Ensure the BFF and VITO service are reachable.</p>
            )}
            {findMatches.isSuccess && (
              <p className="mt-2 text-sm text-primary-hover">Match search completed. Queue refreshed.</p>
            )}
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                Pending candidates
              </h2>
              {pendingMatches.isLoading && (
                <span className="text-xs text-muted-foreground">Loading…</span>
              )}
            </div>

            {pendingMatches.isError && (
              <div className="rounded-2xl border border-red-100 bg-danger-soft p-4 text-sm text-danger">
                Failed to load match queue. Ensure the Experience BFF and VITO service are reachable.
              </div>
            )}

            {!pendingMatches.isLoading && !pendingMatches.isError && matches.length === 0 && (
              <div className="rounded-2xl bg-background p-8 text-center text-sm text-muted-foreground">
                No pending match candidates. Use the search panel above to trigger a match run.
              </div>
            )}

            {matches.map((match) => (
              <MatchRow
                key={match.matchId}
                match={match}
                isPending={isResolving}
                onConfirm={() =>
                  resolveMatch.mutate({ matchId: match.matchId, disposition: "CONFIRMED" })
                }
                onReject={() =>
                  resolveMatch.mutate({ matchId: match.matchId, disposition: "REJECTED" })
                }
              />
            ))}

            {resolveMatch.isError && (
              <p className="text-sm text-red-600">Resolution failed. Please try again.</p>
            )}

            <div className="flex items-center justify-between pt-2">
              <button
                type="button"
                disabled={page === 0 || pendingMatches.isLoading}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="inline-flex items-center gap-1 rounded-xl border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:border-gray-400 disabled:opacity-40"
              >
                <ChevronLeft className="h-4 w-4" />
                Previous
              </button>
              <span className="text-xs text-muted-foreground">Page {page + 1}</span>
              <button
                type="button"
                disabled={matches.length < PAGE_SIZE || pendingMatches.isLoading}
                onClick={() => setPage((p) => p + 1)}
                className="inline-flex items-center gap-1 rounded-xl border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:border-gray-400 disabled:opacity-40"
              >
                Next
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
