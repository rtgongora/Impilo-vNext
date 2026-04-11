"use client";

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FinancePayerOpsReconciliationNotice } from "@/components/FinanceAccessNotice";
import {
  usePayerOpsAdapters,
  usePayerOpsCancelIntent,
  usePayerOpsClaimRemittance,
  usePayerOpsFraudFlags,
  usePayerOpsIssueRemittanceSlip,
  usePayerOpsPaymentIntent,
  usePayerOpsReceipts,
  usePayerOpsReviewApprove,
  usePayerOpsReviewReject,
  usePayerOpsReviews,
} from "@/hooks/queries/useFinancePayerOps";

const DEFAULT_CLAIM_JSON = "{\n  \"slipId\": \"\",\n  \"tenantId\": \"\"\n}\n";

export default function FinancePayerOpsPage() {
  const [intentInput, setIntentInput] = useState("");
  const [activeIntentId, setActiveIntentId] = useState<string | undefined>();
  const intentQ = usePayerOpsPaymentIntent(activeIntentId);
  const receiptsQ = usePayerOpsReceipts(activeIntentId);

  const cancelM = usePayerOpsCancelIntent();
  const slipM = usePayerOpsIssueRemittanceSlip();
  const claimM = usePayerOpsClaimRemittance();

  const [adaptersArmed, setAdaptersArmed] = useState(false);
  const adaptersQ = usePayerOpsAdapters(adaptersArmed);

  const [fraudPage, setFraudPage] = useState("0");
  const [fraudSize, setFraudSize] = useState("20");
  const [fraudArmed, setFraudArmed] = useState(false);
  const fraudQ = usePayerOpsFraudFlags(
    {
      ...(fraudPage ? { page: fraudPage } : {}),
      ...(fraudSize ? { size: fraudSize } : {}),
    },
    fraudArmed,
  );

  const [reviewPage, setReviewPage] = useState("0");
  const [reviewSize, setReviewSize] = useState("20");
  const [reviewsArmed, setReviewsArmed] = useState(false);
  const reviewsQ = usePayerOpsReviews(
    {
      ...(reviewPage ? { page: reviewPage } : {}),
      ...(reviewSize ? { size: reviewSize } : {}),
    },
    reviewsArmed,
  );

  const [reviewId, setReviewId] = useState("");
  const [reviewNoteJson, setReviewNoteJson] = useState("{}");
  const approveM = usePayerOpsReviewApprove();
  const rejectM = usePayerOpsReviewReject();

  const [claimJson, setClaimJson] = useState(DEFAULT_CLAIM_JSON);
  const [claimErr, setClaimErr] = useState<string | null>(null);

  return (
    <AppLayout>
      <PageShell
        title="Payer ops"
        subtitle="Payment intents, remittance, adapters, fraud flags, and ops reviews — MusheX upstream via Experience BFF."
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="max-w-4xl space-y-8">
          <FinancePayerOpsReconciliationNotice />

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Payment intent</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET{" "}
              <code className="text-[11px]">/internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                type="text"
                value={intentInput}
                onChange={(e) => setIntentInput(e.target.value)}
                placeholder="Payment intent id"
                className="min-w-[220px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                aria-label="Payment intent id"
              />
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
                onClick={() => setActiveIntentId(intentInput.trim() || undefined)}
              >
                Load intent
              </button>
              <button
                type="button"
                disabled={cancelM.isPending || !activeIntentId}
                className="rounded-lg border border-red-200 px-3 py-2 text-sm text-red-800 hover:bg-red-50 disabled:opacity-50"
                onClick={() => {
                  if (activeIntentId) cancelM.mutate(activeIntentId);
                }}
              >
                Cancel intent
              </button>
              <button
                type="button"
                disabled={slipM.isPending || !activeIntentId}
                className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
                onClick={() => {
                  if (activeIntentId) slipM.mutate(activeIntentId);
                }}
              >
                Issue remittance slip
              </button>
            </div>
            {intentQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading intent…
              </p>
            ) : intentQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Intent request failed.</p>
            ) : intentQ.data ? (
              <pre className="mt-3 max-h-56 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(intentQ.data, null, 2)}
              </pre>
            ) : (
              <p className="mt-3 text-xs text-slate-500">Load an intent to continue.</p>
            )}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500">Receipts</h3>
            <p className="text-xs text-slate-500">
              GET{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/receipts
              </code>
            </p>
            {receiptsQ.isLoading ? (
              <p className="mt-2 text-sm text-slate-500">Loading receipts…</p>
            ) : receiptsQ.isError ? (
              <p className="mt-2 text-sm text-red-700">Receipts request failed.</p>
            ) : receiptsQ.data ? (
              <pre className="mt-2 max-h-48 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(receiptsQ.data, null, 2)}
              </pre>
            ) : (
              <p className="mt-2 text-xs text-slate-500">Uses the same intent id after load.</p>
            )}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Remittance claim</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST <code className="text-[11px]">/internal/v1/finance/payer-ops/remittance/claim</code> — JSON body per
              MusheX contract.
            </p>
            <textarea
              className="mt-3 w-full min-h-[120px] rounded-lg border border-slate-200 p-2 font-mono text-xs"
              value={claimJson}
              onChange={(e) => setClaimJson(e.target.value)}
              aria-label="Remittance claim JSON"
            />
            {claimErr ? <p className="text-xs text-red-700">{claimErr}</p> : null}
            <button
              type="button"
              disabled={claimM.isPending}
              className="mt-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              onClick={() => {
                let body: unknown;
                try {
                  body = JSON.parse(claimJson) as unknown;
                } catch {
                  setClaimErr("Invalid JSON.");
                  return;
                }
                setClaimErr(null);
                claimM.mutate(body);
              }}
            >
              {claimM.isPending ? "Posting…" : "Post claim"}
            </button>
            {claimM.data != null ? (
              <pre className="mt-3 max-h-40 overflow-auto rounded-lg border bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(claimM.data, null, 2)}
              </pre>
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Adapters</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/payer-ops/adapters</code>
            </p>
            <button
              type="button"
              className="mt-3 rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
              onClick={() => setAdaptersArmed(true)}
            >
              Load adapters
            </button>
            {adaptersQ.isLoading ? (
              <p className="mt-3 text-sm text-slate-500">Loading…</p>
            ) : adaptersQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Failed to load adapters.</p>
            ) : adaptersQ.data != null ? (
              <pre className="mt-3 max-h-56 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(adaptersQ.data, null, 2)}
              </pre>
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Fraud flags</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/payer-ops/fraud-flags</code> (optional query keys
              forwarded to MusheX).
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-2">
              <label className="text-xs text-slate-600">
                page
                <input
                  className="mt-1 block rounded border border-slate-200 px-2 py-1 text-sm w-20"
                  value={fraudPage}
                  onChange={(e) => setFraudPage(e.target.value)}
                />
              </label>
              <label className="text-xs text-slate-600">
                size
                <input
                  className="mt-1 block rounded border border-slate-200 px-2 py-1 text-sm w-20"
                  value={fraudSize}
                  onChange={(e) => setFraudSize(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50"
                onClick={() => setFraudArmed(true)}
              >
                Fetch fraud flags
              </button>
            </div>
            {fraudQ.isLoading ? (
              <p className="mt-3 text-sm text-slate-500">Loading…</p>
            ) : fraudQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Request failed.</p>
            ) : fraudQ.data != null ? (
              <pre className="mt-3 max-h-56 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(fraudQ.data, null, 2)}
              </pre>
            ) : fraudArmed ? null : (
              <p className="mt-3 text-xs text-slate-500">Click fetch to query.</p>
            )}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Ops reviews</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/payer-ops/ops-reviews</code> — approve/reject
              below.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-2">
              <label className="text-xs text-slate-600">
                page
                <input
                  className="mt-1 block rounded border border-slate-200 px-2 py-1 text-sm w-20"
                  value={reviewPage}
                  onChange={(e) => setReviewPage(e.target.value)}
                />
              </label>
              <label className="text-xs text-slate-600">
                size
                <input
                  className="mt-1 block rounded border border-slate-200 px-2 py-1 text-sm w-20"
                  value={reviewSize}
                  onChange={(e) => setReviewSize(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50"
                onClick={() => setReviewsArmed(true)}
              >
                Fetch reviews
              </button>
            </div>
            {reviewsQ.isLoading ? (
              <p className="mt-3 text-sm text-slate-500">Loading…</p>
            ) : reviewsQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Request failed.</p>
            ) : reviewsQ.data != null ? (
              <pre className="mt-3 max-h-48 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(reviewsQ.data, null, 2)}
              </pre>
            ) : reviewsArmed ? null : (
              <p className="mt-3 text-xs text-slate-500">Click fetch to query.</p>
            )}

            <div className="mt-6 border-t border-slate-100 pt-4">
              <h3 className="text-xs font-semibold text-slate-700">Approve / reject</h3>
              <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
                <input
                  type="text"
                  placeholder="review id"
                  value={reviewId}
                  onChange={(e) => setReviewId(e.target.value)}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono min-w-[180px]"
                  aria-label="Ops review id"
                />
                <textarea
                  className="min-h-[72px] flex-1 rounded-lg border border-slate-200 p-2 font-mono text-xs min-w-[200px]"
                  value={reviewNoteJson}
                  onChange={(e) => setReviewNoteJson(e.target.value)}
                  aria-label="Optional JSON body for approve or reject"
                />
              </div>
              <div className="mt-2 flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={approveM.isPending || !reviewId.trim()}
                  className="rounded-lg bg-emerald-700 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-50"
                  onClick={() => {
                    let body: unknown = {};
                    try {
                      body = JSON.parse(reviewNoteJson || "{}") as unknown;
                    } catch {
                      return;
                    }
                    approveM.mutate({ reviewId: reviewId.trim(), body });
                  }}
                >
                  Approve
                </button>
                <button
                  type="button"
                  disabled={rejectM.isPending || !reviewId.trim()}
                  className="rounded-lg bg-red-700 px-3 py-2 text-sm font-medium text-white hover:bg-red-800 disabled:opacity-50"
                  onClick={() => {
                    let body: unknown = {};
                    try {
                      body = JSON.parse(reviewNoteJson || "{}") as unknown;
                    } catch {
                      return;
                    }
                    rejectM.mutate({ reviewId: reviewId.trim(), body });
                  }}
                >
                  Reject
                </button>
              </div>
            </div>
          </section>

          <p className="text-xs text-slate-500">
            <Link href="/finance/commerce-integrations" className="text-indigo-700 hover:underline">
              Integration map
            </Link>{" "}
            — there is still no generic <code className="text-[11px]">/internal/v1/mushex/…</code> aggregate; paths are
            typed per finance subdomain.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
