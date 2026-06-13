"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { EHR_CLINICAL_COLUMNS } from "@/lib/json-api/generic-table-columns";
import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, BookHeart, CheckCircle2, Loader2, RefreshCw, XCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  type ClinicalCurationStatus,
  useClinicalCurationDecision,
  useClinicalCurationReviewItems,
} from "@/hooks/queries/useClinicalCuration";
import {
  useClinicalDefaultEdlizDocumentId,
  useClinicalSourceIngestionSummary,
  useIngestClinicalPdf,
} from "@/hooks/queries/useGuidance";

const STATUS_OPTIONS: ClinicalCurationStatus[] = ["PROPOSED", "APPROVED", "REJECTED"];

function summarisePayload(payload: Record<string, unknown> | undefined) {
  if (!payload) return "No proposal payload attached.";
  const title = typeof payload.title === "string" ? payload.title : undefined;
  const excerpt = typeof payload.excerpt === "string" ? payload.excerpt : undefined;
  const source = typeof payload.source_title === "string" ? payload.source_title : undefined;
  return title ?? excerpt ?? source ?? "Structured proposal payload available below.";
}

export default function ClinicalCurationPage() {
  const { user } = useAuthStore();
  const reviewer = user?.id ?? user?.displayName ?? "experience-admin";
  const [status, setStatus] = useState<ClinicalCurationStatus>("PROPOSED");

  const queueQ = useClinicalCurationReviewItems(status);
  const decideM = useClinicalCurationDecision(status);
  const defaultDocQ = useClinicalDefaultEdlizDocumentId();
  const defaultDocumentId = defaultDocQ.data?.data?.document_id;
  const summaryQ = useClinicalSourceIngestionSummary(defaultDocumentId);
  const ingestM = useIngestClinicalPdf();

  const reviewItems = useMemo(() => queueQ.data?.data ?? [], [queueQ.data]);

  return (
    <AppLayout>
      <PageShell
        title="Clinical Knowledge Curation"
        subtitle="National review queue for EDLIZ-aligned knowledge proposals. This replaces the standalone knowledge-admin sidecar with the same Experience BFF contract."
      >
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          <Link href="/admin" className="inline-flex items-center gap-1 hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Administration
          </Link>
          <Link href="/admin/sidecar-retirement" className="inline-flex items-center gap-1 text-primary hover:text-primary-hover">
            Sidecar retirement ledger
          </Link>
        </div>

        <div className="space-y-6">
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-foreground">EDLIZ source ingestion baseline</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  Uses the same Experience BFF source-ingestion rails as the clinical knowledge dock. This keeps the curation queue grounded in the canonical source document state.
                </p>
              </div>
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm hover:bg-background disabled:opacity-50"
                disabled={ingestM.isPending || !defaultDocumentId}
                onClick={() => {
                  if (!defaultDocumentId) return;
                  ingestM.mutate({ documentId: defaultDocumentId });
                }}
              >
                {ingestM.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                Re-index default PDF
              </button>
            </div>

            <dl className="mt-4 grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg border border-border bg-background p-3">
                <dt className="text-xs uppercase tracking-wide text-muted-foreground">Default document</dt>
                <dd className="mt-1 font-mono text-xs text-foreground">{defaultDocumentId ?? "Unavailable"}</dd>
              </div>
              <div className="rounded-lg border border-border bg-background p-3">
                <dt className="text-xs uppercase tracking-wide text-muted-foreground">PDF-derived sections</dt>
                <dd className="mt-1 text-lg font-semibold text-foreground">
                  {summaryQ.data?.data?.pdf_derived_section_count ?? "—"}
                </dd>
              </div>
              <div className="rounded-lg border border-border bg-background p-3">
                <dt className="text-xs uppercase tracking-wide text-muted-foreground">Total indexed sections</dt>
                <dd className="mt-1 text-lg font-semibold text-foreground">
                  {summaryQ.data?.data?.total_section_count ?? "—"}
                </dd>
              </div>
            </dl>

            {defaultDocQ.isError || summaryQ.isError ? (
              <p className="mt-3 text-sm text-warning-foreground">
                Source-ingestion metadata could not be loaded. The curation queue still works, but the source baseline is unavailable right now.
              </p>
            ) : null}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-foreground">Review queue</h2>
                <p className="mt-1 text-xs text-muted-foreground">
                  BFF route: <code className="text-[11px]">/internal/v1/clinical/curation/review-items</code>. Decisions write reviewer identity from the current Experience session.
                </p>
              </div>

              <label className="text-xs text-muted-foreground">
                Status
                <select
                  value={status}
                  onChange={(event) => setStatus(event.target.value as ClinicalCurationStatus)}
                  className="mt-1 block rounded-lg border border-border px-3 py-2 text-sm"
                  aria-label="Curation status"
                >
                  {STATUS_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {queueQ.isLoading ? (
              <p className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading curation queue…
              </p>
            ) : queueQ.isError ? (
              <p className="mt-4 text-sm text-danger">Clinical curation queue request failed.</p>
            ) : reviewItems.length === 0 ? (
              <p className="mt-4 text-sm text-muted-foreground">No {status} items in the queue.</p>
            ) : (
              <ul className="mt-4 space-y-4">
                {reviewItems.map((item) => {
                  const payload =
                    item.proposed_payload && typeof item.proposed_payload === "object"
                      ? (item.proposed_payload as Record<string, unknown>)
                      : undefined;

                  return (
                    <li key={item.id} className="rounded-xl border border-border bg-background/70 p-4">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <p className="font-mono text-xs text-muted-foreground">{item.id}</p>
                          <h3 className="mt-1 text-sm font-semibold text-foreground">{summarisePayload(payload)}</h3>
                          <p className="mt-1 text-xs text-muted-foreground">
                            Status: <span className="font-semibold text-foreground">{String(item.review_status)}</span>
                          </p>
                        </div>

                        {status === "PROPOSED" ? (
                          <div className="flex flex-wrap gap-2">
                            <button
                              type="button"
                              disabled={decideM.isPending}
                              className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                              onClick={() =>
                                decideM.mutate({ id: item.id, decision: "APPROVED", reviewer })
                              }
                            >
                              <CheckCircle2 className="h-4 w-4" /> Approve
                            </button>
                            <button
                              type="button"
                              disabled={decideM.isPending}
                              className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-neutral-100 disabled:opacity-50"
                              onClick={() =>
                                decideM.mutate({ id: item.id, decision: "REJECTED", reviewer })
                              }
                            >
                              <XCircle className="h-4 w-4" /> Reject
                            </button>
                          </div>
                        ) : null}
                      </div>

                      <div className="mt-3 rounded-lg border border-border bg-card p-3">
                        <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                          <BookHeart className="h-4 w-4" />
                          Proposed payload
                        </div>
                        <JsonApiDataTable data={payload ?? {}} columns={EHR_CLINICAL_COLUMNS} emptyTitle="No proposed fields" />
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}

            {decideM.isError ? (
              <p className="mt-3 text-sm text-danger">Decision request failed.</p>
            ) : null}
            {decideM.isSuccess ? (
              <p className="mt-3 text-sm text-primary-hover">Decision recorded and queue refreshed.</p>
            ) : null}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
