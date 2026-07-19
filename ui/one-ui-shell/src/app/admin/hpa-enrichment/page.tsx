"use client";

/**
 * HPA national facility enrichment — admin review console. Route: /admin/hpa-enrichment
 *
 * Steward view of the HPA import batches, their per-outcome reconciliation counts,
 * and the review queues (identity conflicts / possible-existing / unresolved-site).
 * Review items never block the safe match-or-create of other candidates.
 */

import { useState } from "react";
import { Building2, ClipboardCheck, ListChecks } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useHpaImportRuns,
  useHpaOutcomes,
  useHpaReviewQueue,
  type HpaImportRun,
} from "@/hooks/queries/useHpaImport";

function RunRow({ run, active, onSelect }: { run: HpaImportRun; active: boolean; onSelect: () => void }) {
  return (
    <button
      type="button"
      onClick={onSelect}
      data-testid="hpa-run-row"
      className={[
        "w-full rounded-xl border p-4 text-left transition",
        active ? "border-primary/40 bg-primary/5" : "border-border bg-card hover:border-primary/30",
      ].join(" ")}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-foreground">Batch #{run.id}</span>
        <span className="rounded-full bg-muted px-2 py-0.5 text-xs uppercase tracking-wide text-muted-foreground">
          {run.dry_run ? "dry-run" : "applied"} · {run.status}
        </span>
      </div>
      <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-muted-foreground sm:grid-cols-3">
        <span>candidates: <b className="text-foreground">{run.candidates_total}</b></span>
        <span>estate scanned: <b className="text-foreground">{run.live_tuso_scanned}</b></span>
        <span>enriched: <b className="text-foreground">{run.enriched_existing}</b></span>
        <span>new establishment: <b className="text-foreground">{run.created_establishment}</b></span>
        <span>unresolved site: <b className="text-foreground">{run.created_unresolved_site}</b></span>
        <span>review: <b className="text-foreground">{run.routed_to_review}</b></span>
      </div>
    </button>
  );
}

export default function HpaEnrichmentAdminPage() {
  const runs = useHpaImportRuns();
  const [runId, setRunId] = useState<number | null>(null);
  const outcomes = useHpaOutcomes(runId);
  const review = useHpaReviewQueue(runId);

  return (
    <AppLayout>
      <PageShell
        title="HPA facility enrichment"
        subtitle="Import batches, reconciliation outcomes and review queues"
      >
        <div className="mx-auto max-w-4xl space-y-6">
          <section className="space-y-2">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Building2 className="h-4 w-4 text-primary" /> Import batches
            </h2>
            {runs.isLoading && <p className="text-sm text-muted-foreground">Loading batches…</p>}
            {!runs.isLoading && (runs.data ?? []).length === 0 && (
              <p className="rounded-lg border border-dashed border-border bg-muted/30 p-4 text-sm text-muted-foreground">
                No HPA import batches yet. Run a dry-run or apply against the HPA candidate feed to populate this list.
              </p>
            )}
            <div className="space-y-2">
              {(runs.data ?? []).map((run) => (
                <RunRow key={run.id} run={run} active={run.id === runId} onSelect={() => setRunId(run.id)} />
              ))}
            </div>
          </section>

          {runId != null && (
            <>
              <section className="space-y-2">
                <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <ListChecks className="h-4 w-4 text-primary" /> Reconciliation outcomes
                </h2>
                {outcomes.isLoading && <p className="text-sm text-muted-foreground">Loading outcomes…</p>}
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  {(outcomes.data ?? []).map((o) => (
                    <div
                      key={o.decision_outcome}
                      className="flex items-center justify-between rounded-lg border border-border bg-card p-3 text-sm"
                      data-testid="hpa-outcome-row"
                    >
                      <span className="text-foreground">{o.decision_outcome.replace(/_/g, " ")}</span>
                      <span className="font-semibold text-foreground">{o.n}</span>
                    </div>
                  ))}
                </div>
              </section>

              <section className="space-y-2">
                <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <ClipboardCheck className="h-4 w-4 text-primary" /> Review queue
                </h2>
                {review.isLoading && <p className="text-sm text-muted-foreground">Loading review queue…</p>}
                {!review.isLoading && (review.data ?? []).length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    No items awaiting review in this batch — safe match-or-create was applied without blocking.
                  </p>
                )}
                <ul className="space-y-2">
                  {(review.data ?? []).map((r) => (
                    <li
                      key={r.id}
                      className="rounded-lg border border-border bg-card p-3 text-sm"
                      data-testid="hpa-review-row"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-foreground">
                          {r.decision_outcome.replace(/_/g, " ")}
                        </span>
                        <span className="rounded-full bg-muted px-2 py-0.5 text-xs uppercase tracking-wide text-muted-foreground">
                          {r.confidence} · {r.reviewer_status}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {r.source_record_key}
                        {r.reason_codes ? ` · ${r.reason_codes}` : ""}
                      </p>
                    </li>
                  ))}
                </ul>
              </section>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
