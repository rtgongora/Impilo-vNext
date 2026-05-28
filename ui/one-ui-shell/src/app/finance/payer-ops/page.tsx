"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FinancePayerOpsReconciliationNotice } from "@/components/FinanceAccessNotice";
import { useFinanceSettlementsByIntentIds } from "@/hooks/queries/useFinanceSettlements";
import {
  usePayerOpsAdapters,
  usePayerOpsAttempts,
  usePayerOpsCancelIntent,
  usePayerOpsClaimRemittance,
  usePayerOpsFraudFlags,
  usePayerOpsInitiateAttempt,
  usePayerOpsIssueRemittanceSlip,
  usePayerOpsPaymentIntent,
  usePayerOpsReceipts,
  usePayerOpsReselect,
  usePayerOpsReviewApprove,
  usePayerOpsReviewReject,
  usePayerOpsReviews,
} from "@/hooks/queries/useFinancePayerOps";

const DEFAULT_CLAIM_JSON = "{\n  \"slipId\": \"\",\n  \"tenantId\": \"\"\n}\n";

function collectionCount(value: unknown): number {
  if (Array.isArray(value)) return value.length;
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    for (const key of ["data", "items", "content", "receipts", "attempts", "settlements"]) {
      const nested = record[key];
      if (Array.isArray(nested)) return nested.length;
    }
  }
  return value == null ? 0 : 1;
}

function hasPayload(value: unknown): boolean {
  return value != null && collectionCount(value) > 0;
}

