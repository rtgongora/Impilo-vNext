"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { MusheXRailSafetyPanel } from "@/components/finance/MusheXRailSafetyPanel";
import { PayerOpsReviewsTable } from "@/components/finance/PayerOpsReviewsTable";
import {
  PayerOpsAdaptersTable,
  PayerOpsAttemptsTable,
  PayerOpsIntentPanel,
  PayerOpsReceiptsTable,
  PayerOpsSettlementsTable,
} from "@/components/finance/PayerOpsEntityPanels";
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
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="mb-4 rounded-xl border border-info/25 bg-info-soft/60 p-4 text-sm text-foreground">
          <p className="font-medium text-foreground">Payer claims queue</p>
          <p className="mt-1">
            Search and hand off into MusheX payer-claim detail from{" "}
            <Link href="/finance/payer-claims" className="text-primary hover:underline">
              /finance/payer-claims
            </Link>
            .
          </p>
        </div>

        <section className="mb-4 rounded-xl border border-border bg-card p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary-hover">
                Coverage-to-cash workflow
              </p>
              <h2 className="mt-1 text-base font-semibold text-foreground">Unified payer journey</h2>
              <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
                Use this route as the orchestration surface for coverage claims, remittance, settlement payout, and refund
                follow-up. The finance drill-down routes remain available for focused operator tasks.
              </p>
            </div>
            <span className="rounded-full border border-info/25 bg-info-soft px-3 py-1 text-xs font-medium text-primary-hover">
              role-gated payer ops
            </span>
          </div>
          <div className="mt-4 grid gap-3 md:grid-cols-4">
            {[
              // This page admits PAYER_OPS (SYSTEM_ADMIN, FINANCE, DEVELOPER); /coverage admits
              // only ADMIN, so a FINANCE payer operator was bounced home by their own console.
              { href: "/ruvimbo/payer", label: "Coverage claims", detail: "Eligibility, claims, preauth, remittance rows" },
              { href: "/finance/payer-claims", label: "Payer claims", detail: "MusheX claim review and queue handoff" },
              { href: "/finance/settlements", label: "Settlements", detail: "Run periods and release payouts" },
              { href: "/finance/refunds", label: "Refunds", detail: "Refund from a loaded payment intent" },
            ].map((step) => (
              <Link
                key={step.href}
                href={step.href}
                className="rounded-lg border border-border p-3 text-sm hover:border-indigo-300 hover:bg-info-soft/50"
              >
                <span className="font-medium text-foreground">{step.label}</span>
                <span className="mt-1 block text-xs text-muted-foreground">{step.detail}</span>
              </Link>
            ))}
          </div>
          <div className="mt-4 rounded-lg border border-border bg-background p-3 text-xs text-muted-foreground">
            <p>
              Active intent: <span className="font-mono text-foreground">{activeIntentId ?? "load a payment intent below"}</span>
            </p>
            <p className="mt-1">
              Current payer state: <span className="font-semibold text-foreground">{currentStage}</span>
            </p>
            <div className="mt-3 grid gap-2 md:grid-cols-6">
              {journeySteps.map((step) => (
                <div
                  key={step.key}
                  className={`rounded-lg border p-2 ${
                    step.done ? "border-success/25 bg-success-soft text-primary-hover" : "border-border bg-card text-foreground"
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
                <p className="mt-1 text-danger">Linked settlement lookup failed.</p>
              ) : settlementsQ.data != null ? (
                <div className="mt-3">
                  <PayerOpsSettlementsTable
                    data={settlementsQ.data}
                    isLoading={settlementsQ.isPending}
                    error={settlementsQ.error as Error | null}
                  />
                </div>
              ) : (
                <p className="mt-1">No linked settlement data returned yet.</p>
              )
            ) : null}
          </div>
        </section>

        <div className="max-w-4xl space-y-8">
          <MusheXRailSafetyPanel />
          <FinancePayerOpsReconciliationNotice />

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Payment intent</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET{" "}
              <code className="text-[11px]">/internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                type="text"
                value={intentInput}
                onChange={(e) => setIntentInput(e.target.value)}
                placeholder="Payment intent id"
                className="min-w-[220px] flex-1 rounded-lg border border-border px-3 py-2 text-sm font-mono"
                aria-label="Payment intent id"
              />
              <button
                type="button"
                className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
                onClick={() => setActiveIntentId(intentInput.trim() || undefined)}
              >
                Load intent
              </button>
              <button
                type="button"
                disabled={cancelM.isPending || !activeIntentId}
                className="rounded-lg border border-danger/28 px-3 py-2 text-sm text-red-800 hover:bg-danger-soft disabled:opacity-50"
                onClick={() => {
                  if (activeIntentId) cancelM.mutate(activeIntentId);
                }}
              >
                Cancel intent
              </button>
              <button
                type="button"
                disabled={slipM.isPending || !activeIntentId}
                className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
                onClick={() => {
                  if (activeIntentId) slipM.mutate(activeIntentId);
                }}
              >
                Issue remittance slip
              </button>
            </div>
            {intentQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading intent…
              </p>
            ) : intentQ.isError ? (
              <p className="mt-3 text-sm text-danger">Intent request failed.</p>
            ) : intentQ.data ? (
              <PayerOpsIntentPanel
                data={intentQ.data}
                isLoading={intentQ.isPending}
                error={intentQ.error as Error | null}
              />
            ) : (
              <p className="mt-3 text-xs text-muted-foreground">Load an intent to continue.</p>
            )}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Receipts</h3>
            <p className="text-xs text-muted-foreground">
              GET{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/receipts
              </code>
            </p>
            {receiptsQ.isLoading ? (
              <p className="mt-2 text-sm text-muted-foreground">Loading receipts…</p>
            ) : receiptsQ.isError ? (
              <p className="mt-2 text-sm text-danger">Receipts request failed.</p>
            ) : receiptsQ.data ? (
              <div className="mt-2">
                <PayerOpsReceiptsTable
                  data={receiptsQ.data}
                  isLoading={receiptsQ.isPending}
                  error={receiptsQ.error as Error | null}
                />
              </div>
            ) : (
              <p className="mt-2 text-xs text-muted-foreground">Uses the same intent id after load.</p>
            )}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Payment attempts (Phase 3)
            </h3>
            <p className="text-xs text-muted-foreground">
              POST{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/attempts
              </code>{" "}
              — honours the rail recorded on the intent. Use the SANDBOX rail in preview for
              governed simulated settlement; production live rails require operator enablement
              and a configured provider client per MusheX safety doctrine.
            </p>
            <div className="mt-3 flex flex-wrap gap-2 items-end">
              <label className="text-xs text-muted-foreground">
                Idempotency key (optional)
                <input
                  type="text"
                  value={attemptIdempotency}
                  onChange={(e) => setAttemptIdempotency(e.target.value)}
                  placeholder="e.g. retry-2026-05-13"
                  className="mt-1 block min-w-[200px] rounded-lg border border-border px-3 py-2 text-sm font-mono"
                  aria-label="Attempt idempotency key"
                />
              </label>
              <label className="text-xs text-muted-foreground">
                Reason (optional)
                <input
                  type="text"
                  value={attemptReason}
                  onChange={(e) => setAttemptReason(e.target.value)}
                  placeholder="audit note"
                  className="mt-1 block min-w-[200px] rounded-lg border border-border px-3 py-2 text-sm"
                  aria-label="Attempt reason"
                />
              </label>
              <button
                type="button"
                disabled={reselectM.isPending || !activeIntentId}
                className="rounded-lg border border-border bg-card px-3 py-2 text-sm font-medium text-foreground hover:bg-background disabled:opacity-50"
                onClick={() =>
                  reselectM.mutate({ intentId: activeIntentId!, reason: "preview-sandbox-rail" })
                }
              >
                {reselectM.isPending ? "Reselecting…" : "Reselect SANDBOX rail"}
              </button>
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
                className="mt-3 rounded-lg border border-amber-300 bg-warning-soft p-3 text-xs text-warning-foreground"
              >
                <p className="font-medium">Confirm initiate attempt</p>
                <p className="mt-1">
                  This calls MusheX <code>initiateAttempt</code> for intent{" "}
                  <code className="font-mono">{activeIntentId}</code>. Preview environments should
                  reselect the SANDBOX rail first; live rails move money only after operator
                  configuration and provider readiness checks pass.
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
                    className="rounded-lg border border-border px-3 py-2 text-xs hover:bg-neutral-100"
                    onClick={() => setShowInitiateConfirm(false)}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : null}
            {initiateM.isError ? (
              <p className="mt-2 text-xs text-danger">Initiate attempt request failed.</p>
            ) : null}
            {initiateM.data != null ? (
              <p className="mt-2 rounded-lg border border-success/25 bg-success-soft px-3 py-2 text-xs text-primary-hover">
                Payment attempt initiated. Refresh attempts history to inspect status.
              </p>
            ) : null}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Reselect rail
            </h3>
            <p className="text-xs text-muted-foreground">
              POST{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/attempts/reselect
              </code>{" "}
              — re-runs RailSelectionPolicy. No money moves; the previous selection is preserved on
              <code className="ml-1">metadata.rail_selection.history[]</code>.
            </p>
            <div className="mt-3 flex flex-wrap gap-2 items-end">
              <label className="text-xs text-muted-foreground flex-1 min-w-[220px]">
                Reason (optional)
                <input
                  type="text"
                  value={reselectReason}
                  onChange={(e) => setReselectReason(e.target.value)}
                  placeholder="why reselect"
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                  aria-label="Reselect reason"
                />
              </label>
              <button
                type="button"
                disabled={reselectM.isPending || !activeIntentId}
                className="rounded-lg border border-indigo-300 px-3 py-2 text-sm text-primary-hover hover:bg-info-soft disabled:opacity-50"
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
              <p className="mt-2 text-xs text-danger">Reselect request failed.</p>
            ) : null}
            {reselectM.data != null ? (
              <p className="mt-2 rounded-lg border border-info/25 bg-info-soft px-3 py-2 text-xs text-primary-hover">
                Rail reselection recorded on intent metadata.
              </p>
            ) : null}

            <h3 className="mt-6 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Attempts history
            </h3>
            <p className="text-xs text-muted-foreground">
              GET{" "}
              <code className="text-[11px]">
                /internal/v1/finance/payer-ops/payment-intents/&#123;intentId&#125;/attempts
              </code>
            </p>
            {attemptsQ.isLoading ? (
              <p className="mt-2 text-sm text-muted-foreground">Loading attempts…</p>
            ) : attemptsQ.isError ? (
              <p className="mt-2 text-sm text-danger">Attempts request failed.</p>
            ) : attemptsQ.data ? (
              <div className="mt-2">
                <PayerOpsAttemptsTable
                  data={attemptsQ.data}
                  isLoading={attemptsQ.isPending}
                  error={attemptsQ.error as Error | null}
                />
              </div>
            ) : (
              <p className="mt-2 text-xs text-muted-foreground">Load an intent to see its attempts.</p>
            )}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Remittance claim</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              POST <code className="text-[11px]">/internal/v1/finance/payer-ops/remittance/claim</code> — JSON body per
              MusheX contract.
            </p>
            <textarea
              className="mt-3 w-full min-h-[120px] rounded-lg border border-border p-2 font-mono text-xs"
              value={claimJson}
              onChange={(e) => setClaimJson(e.target.value)}
              aria-label="Remittance claim JSON"
            />
            {claimErr ? <p className="text-xs text-danger">{claimErr}</p> : null}
            <button
              type="button"
              disabled={claimM.isPending}
              className="mt-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
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
              <p className="mt-2 rounded-lg border border-success/25 bg-success-soft px-3 py-2 text-xs text-primary-hover">
                Remittance claim accepted. Continue with attempts and settlement reconciliation.
              </p>
            ) : null}
          </section>

          <section
            className="rounded-xl border border-success/25 bg-success-soft/50 p-5 shadow-sm"
            data-testid="payer-ops-production-rails"
          >
            <h2 className="text-sm font-semibold text-primary-hover">Production MusheX rails</h2>
            <p className="mt-1 text-xs text-primary-hover">
              Live payment rails (mobile money, card acquirers, bank switches) are configured in MusheX and surfaced here
              via adapter registry. Preview uses SANDBOX; production operators load adapters, confirm rail enablement, then
              initiate attempts against the intent&apos;s selected rail.
            </p>
            <p className="mt-2 text-xs text-primary-hover">
              Workflow: load intent → claim remittance → load adapters → reselect rail (SANDBOX in preview, live in
              production) → initiate attempt → reconcile receipts and settlements.
            </p>
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Adapters</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/finance/payer-ops/adapters</code>
            </p>
            <button
              type="button"
              className="mt-3 rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
              onClick={() => setAdaptersArmed(true)}
            >
              Load adapters
            </button>
            {adaptersQ.isLoading ? (
              <p className="mt-3 text-sm text-muted-foreground">Loading…</p>
            ) : adaptersQ.isError ? (
              <p className="mt-3 text-sm text-danger">Failed to load adapters.</p>
            ) : adaptersQ.data != null ? (
              <div className="mt-3">
                <PayerOpsAdaptersTable
                  data={adaptersQ.data}
                  isLoading={adaptersQ.isPending}
                  error={adaptersQ.error as Error | null}
                />
              </div>
            ) : null}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Fraud flags</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/finance/payer-ops/fraud-flags</code> (optional query keys
              forwarded to MusheX).
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-2">
              <label className="text-xs text-muted-foreground">
                page
                <input
                  className="mt-1 block rounded border border-border px-2 py-1 text-sm w-20"
                  value={fraudPage}
                  onChange={(e) => setFraudPage(e.target.value)}
                />
              </label>
              <label className="text-xs text-muted-foreground">
                size
                <input
                  className="mt-1 block rounded border border-border px-2 py-1 text-sm w-20"
                  value={fraudSize}
                  onChange={(e) => setFraudSize(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-border px-3 py-2 text-sm hover:bg-background"
                onClick={() => setFraudArmed(true)}
              >
                Fetch fraud flags
              </button>
            </div>
            {fraudQ.isLoading ? (
              <p className="mt-3 text-sm text-muted-foreground">Loading…</p>
            ) : fraudQ.isError ? (
              <p className="mt-3 text-sm text-danger">Request failed.</p>
            ) : fraudQ.data != null ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={fraudQ.data}
                  columns={[
                    { key: "flag", header: "Flag", fields: ["flagId", "id"] },
                    { key: "severity", header: "Severity", fields: ["severity", "riskLevel"] },
                    { key: "intent", header: "Intent", fields: ["intentId", "paymentIntentId"] },
                    { key: "reason", header: "Reason", fields: ["reason", "rule", "description"] },
                    { key: "status", header: "Status", fields: ["status"] },
                  ]}
                  isLoading={fraudQ.isPending}
                  error={fraudQ.error as Error | null}
                />
              </div>
            ) : fraudArmed ? null : (
              <p className="mt-3 text-xs text-muted-foreground">Click fetch to query.</p>
            )}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Ops reviews</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/finance/payer-ops/ops-reviews</code> — approve/reject
              below.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-2">
              <label className="text-xs text-muted-foreground">
                page
                <input
                  className="mt-1 block rounded border border-border px-2 py-1 text-sm w-20"
                  value={reviewPage}
                  onChange={(e) => setReviewPage(e.target.value)}
                />
              </label>
              <label className="text-xs text-muted-foreground">
                size
                <input
                  className="mt-1 block rounded border border-border px-2 py-1 text-sm w-20"
                  value={reviewSize}
                  onChange={(e) => setReviewSize(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-border px-3 py-2 text-sm hover:bg-background"
                onClick={() => setReviewsArmed(true)}
              >
                Fetch reviews
              </button>
            </div>
            {reviewsArmed ? (
              <div className="mt-3">
                <PayerOpsReviewsTable
                  data={reviewsQ.data}
                  isLoading={reviewsQ.isLoading}
                  error={reviewsQ.isError ? (reviewsQ.error as Error) : null}
                  pendingReviewId={
                    approveM.isPending || rejectM.isPending ? reviewId : undefined
                  }
                  onApprove={(id) => {
                    setReviewId(id);
                    try {
                      const body = JSON.parse(reviewNoteJson || "{}") as unknown;
                      approveM.mutate({ reviewId: id, body });
                    } catch {
                      /* invalid JSON body — operator must fix note field */
                    }
                  }}
                  onReject={(id) => {
                    setReviewId(id);
                    try {
                      const body = JSON.parse(reviewNoteJson || "{}") as unknown;
                      rejectM.mutate({ reviewId: id, body });
                    } catch {
                      /* invalid JSON body — operator must fix note field */
                    }
                  }}
                />
              </div>
            ) : (
              <p className="mt-3 text-xs text-muted-foreground">Click fetch to query.</p>
            )}

            <div className="mt-6 border-t border-border pt-4">
              <h3 className="text-xs font-semibold text-foreground">Approve / reject</h3>
              <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
                <input
                  type="text"
                  placeholder="review id"
                  value={reviewId}
                  onChange={(e) => setReviewId(e.target.value)}
                  className="rounded-lg border border-border px-3 py-2 text-sm font-mono min-w-[180px]"
                  aria-label="Ops review id"
                />
                <textarea
                  className="min-h-[72px] flex-1 rounded-lg border border-border p-2 font-mono text-xs min-w-[200px]"
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

          <p className="text-xs text-muted-foreground">
            <Link href="/finance/commerce-integrations" className="text-primary-hover hover:underline">
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
