"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
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
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-gray-500">
          <Link href="/admin" className="inline-flex items-center gap-1 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Administration
          </Link>
          <Link href="/admin/sidecar-retirement" className="inline-flex items-center gap-1 text-impilo-600 hover:text-impilo-700">
            Sidecar retirement ledger
          </Link>
        </div>

        <div className="space-y-6">
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-900">EDLIZ source ingestion baseline</h2>
                <p className="mt-1 text-xs text-slate-500">
                  Uses the same Experience BFF source-ingestion rails as the clinical knowledge dock. This keeps the curation queue grounded in the canonical source document state.
                </p>
              </div>
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50 disabled:opacity-50"
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
              <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
                <dt className="text-xs uppercase tracking-wide text-slate-500">Default document</dt>
                <dd className="mt-1 font-mono text-xs text-slate-800">{defaultDocumentId ?? "Unavailable"}</dd>
              </div>
              <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
                <dt className="text-xs uppercase tracking-wide text-slate-500">PDF-derived sections</dt>
                <dd className="mt-1 text-lg font-semibold text-slate-900">
                  {summaryQ.data?.data?.pdf_derived_section_count ?? "—"}
                </dd>
              </div>
              <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
                <dt className="text-xs uppercase tracking-wide text-slate-500">Total indexed sections</dt>
                <dd className="mt-1 text-lg font-semibold text-slate-900">
                  {summaryQ.data?.data?.total_section_count ?? "—"}
                </dd>
              </div>
            </dl>

            {defaultDocQ.isError || summaryQ.isError ? (
              <p className="mt-3 text-sm text-amber-700">
                Source-ingestion metadata could not be loaded. The curation queue still works, but the source baseline is unavailable right now.
              </p>
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold text-slate-900">Review queue</h2>
                <p className="mt-1 text-xs text-slate-500">
                  BFF route: <code className="text-[11px]">/internal/v1/clinical/curation/review-items</code>. Decisions write reviewer identity from the current Experience session.
                </p>
              </div>

              <label className="text-xs text-slate-600">
                Status
                <select
                  value={status}
                  onChange={(event) => setStatus(event.target.value as ClinicalCurationStatus)}
                  className="mt-1 block rounded-lg border border-slate-200 px-3 py-2 text-sm"
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
              <p className="mt-4 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading curation queue…
              </p>
            ) : queueQ.isError ? (
              <p className="mt-4 text-sm text-red-700">Clinical curation queue request failed.</p>
            ) : reviewItems.length === 0 ? (
              <p className="mt-4 text-sm text-slate-500">No {status} items in the queue.</p>
            ) : (
              <ul className="mt-4 space-y-4">
                {reviewItems.map((item) => {
                  const payload =
                    item.proposed_payload && typeof item.proposed_payload === "object"
                      ? (item.proposed_payload as Record<string, unknown>)
                      : undefined;

                  return (
                    <li key={item.id} className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <p className="font-mono text-xs text-slate-500">{item.id}</p>
                          <h3 className="mt-1 text-sm font-semibold text-slate-900">{summarisePayload(payload)}</h3>
                          <p className="mt-1 text-xs text-slate-500">
                            Status: <span className="font-semibold text-slate-700">{String(item.review_status)}</span>
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
                              className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-700 hover:bg-slate-100 disabled:opacity-50"
                              onClick={() =>
                                decideM.mutate({ id: item.id, decision: "REJECTED", reviewer })
                              }
                            >
                              <XCircle className="h-4 w-4" /> Reject
                            </button>
                          </div>
                        ) : null}
                      </div>

                      <div className="mt-3 rounded-lg border border-slate-200 bg-white p-3">
                        <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                          <BookHeart className="h-4 w-4" />
                          Proposed payload
                        </div>
                        <QueryResultPanel title="Proposed payload" data={payload ?? {}} />
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}

            {decideM.isError ? (
              <p className="mt-3 text-sm text-red-700">Decision request failed.</p>
            ) : null}
            {decideM.isSuccess ? (
              <p className="mt-3 text-sm text-emerald-700">Decision recorded and queue refreshed.</p>
            ) : null}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
