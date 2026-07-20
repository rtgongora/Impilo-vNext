"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAdjudicationCase,
  useAssignReviewer,
  useRecordDecision,
  useMergeCase,
} from "@/hooks/queries/useAbisAdjudication";
import { Scale, ShieldCheck, ShieldX, GitMerge, AlertTriangle, Loader2 } from "lucide-react";

/**
 * Adjudication case detail — assign a reviewer, record a DISTINCT / CONFIRMED_DUPLICATE
 * decision, and (only for a confirmed duplicate) perform a governed merge.
 *
 * DUAL CONTROL: the merge is NEVER automatic and the signed-in reviewer performing it must
 * differ from the reviewer who recorded the decision — enforced server-side (a same-reviewer
 * merge is rejected with a conflict).
 */
export default function AdjudicationCaseDetail() {
  const params = useParams();
  const caseId = Number(Array.isArray(params.caseId) ? params.caseId[0] : params.caseId);

  const { data: kase, isLoading, isError } = useAdjudicationCase(Number.isFinite(caseId) ? caseId : null);
  const assign = useAssignReviewer(caseId);
  const decide = useRecordDecision(caseId);
  const merge = useMergeCase(caseId);

  const [reviewer, setReviewer] = useState("");
  const [reason, setReason] = useState("");
  const [survivor, setSurvivor] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function doAssign() {
    if (!reviewer.trim()) return;
    setError(null);
    try {
      await assign.mutateAsync(reviewer.trim());
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  async function doDecision(decision: "DISTINCT" | "CONFIRMED_DUPLICATE") {
    setError(null);
    try {
      await decide.mutateAsync({ decision, reason: reason.trim() || undefined });
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  async function doMerge() {
    if (!survivor.trim()) return;
    setError(null);
    try {
      await merge.mutateAsync(survivor.trim());
    } catch (e) {
      setError(String((e as Error).message ?? e));
    }
  }

  return (
    <AppLayout>
      <PageShell
        title={`Adjudication case #${Number.isFinite(caseId) ? caseId : ""}`}
        subtitle="Review candidate scores, decide, and — for a confirmed duplicate — merge under dual control."
        icon={<Scale className="h-5 w-5" />}
      >
        {isLoading && (
          <p className="text-sm flex items-center gap-1"><Loader2 className="h-4 w-4 animate-spin" /> Loading case…</p>
        )}
        {isError && <p data-testid="case-error" className="text-sm text-red-600">Failed to load the case.</p>}

        {kase && (
          <div className="max-w-3xl space-y-6">
            <section data-testid="case-summary" className="rounded-lg border p-4 grid grid-cols-2 gap-2 text-sm">
              <div><span className="text-muted-foreground">Subject</span><div className="font-mono text-xs">{kase.subjectRef}</div></div>
              <div><span className="text-muted-foreground">Modality</span><div>{kase.modality}</div></div>
              <div><span className="text-muted-foreground">Reason</span><div>{kase.reason}</div></div>
              <div><span className="text-muted-foreground">Status</span><div data-testid="case-status">{kase.status}</div></div>
              <div><span className="text-muted-foreground">Decision</span><div data-testid="case-decision">{kase.decision ?? "—"}</div></div>
              <div><span className="text-muted-foreground">Decided by</span><div>{kase.decidedBy ?? "—"}</div></div>
            </section>

            {/* Candidates */}
            <section className="rounded-lg border">
              <h3 className="font-semibold p-3 border-b">Candidate matches</h3>
              <table className="w-full text-sm">
                <thead className="bg-muted/50 text-left">
                  <tr><th className="p-2">Rank</th><th className="p-2">Candidate subject</th><th className="p-2">Score</th><th className="p-2">Detail</th></tr>
                </thead>
                <tbody data-testid="candidate-body">
                  {(kase.candidates ?? []).map((c) => (
                    <tr key={c.candidateSubjectRef} className="border-t">
                      <td className="p-2">{c.rank + 1}</td>
                      <td className="p-2 font-mono text-xs">{c.candidateSubjectRef}</td>
                      <td className="p-2">{(c.score * 100).toFixed(1)}%</td>
                      <td className="p-2 text-muted-foreground">{c.summary ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>

            {/* Assign */}
            {kase.status !== "RESOLVED" && (
              <section className="rounded-lg border p-4 space-y-3">
                <h3 className="font-semibold">Assign a reviewer</h3>
                <div className="flex gap-2">
                  <input
                    data-testid="reviewer-input"
                    className="flex-1 rounded-md border px-3 py-2 bg-background"
                    placeholder="reviewer id"
                    value={reviewer}
                    onChange={(e) => setReviewer(e.target.value)}
                  />
                  <button
                    data-testid="assign-btn"
                    className="rounded-md border px-4 py-2 disabled:opacity-50"
                    onClick={doAssign}
                    disabled={!reviewer.trim() || assign.isPending}
                  >
                    Assign
                  </button>
                </div>
              </section>
            )}

            {/* Decision */}
            {kase.status !== "RESOLVED" && (
              <section className="rounded-lg border p-4 space-y-3">
                <h3 className="font-semibold">Record a decision</h3>
                <textarea
                  data-testid="reason-input"
                  className="w-full rounded-md border px-3 py-2 bg-background"
                  placeholder="Reason (optional)"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
                <div className="flex gap-2">
                  <button
                    data-testid="decide-distinct"
                    className="rounded-md border px-4 py-2 flex items-center gap-1 disabled:opacity-50"
                    onClick={() => doDecision("DISTINCT")}
                    disabled={decide.isPending}
                  >
                    <ShieldCheck className="h-4 w-4" /> Distinct (not a duplicate)
                  </button>
                  <button
                    data-testid="decide-duplicate"
                    className="rounded-md bg-red-600 text-white px-4 py-2 flex items-center gap-1 disabled:opacity-50"
                    onClick={() => doDecision("CONFIRMED_DUPLICATE")}
                    disabled={decide.isPending}
                  >
                    <ShieldX className="h-4 w-4" /> Confirm duplicate
                  </button>
                </div>
              </section>
            )}

            {/* Governed merge — only for a confirmed duplicate that has not merged yet */}
            {kase.decision === "CONFIRMED_DUPLICATE" && !kase.mergedAt && (
              <section data-testid="merge-section" className="rounded-lg border border-amber-400 p-4 space-y-3">
                <h3 className="font-semibold flex items-center gap-1"><GitMerge className="h-4 w-4" /> Governed merge (dual control)</h3>
                <p className="text-xs text-amber-700 dark:text-amber-400 flex items-start gap-1">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  A merge is never automatic. You must be a DIFFERENT reviewer from the one who recorded the
                  decision ({kase.decidedBy ?? "—"}); a same-reviewer merge is rejected.
                </p>
                <label className="block text-sm">
                  <span className="block mb-1">Surviving subject (merge into)</span>
                  <select
                    data-testid="survivor-select"
                    className="w-full rounded-md border px-3 py-2 bg-background"
                    value={survivor}
                    onChange={(e) => setSurvivor(e.target.value)}
                  >
                    <option value="">Select a candidate…</option>
                    {(kase.candidates ?? []).map((c) => (
                      <option key={c.candidateSubjectRef} value={c.candidateSubjectRef}>
                        {c.candidateSubjectRef} ({(c.score * 100).toFixed(1)}%)
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  data-testid="merge-btn"
                  className="rounded-md bg-amber-600 text-white px-4 py-2 disabled:opacity-50"
                  onClick={doMerge}
                  disabled={!survivor.trim() || merge.isPending}
                >
                  {merge.isPending ? "Merging…" : "Perform governed merge"}
                </button>
              </section>
            )}

            {kase.mergedAt && (
              <p data-testid="merged-note" className="text-sm text-green-600 flex items-center gap-1">
                <GitMerge className="h-4 w-4" /> Merged into {kase.mergeTargetSubjectRef} · dual sign-off by {kase.dualSignOffBy}
              </p>
            )}

            {error && <p data-testid="error" className="text-sm text-red-600">{error}</p>}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
