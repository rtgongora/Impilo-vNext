"use client";

import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { GitMerge } from "lucide-react";
import {
  useDedupCases,
  useMergeDedup,
  useScoreDedup,
  useUnmergeDedup,
  type DedupCase,
} from "@/hooks/queries/useVitoDedup";

const STATUS_OPTIONS = ["", "PENDING", "MERGED", "DISMISSED", "REJECTED"] as const;

function statusBadge(status: DedupCase["status"]) {
  const map: Record<DedupCase["status"], string> = {
    PENDING: "bg-amber-50 text-amber-800 border-amber-100",
    MERGED: "bg-emerald-50 text-emerald-800 border-emerald-100",
    DISMISSED: "bg-gray-100 text-gray-600 border-gray-200",
    REJECTED: "bg-red-50 text-red-700 border-red-100",
  };
  return map[status] ?? "bg-gray-100 text-gray-600 border-gray-200";
}

export default function VitoDedupPage() {
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const cases = useDedupCases(statusFilter || undefined, page, pageSize);
  const merge = useMergeDedup();
  const unmerge = useUnmergeDedup();
  const score = useScoreDedup();

  const [scoreSource, setScoreSource] = useState("");
  const [scoreTarget, setScoreTarget] = useState("");

  const [mergeNotes, setMergeNotes] = useState<Record<string, string>>({});

  const rows = cases.data?.data?.items ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Deduplication Management"
        subtitle="Review potential duplicate person records, run similarity scoring, and merge or dismiss cases."
        icon={<GitMerge className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2 space-y-4">
              <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-gray-200 bg-white p-4">
                <label className="flex items-center gap-2 text-sm text-gray-600">
                  Status filter
                  <select
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
                    value={statusFilter}
                    onChange={(e) => {
                      setStatusFilter(e.target.value);
                      setPage(0);
                    }}
                  >
                    {STATUS_OPTIONS.map((s) => (
                      <option key={s} value={s}>
                        {s === "" ? "All" : s}
                      </option>
                    ))}
                  </select>
                </label>
                <span className="ml-auto text-xs text-gray-400">Page {page + 1}</span>
              </div>

              {cases.isLoading && (
                <div className="rounded-2xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-400">
                  Loading cases…
                </div>
              )}

              {cases.isError && (
                <div className="rounded-2xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">
                  Failed to load dedup cases. Ensure the BFF and VITO service are reachable.
                </div>
              )}

              {!cases.isLoading && !cases.isError && rows.length === 0 && (
                <div className="rounded-2xl bg-gray-50 p-8 text-center text-sm text-gray-500">
                  No deduplication cases found for the selected filter.
                </div>
              )}

              {rows.length > 0 && (
                <div className="space-y-3">
                  {rows.map((c) => (
                    <div
                      key={c.id}
                      className="rounded-2xl border border-gray-200 bg-white p-4 space-y-3"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                          <p className="font-medium text-gray-900 font-mono text-sm">
                            {c.sourceHealthId} ↔ {c.targetHealthId}
                          </p>
                          <p className="mt-0.5 text-xs text-gray-500">
                            Score: <span className="font-semibold">{c.score}</span> · ID: {c.id}
                          </p>
                          <p className="text-xs text-gray-400">
                            Created: {new Date(c.createdAt).toLocaleDateString()}
                            {c.updatedAt ? ` · Updated: ${new Date(c.updatedAt).toLocaleDateString()}` : ""}
                          </p>
                        </div>
                        <span
                          className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${statusBadge(c.status)}`}
                        >
                          {c.status}
                        </span>
                      </div>

                      {c.status === "PENDING" && (
                        <div className="flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
                          <input
                            type="text"
                            className="rounded-lg border border-gray-300 px-2 py-1 text-xs flex-1 min-w-[140px]"
                            placeholder="Merge notes (optional)"
                            value={mergeNotes[c.id] ?? ""}
                            onChange={(e) =>
                              setMergeNotes((prev) => ({ ...prev, [c.id]: e.target.value }))
                            }
                          />
                          <button
                            type="button"
                            disabled={merge.isPending}
                            className="rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                            onClick={() =>
                              merge.mutate({
                                caseId: c.id,
                                body: {
                                  sourceHealthId: c.sourceHealthId,
                                  targetHealthId: c.targetHealthId,
                                  notes: mergeNotes[c.id] || undefined,
                                },
                              })
                            }
                          >
                            Merge
                          </button>
                        </div>
                      )}

                      {c.status === "MERGED" && (
                        <div className="flex items-center gap-2 border-t border-gray-100 pt-3">
                          <button
                            type="button"
                            disabled={unmerge.isPending}
                            className="rounded-xl border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:border-red-300 hover:text-red-700 disabled:opacity-50"
                            onClick={() => unmerge.mutate(c.id)}
                          >
                            Unmerge
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {rows.length > 0 && (
                <div className="flex items-center justify-between">
                  <button
                    type="button"
                    disabled={page === 0}
                    className="rounded-xl border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:border-gray-400 disabled:opacity-40"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    disabled={rows.length < pageSize}
                    className="rounded-xl border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:border-gray-400 disabled:opacity-40"
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </button>
                </div>
              )}
            </div>

            <div className="space-y-4">
              <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
                <h2 className="text-sm font-semibold text-gray-900">Manual score check</h2>
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  Source Health ID
                  <input
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm font-mono"
                    value={scoreSource}
                    onChange={(e) => setScoreSource(e.target.value)}
                    placeholder="e.g. ZW-0001-2345"
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs text-gray-500">
                  Target Health ID
                  <input
                    className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm font-mono"
                    value={scoreTarget}
                    onChange={(e) => setScoreTarget(e.target.value)}
                    placeholder="e.g. ZW-0001-6789"
                  />
                </label>
                <button
                  type="button"
                  disabled={score.isPending || !scoreSource.trim() || !scoreTarget.trim()}
                  className="w-full rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                  onClick={() =>
                    score.mutate({
                      sourceHealthId: scoreSource.trim(),
                      targetHealthId: scoreTarget.trim(),
                    })
                  }
                >
                  {score.isPending ? "Scoring…" : "Calculate score"}
                </button>

                {score.isError && (
                  <p className="text-xs text-red-600">Score request failed. Check the Health IDs and BFF connectivity.</p>
                )}

                {score.data?.data && (
                  <div className="rounded-xl border border-gray-200 bg-gray-50 p-3 space-y-1">
                    <p className="text-xs text-gray-500 font-medium">Score result</p>
                    <p className="text-2xl font-bold text-gray-900">{score.data.data.score}</p>
                    {score.data.data.conflictingFields && score.data.data.conflictingFields.length > 0 && (
                      <div className="mt-2">
                        <p className="text-xs text-gray-500">Conflicting fields:</p>
                        <ul className="mt-1 space-y-0.5">
                          {score.data.data.conflictingFields.map((f) => (
                            <li key={f} className="text-xs text-red-600">
                              {f}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