export default function FinancePayerOpsPage() {
  const [intentInput, setIntentInput] = useState("");
  const [activeIntentId, setActiveIntentId] = useState<string | undefined>();
  const intentQ = usePayerOpsPaymentIntent(activeIntentId);
  const receiptsQ = usePayerOpsReceipts(activeIntentId);
  const settlementsQ = useFinanceSettlementsByIntentIds(activeIntentId ? [activeIntentId] : [], Boolean(activeIntentId));

  const cancelM = usePayerOpsCancelIntent();
  const slipM = usePayerOpsIssueRemittanceSlip();
  const claimM = usePayerOpsClaimRemittance();
  const initiateM = usePayerOpsInitiateAttempt();
  const reselectM = usePayerOpsReselect();
  const attemptsQ = usePayerOpsAttempts(activeIntentId);

  const [attemptIdempotency, setAttemptIdempotency] = useState("");
  const [attemptReason, setAttemptReason] = useState("");
  const [reselectReason, setReselectReason] = useState("");
  const [showInitiateConfirm, setShowInitiateConfirm] = useState(false);

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
  const journeySteps = [
    { key: "intent", label: "Intent", done: Boolean(intentQ.data), detail: activeIntentId ?? "not loaded" },
    { key: "claim", label: "Remittance claim", done: Boolean(claimM.data), detail: claimM.isError ? "claim failed" : "awaiting claim" },
    { key: "attempts", label: "Attempts", done: hasPayload(attemptsQ.data), detail: `${collectionCount(attemptsQ.data)} attempts` },
    { key: "receipts", label: "Receipts", done: hasPayload(receiptsQ.data), detail: `${collectionCount(receiptsQ.data)} receipts` },
    { key: "settlement", label: "Settlement", done: hasPayload(settlementsQ.data), detail: `${collectionCount(settlementsQ.data)} linked rows` },
    { key: "refund", label: "Refund review", done: false, detail: "drill-down under refunds route" },
  ];
  const currentStage = journeySteps.find((step) => !step.done)?.label ?? "Reconciled";

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

        <div className="mb-4 rounded-xl border border-indigo-200 bg-indigo-50/60 p-4 text-sm text-slate-700">
          <p className="font-medium text-slate-900">Payer claims queue</p>
          <p className="mt-1">
            Search and hand off into MusheX payer-claim detail from{" "}
            <Link href="/finance/payer-claims" className="text-impilo-600 hover:underline">
              /finance/payer-claims
            </Link>
            .
          </p>
        </div>

        <section className="mb-4 rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-indigo-700">
                Coverage-to-cash workflow
              </p>
              <h2 className="mt-1 text-base font-semibold text-slate-900">Unified payer journey</h2>
              <p className="mt-1 max-w-3xl text-sm text-slate-600">
                Use this route as the orchestration surface for coverage claims, remittance, settlement payout, and refund
                follow-up. The finance drill-down routes remain available for focused operator tasks.
              </p>
            </div>
            <span className="rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-xs font-medium text-indigo-700">
              role-gated payer ops
            </span>
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            {[
              { href: "/coverage", label: "Coverage claims", detail: "Eligibility, claims, preauth, remittance rows" },
              { href: "/finance/payer-claims", label: "Payer claims", detail: "MusheX claim review and queue handoff" },
              { href: "/finance/settlements", label: "Settlements", detail: "Run periods and release payouts" },
              { href: "/finance/refunds", label: "Refunds", detail: "Refund from a loaded payment intent" },
            ].map((step) => (
              <Link
                key={step.href}
                href={step.href}
                className="rounded-lg border border-slate-200 p-3 text-sm hover:border-indigo-300 hover:bg-indigo-50/50"
              >
                <span className="font-medium text-slate-900">{step.label}</span>
                <span className="mt-1 block text-xs text-slate-500">{step.detail}</span>
              </Link>
            ))}
          </div>
          <div className="mt-4 rounded-lg border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600">
            <p>
              Active intent: <span className="font-mono text-slate-800">{activeIntentId ?? "load a payment intent below"}</span>
            </p>
            <p className="mt-1">
              Current payer state: <span className="font-semibold text-slate-900">{currentStage}</span>
            </p>
            <div className="mt-3 grid gap-2 md:grid-cols-6">
              {journeySteps.map((step) => (
                <div
                  key={step.key}
                  className={`rounded-lg border p-2 ${
                    step.done ? "border-emerald-200 bg-emerald-50 text-emerald-900" : "border-slate-200 bg-white text-slate-700"
                  }`}
                >
                  <p className="font-medium">{step.label}</p>
                  <p className="mt-1 text-[11px]">{step.detail}</p>
                </div>
              ))}
            </div>
            {activeIntentId ? (
              settlementsQ.isLoading ? (
                <p className="mt-1">Loading settlements linked to this intent...</p>
              ) : settlementsQ.isError ? (
                <p className="mt-1 text-red-700">Linked settlement lookup failed.</p>
              ) : settlementsQ.data != null ? (
                <QueryResultPanel title="Settlements Q" isPending={settlementsQ.isPending} isLoading={settlementsQ.isPending} isError={settlementsQ.isError} error={settlementsQ.error} data={settlementsQ.data} />
              ) : (
                <p className="mt-1">No linked settlement data returned yet.</p>
              )
            ) : null}
          </div>
        </section>

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
              <QueryResultPanel title="Intent Q" isPending={intentQ.isPending} isLoading={intentQ.isPending} isError={intentQ.isError} error={intentQ.error} data={intentQ.data} />
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
              <QueryResultPanel title="Receipts Q" isPending={receiptsQ.isPending} isLoading={receiptsQ.isPending} isError={receiptsQ.isError} error={receiptsQ.error} data={receiptsQ.data} />
            ) : (
              <p className="mt-2 text-xs text-slate-500">Uses the same intent id after load.</p>
            )}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Payment attempts (Phase 3)
            </h3>
            <p className="text-xs text-slate-500">
              POST{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/attempts
              </code>{" "}
              — honours the rail recorded on the intent. The MusheX safety gate prevents real
              providers from being called until an operator opts the rail in
              <em> and</em> a real provider client is wired in
              (every adapter today is a stub).
            </p>
            <div className="mt-3 flex flex-wrap gap-2 items-end">
              <label className="text-xs text-slate-600">
                Idempotency key (optional)
                <input
                  type="text"
                  value={attemptIdempotency}
                  onChange={(e) => setAttemptIdempotency(e.target.value)}
                  placeholder="e.g. retry-2026-05-13"
                  className="mt-1 block min-w-[200px] rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                  aria-label="Attempt idempotency key"
                />
              </label>
              <label className="text-xs text-slate-600">
                Reason (optional)
                <input
                  type="text"
                  value={attemptReason}
                  onChange={(e) => setAttemptReason(e.target.value)}
                  placeholder="audit note"
                  className="mt-1 block min-w-[200px] rounded-lg border border-slate-200 px-3 py-2 text-sm"
                  aria-label="Attempt reason"
                />
              </label>
              <button
                type="button"
                disabled={initiateM.isPending || !activeIntentId}
                className="rounded-lg bg-emerald-700 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-50"
                onClick={() => setShowInitiateConfirm(true)}
              >
                Initiate attempt…
              </button>
            </div>
            {showInitiateConfirm ? (
              <div
                role="dialog"
                aria-modal="true"
                aria-label="Confirm initiate payment attempt"
                className="mt-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900"
              >
                <p className="font-medium">Confirm initiate attempt</p>
                <p className="mt-1">
                  This calls MusheX <code>initiateAttempt</code> for intent{" "}
                  <code className="font-mono">{activeIntentId}</code>. The safety gate will refuse
                  to invoke a real provider until both the operator config flags AND the
                  adapter&apos;s <code>liveCapable()</code> are true. Today, every adapter is a
                  stub, so attempts on real rails persist as <code>INITIATED</code>+<code>BLOCKED_NOT_LIVE_CAPABLE</code>
                  with no money movement.
                </p>
                <div className="mt-2 flex gap-2">
                  <button
                    type="button"
                    className="rounded-lg bg-emerald-700 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-800"
                    onClick={() => {
                      if (!activeIntentId) {
                        setShowInitiateConfirm(false);
                        return;
                      }
                      initiateM.mutate({
                        intentId: activeIntentId,
                        idempotencyKey: attemptIdempotency.trim() || undefined,
                        reason: attemptReason.trim() || undefined,
                      });
                      setShowInitiateConfirm(false);
                    }}
                  >
                    Confirm and initiate
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-slate-200 px-3 py-2 text-xs hover:bg-slate-100"
                    onClick={() => setShowInitiateConfirm(false)}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : null}
            {initiateM.isError ? (
              <p className="mt-2 text-xs text-red-700">Initiate attempt request failed.</p>
            ) : null}
            {initiateM.data != null ? (
              <QueryResultPanel title="Initiate M" isPending={initiateM.isPending} isLoading={initiateM.isPending} isError={initiateM.isError} error={initiateM.error} data={initiateM.data} />
            ) : null}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Reselect rail
            </h3>
            <p className="text-xs text-slate-500">
              POST{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/attempts/reselect
              </code>{" "}
              — re-runs RailSelectionPolicy. No money moves; the previous selection is preserved on
              <code className="ml-1">metadata.rail_selection.history[]</code>.
            </p>
            <div className="mt-3 flex flex-wrap gap-2 items-end">
              <label className="text-xs text-slate-600 flex-1 min-w-[220px]">
                Reason (optional)
                <input
                  type="text"
                  value={reselectReason}
                  onChange={(e) => setReselectReason(e.target.value)}
                  placeholder="why reselect"
                  className="mt-1 block w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                  aria-label="Reselect reason"
                />
              </label>
              <button
                type="button"
                disabled={reselectM.isPending || !activeIntentId}
                className="rounded-lg border border-indigo-300 px-3 py-2 text-sm text-indigo-800 hover:bg-indigo-50 disabled:opacity-50"
                onClick={() => {
                  if (!activeIntentId) return;
                  reselectM.mutate({
                    intentId: activeIntentId,
                    reason: reselectReason.trim() || undefined,
                  });
                }}
              >
                Reselect rail
              </button>
            </div>
            {reselectM.isError ? (
              <p className="mt-2 text-xs text-red-700">Reselect request failed.</p>
            ) : null}
            {reselectM.data != null ? (
              <QueryResultPanel title="Reselect M" isPending={reselectM.isPending} isLoading={reselectM.isPending} isError={reselectM.isError} error={reselectM.error} data={reselectM.data} />
            ) : null}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Attempts history
            </h3>
            <p className="text-xs text-slate-500">
              GET{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/attempts
              </code>
            </p>
            {attemptsQ.isLoading ? (
              <p className="mt-2 text-sm text-slate-500">Loading attempts…</p>
            ) : attemptsQ.isError ? (
              <p className="mt-2 text-sm text-red-700">Attempts request failed.</p>
            ) : attemptsQ.data ? (
              <QueryResultPanel title="Attempts Q" isPending={attemptsQ.isPending} isLoading={attemptsQ.isPending} isError={attemptsQ.isError} error={attemptsQ.error} data={attemptsQ.data} />
            ) : (
              <p className="mt-2 text-xs text-slate-500">Load an intent to see its attempts.</p>
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
              <QueryResultPanel title="Claim M" isPending={claimM.isPending} isLoading={claimM.isPending} isError={claimM.isError} error={claimM.error} data={claimM.data} />
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
              <QueryResultPanel title="Adapters Q" isPending={adaptersQ.isPending} isLoading={adaptersQ.isPending} isError={adaptersQ.isError} error={adaptersQ.error} data={adaptersQ.data} />
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
              <QueryResultPanel title="Fraud Q" isPending={fraudQ.isPending} isLoading={fraudQ.isPending} isError={fraudQ.isError} error={fraudQ.error} data={fraudQ.data} />
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
              <QueryResultPanel title="Reviews Q" isPending={reviewsQ.isPending} isLoading={reviewsQ.isPending} isError={reviewsQ.isError} error={reviewsQ.error} data={reviewsQ.data} />
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
