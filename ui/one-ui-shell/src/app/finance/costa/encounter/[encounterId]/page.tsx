"use client";

/**
 * COSTA encounter timeline — read-only Phase 5 slice.
 * Route: /finance/costa/encounter/[encounterId] | pageTitle: "COSTA encounter timeline"
 *
 * Merges encounter-scoped COSTA + MusheX read signals already exposed by
 * the BFF into a single chronological view:
 *
 *   - Service-access decisions  (via `useServiceAccessDecisionsList`)
 *   - Cost events / charge sheet rows  (via `useCostaIntelCostEvents`)
 *   - Invoice lifecycle rows      (via billing-workspace lifecycle)
 *   - MusheX source-list intents  (via payer-ops source list)
 *   - MusheX settlements          (via settlement filter-by-intents)
 *   - Subsidy/write-off rows      (via billing-workspace subsidies list)
 *
 * No write actions. No fabricated rows. Reuses existing hooks.
 *
 * Doctrine: {@link ../../../../../../../docs/doctrine/mushex-gateway-neutrality.md}
 * Phase 5 design: {@link ../../../../../../../docs/design/phase-5-costa-encounter-timeline.md}
 */

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  ClipboardCheck,
  Clock,
  CreditCard,
  FileText,
  HandCoins,
  Layers,
  Loader2,
  Receipt,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { FINANCE_MUTATION_COLUMNS } from "@/lib/json-api/finance-table-columns";
import { useCostaIntelCostEvents, useCostaIntelInvoiceFromEstimate } from "@/hooks/queries/useCostaIntel";
import {
  useFinanceBillingInvoicesForEncounter,
  useFinanceBillingSubsidiesForEncounter,
} from "@/hooks/queries/useFinanceBillingWorkspace";
import { usePayerOpsPaymentIntentsBySource } from "@/hooks/queries/useFinancePayerOps";
import { useFinanceSettlementsByIntentIds } from "@/hooks/queries/useFinanceSettlements";
import {
  useServiceAccessDecisionsList,
  type ServiceAccessDecisionRow,
} from "@/hooks/queries/useServiceAccessDecisions";

type TimelineKind = "DECISION" | "COST_EVENT" | "INVOICE" | "INTENT" | "SETTLEMENT" | "SUBSIDY";

interface TimelineRow {
  kind: TimelineKind;
  timestamp: string | null;
  label: string;
  detail: string;
  badge: string;
  status?: string | null;
  amount?: string | null;
  raw: Record<string, unknown>;
}

function asString(value: unknown): string | null {
  if (value == null) return null;
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return null;
}

function extractRows(payload: unknown): Record<string, unknown>[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    if (Array.isArray(obj.data)) return obj.data as Record<string, unknown>[];
    if (Array.isArray(obj.items)) return obj.items as Record<string, unknown>[];
    if (Array.isArray(obj.content)) return obj.content as Record<string, unknown>[];
  }
  return [];
}

/**
 * Pick the most likely timestamp field from a cost event row. Cost events
 * carry an unknown shape today (the upstream costing-engine emits a JSON map);
 * we probe the conventional field names and fall back to `null` when none
 * matches rather than inventing a value.
 */
function pickEventTimestamp(row: Record<string, unknown>): string | null {
  return (
    asString(row.captured_at) ??
    asString(row.event_time) ??
    asString(row.eventTime) ??
    asString(row.recorded_at) ??
    asString(row.recordedAt) ??
    asString(row.created_at) ??
    asString(row.createdAt) ??
    null
  );
}

function pickEventLabel(row: Record<string, unknown>): string {
  return (
    asString(row.service_name) ??
    asString(row.serviceName) ??
    asString(row.item_name) ??
    asString(row.itemName) ??
    asString(row.description) ??
    asString(row.id) ??
    "Cost event"
  );
}

function pickEventAmount(row: Record<string, unknown>): string | null {
  return (
    asString(row.total_amount) ??
    asString(row.totalAmount) ??
    asString(row.amount) ??
    asString(row.cost) ??
    null
  );
}

function decisionToRow(d: ServiceAccessDecisionRow): TimelineRow {
  const label = d.requested_service_name?.trim() || "Service access decision";
  const detail = `Decision id ${d.service_access_decision_id}${
    d.billing_timing_mode ? ` · billing mode: ${d.billing_timing_mode}` : ""
  }`;
  return {
    kind: "DECISION",
    timestamp: d.decision_timestamp ?? null,
    label,
    detail,
    badge: d.access_status,
    status: d.access_status,
    amount:
      d.estimated_cost == null
        ? null
        : typeof d.estimated_cost === "number"
          ? d.estimated_cost.toFixed(2)
          : String(d.estimated_cost),
    raw: d as unknown as Record<string, unknown>,
  };
}

function costEventToRow(raw: unknown): TimelineRow | null {
  if (raw == null || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const ts = pickEventTimestamp(row);
  return {
    kind: "COST_EVENT",
    timestamp: ts,
    label: pickEventLabel(row),
    detail: asString(row.event_kind) ?? asString(row.eventKind) ?? "Cost event captured at point of care",
    badge: "CAPTURED",
    amount: pickEventAmount(row),
    raw: row,
  };
}

function invoiceToRow(raw: unknown): TimelineRow | null {
  if (raw == null || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const invoiceRaw = row.invoice;
  const invoice =
    invoiceRaw && typeof invoiceRaw === "object" && !Array.isArray(invoiceRaw)
      ? (invoiceRaw as Record<string, unknown>)
      : null;
  if (!invoice) return null;
  const invoiceId = asString(row.invoice_id) ?? asString(invoice.invoiceId) ?? "—";
  const status = asString(invoice.invoiceStatus) ?? "ISSUED";
  const mushexIntentId = asString(row.mushex_intent_id);
  const paymentStatus = asString(row.payment_status);
  const detailParts = [
    `Invoice ${invoiceId}`,
    mushexIntentId ? `MusheX intent: ${mushexIntentId}` : "MusheX intent: —",
    paymentStatus ? `Payment signal: ${paymentStatus}` : "Payment signal: —",
  ];
  return {
    kind: "INVOICE",
    timestamp: asString(invoice.issuedAt),
    label: `Invoice ${asString(invoice.invoiceNumber) ?? invoiceId}`,
    detail: detailParts.join(" · "),
    badge: status,
    status,
    amount: asString(invoice.total),
    raw: row,
  };
}

function intentToRow(raw: unknown): TimelineRow | null {
  if (raw == null || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const intentId = asString(row.intentId);
  if (!intentId) return null;
  const status = asString(row.status) ?? "CREATED";
  const sourceId = asString(row.sourceId);
  return {
    kind: "INTENT",
    timestamp: asString(row.createdAt),
    label: `Intent ${intentId}`,
    detail: `Source: ${asString(row.sourceType) ?? "—"}${sourceId ? `/${sourceId}` : ""}`,
    badge: status,
    status,
    amount: asString(row.amountTotal),
    raw: row,
  };
}

function settlementToRow(raw: unknown): TimelineRow | null {
  if (raw == null || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const settlementId = asString(row.settlementId);
  if (!settlementId) return null;
  const status = asString(row.status) ?? "COMPUTED";
  const totals = asString(row.totals);
  return {
    kind: "SETTLEMENT",
    timestamp: asString(row.createdAt),
    label: `Settlement ${settlementId}`,
    detail: totals ? `Totals: ${totals}` : "Settlement linkage row",
    badge: status,
    status,
    amount: null,
    raw: row,
  };
}

function subsidyToRow(raw: unknown): TimelineRow | null {
  if (raw == null || typeof raw !== "object") return null;
  const row = raw as Record<string, unknown>;
  const billId = asString(row.bill_id);
  if (!billId) return null;
  const subsidyAmount = asString(row.subsidy_amount);
  const writeOffAmount = asString(row.write_off_amount);
  const detailParts = [
    `Bill ${billId}`,
    `Write-off: ${writeOffAmount ?? "0.00"}`,
    `Status: ${asString(row.bill_status) ?? "—"}`,
  ];
  return {
    kind: "SUBSIDY",
    timestamp: asString(row.finalized_at) ?? asString(row.created_at),
    label: `Subsidy allocation ${billId}`,
    detail: detailParts.join(" · "),
    badge: "SUBSIDY",
    status: asString(row.bill_status),
    amount: subsidyAmount ?? "0.00",
    raw: row,
  };
}

function compareTimestamps(a: TimelineRow, b: TimelineRow): number {
  if (a.timestamp === b.timestamp) return 0;
  if (!a.timestamp) return 1; // null timestamps sink to the bottom
  if (!b.timestamp) return -1;
  return a.timestamp < b.timestamp ? -1 : 1;
}

function statusBadgeChrome(kind: TimelineKind, status: string | undefined): string {
  if (kind === "COST_EVENT") {
    return "bg-emerald-100 text-emerald-800 border-emerald-200";
  }
  if (kind === "INVOICE") {
    return "bg-indigo-100 text-indigo-800 border-indigo-200";
  }
  if (kind === "INTENT") {
    return "bg-violet-100 text-violet-800 border-violet-200";
  }
  if (kind === "SETTLEMENT") {
    return "bg-cyan-100 text-cyan-800 border-cyan-200";
  }
  if (kind === "SUBSIDY") {
    return "bg-amber-100 text-amber-800 border-amber-200";
  }
  const s = (status ?? "").toUpperCase();
  if (s === "GRANTED" || s === "APPROVED" || s === "AUTHORISED") {
    return "bg-emerald-100 text-emerald-800 border-emerald-200";
  }
  if (s === "DEFERRED" || s === "PENDING") {
    return "bg-amber-100 text-amber-800 border-amber-200";
  }
  if (s === "DENIED" || s === "REJECTED" || s === "BLOCKED") {
    return "bg-red-100 text-red-800 border-red-200";
  }
  if (s === "EXEMPT" || s === "WAIVED") {
    return "bg-sky-100 text-sky-800 border-sky-200";
  }
  return "bg-slate-100 text-slate-700 border-slate-200";
}

export default function CostaEncounterTimelinePage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const encounterId = typeof params.encounterId === "string" ? params.encounterId : "";
  const patientId = searchParams.get("patientId");
  const patientCpid = searchParams.get("patientCpid") ?? searchParams.get("cpid");

  const decisionsQ = useServiceAccessDecisionsList(encounterId);
  const costEventsQ = useCostaIntelCostEvents(encounterId, patientCpid);
  const invoicesQ = useFinanceBillingInvoicesForEncounter(encounterId, Boolean(encounterId));
  const subsidiesQ = useFinanceBillingSubsidiesForEncounter(encounterId, Boolean(encounterId));
  const issueInvoiceM = useCostaIntelInvoiceFromEstimate();
  const [invoiceReason, setInvoiceReason] = useState("");
  const [invoiceConfirmed, setInvoiceConfirmed] = useState(false);
  const [invoiceErr, setInvoiceErr] = useState<string | null>(null);
  const [invoiceJson, setInvoiceJson] = useState(() =>
    JSON.stringify({ cost_estimate_id: "", encounter_id: encounterId }, null, 2),
  );

  const decisionRows = (decisionsQ.data ?? []).map(decisionToRow);
  const costEventRows = (costEventsQ.data ?? [])
    .map(costEventToRow)
    .filter((row): row is TimelineRow => row !== null);
  const invoiceRows = extractRows(invoicesQ.data)
    .map(invoiceToRow)
    .filter((row): row is TimelineRow => row !== null);
  const sourceBillIds = invoiceRows
    .map((row) => asString(row.raw.bill_id))
    .filter((v): v is string => Boolean(v && v.trim().length > 0));
  const sourceIntentsQ = usePayerOpsPaymentIntentsBySource("COSTA_BILL", sourceBillIds, sourceBillIds.length > 0);
  const intentRows = extractRows(sourceIntentsQ.data)
    .map(intentToRow)
    .filter((row): row is TimelineRow => row !== null);
  const intentIds = intentRows
    .map((row) => asString(row.raw.intentId))
    .filter((v): v is string => Boolean(v && v.trim().length > 0));
  const settlementsQ = useFinanceSettlementsByIntentIds(intentIds, intentIds.length > 0);
  const settlementRows = extractRows(settlementsQ.data)
    .map(settlementToRow)
    .filter((row): row is TimelineRow => row !== null);
  const subsidyRows = extractRows(subsidiesQ.data)
    .map(subsidyToRow)
    .filter((row): row is TimelineRow => row !== null);

  const timeline = [...decisionRows, ...costEventRows, ...invoiceRows, ...intentRows, ...settlementRows, ...subsidyRows].sort(compareTimestamps);

  const handoffQuery = new URLSearchParams();
  if (patientId) handoffQuery.set("patientId", patientId);
  if (encounterId) handoffQuery.set("encounterId", encounterId);
  const handoffSuffix = handoffQuery.toString() ? `?${handoffQuery.toString()}` : "";

  const isLoading =
    decisionsQ.isLoading || costEventsQ.isLoading || invoicesQ.isLoading || sourceIntentsQ.isLoading || settlementsQ.isLoading || subsidiesQ.isLoading;
  const hasError = decisionsQ.isError || costEventsQ.isError || invoicesQ.isError || sourceIntentsQ.isError || settlementsQ.isError || subsidiesQ.isError;
  const isEmpty =
    !isLoading &&
    !hasError &&
    decisionRows.length === 0 &&
    costEventRows.length === 0 &&
    invoiceRows.length === 0 &&
    intentRows.length === 0 &&
    settlementRows.length === 0 &&
    subsidyRows.length === 0;

  return (
    <AppLayout>
      <PageShell
        title="COSTA encounter timeline"
        subtitle={
          encounterId
            ? `Read-only chronological view for encounter ${encounterId}`
            : "Open this page with an encounter id in the URL to view the timeline."
        }
      >
        <div className="mb-4">
          <Link
            href={`/finance/costa${handoffSuffix}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to COSTA hub
          </Link>
        </div>

        <div className="space-y-6">
          {/* Identity / context card */}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700">
                <Clock className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-slate-900">Encounter context</h2>
                <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-3">
                  <div>
                    <dt className="text-slate-500">Encounter</dt>
                    <dd className="font-mono text-[11px] text-slate-900">{encounterId || "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Patient</dt>
                    <dd className="font-mono text-[11px] text-slate-900">{patientId ?? "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Patient CPID</dt>
                    <dd className="font-mono text-[11px] text-slate-900">{patientCpid ?? "—"}</dd>
                  </div>
                </dl>
                <p className="mt-3 inline-flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] text-slate-600">
                  Timeline is read-only. Issue-invoice-from-estimate is the one actionable billing trigger
                  exposed here when a cost estimate id is available.
                </p>
              </div>
            </div>
          </section>

          {/* Chronological merged timeline */}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
                <Layers className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-slate-900">
                  Service-access decisions & charge sheet events
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  Combined timeline sourced from
                  <code className="mx-1 text-[10px]">
                    /internal/v1/finance/service-access-decisions?encounter_id=…
                  </code>
                  and
                  <code className="mx-1 text-[10px]">
                    /internal/v1/finance/costa-intel/cost-events?encounter_id=…
                  </code>
                  and
                  <code className="mx-1 text-[10px]">
                    /internal/v1/finance/billing-workspace/lifecycle/invoices?encounterId=…
                  </code>
                  ,
                  <code className="mx-1 text-[10px]">
                    /internal/v1/finance/payer-ops/payment-intents?sourceType=COSTA_BILL&sourceIds=…
                  </code>
                  and
                  <code className="mx-1 text-[10px]">
                    /internal/v1/finance/settlements?intentIds=…
                  </code>
                  and
                  <code className="mx-1 text-[10px]">
                    /internal/v1/finance/billing-workspace/lifecycle/subsidies?encounterId=…
                  </code>
                  . Sorted chronologically; rows without a timestamp sink to the bottom.
                </p>

                {isLoading && (
                  <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading timeline…
                  </p>
                )}
                {hasError && (
                  <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
                    <AlertCircle className="h-3.5 w-3.5" /> Could not load one or both timeline
                    sources from COSTA.
                  </p>
                )}
                {isEmpty && (
                  <p className="mt-3 text-xs text-slate-500">
                    No service-access decisions, cost events, invoices, intents, settlements, or subsidy rows returned for this encounter.
                  </p>
                )}

                {!isLoading && !hasError && timeline.length > 0 && (
                  <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                    <table className="w-full text-xs">
                      <thead className="bg-slate-50 text-slate-600">
                        <tr>
                          <th className="px-3 py-2 text-left font-medium">When</th>
                          <th className="px-3 py-2 text-left font-medium">Kind</th>
                          <th className="px-3 py-2 text-left font-medium">Label</th>
                          <th className="px-3 py-2 text-right font-medium">Amount</th>
                          <th className="px-3 py-2 text-left font-medium">Status</th>
                          <th className="px-3 py-2 text-left font-medium">Detail</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {timeline.map((row, idx) => (
                          <tr
                            key={`${row.kind}-${idx}-${row.timestamp ?? "no-ts"}`}
                            className="bg-white"
                          >
                            <td className="px-3 py-2 text-slate-500 font-mono text-[10px]">
                              {row.timestamp ?? "—"}
                            </td>
                            <td className="px-3 py-2">
                              <span className="inline-flex items-center gap-1 text-[10px] font-medium text-slate-700">
                                {row.kind === "DECISION" ? (
                                  <ClipboardCheck className="h-3 w-3" />
                                ) : row.kind === "COST_EVENT" ? (
                                  <FileText className="h-3 w-3" />
                                ) : row.kind === "INTENT" ? (
                                  <CreditCard className="h-3 w-3" />
                                ) : row.kind === "SETTLEMENT" ? (
                                  <Wallet className="h-3 w-3" />
                                ) : row.kind === "SUBSIDY" ? (
                                  <HandCoins className="h-3 w-3" />
                                ) : (
                                  <Receipt className="h-3 w-3" />
                                )}
                                {row.kind === "DECISION"
                                  ? "Decision"
                                  : row.kind === "COST_EVENT"
                                    ? "Cost event"
                                    : row.kind === "INTENT"
                                      ? "Intent"
                                      : row.kind === "SETTLEMENT"
                                        ? "Settlement"
                                      : row.kind === "SUBSIDY"
                                        ? "Subsidy"
                                        : "Invoice"}
                              </span>
                            </td>
                            <td className="px-3 py-2 text-slate-900">{row.label}</td>
                            <td className="px-3 py-2 text-right text-slate-900">
                              {row.amount ?? "—"}
                            </td>
                            <td className="px-3 py-2">
                              <span
                                className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-medium ${statusBadgeChrome(row.kind, row.status ?? undefined)}`}
                              >
                                {row.badge}
                              </span>
                            </td>
                            <td className="px-3 py-2 text-slate-600">{row.detail}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </section>

          {/* Actionable billing trigger — issue invoice from cost estimate */}
          <section className="rounded-xl border border-amber-200 bg-amber-50/40 p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Billing trigger — issue invoice from estimate</h2>
            <p className="mt-1 text-xs text-slate-600">
              POST{" "}
              <code className="text-[10px]">
                /internal/v1/finance/costa-intel/invoices/from-cost-estimate
              </code>
              . Use when charge sheet rows and a cost estimate id are ready for invoice issuance on this
              encounter.
            </p>
            <textarea
              aria-label="Invoice-from-estimate JSON"
              className="mt-3 min-h-[120px] w-full rounded-lg border border-slate-200 p-2 font-mono text-xs"
              value={invoiceJson}
              onChange={(e) => setInvoiceJson(e.target.value)}
            />
            <input
              type="text"
              placeholder="Reason for this write (required)"
              className="mt-2 w-full rounded-lg border border-slate-200 px-3 py-2 text-xs"
              value={invoiceReason}
              onChange={(e) => setInvoiceReason(e.target.value)}
            />
            <label className="mt-2 flex items-center gap-2 text-xs text-slate-700">
              <input
                type="checkbox"
                checked={invoiceConfirmed}
                onChange={(e) => setInvoiceConfirmed(e.target.checked)}
              />
              I confirm this is an intentional billing write for this encounter.
            </label>
            {invoiceErr ? <p className="mt-2 text-xs text-red-700">{invoiceErr}</p> : null}
            <button
              type="button"
              disabled={issueInvoiceM.isPending || !invoiceConfirmed || !invoiceReason.trim()}
              className="mt-2 rounded-lg bg-amber-700 px-3 py-2 text-xs font-medium text-white hover:bg-amber-800 disabled:opacity-50"
              onClick={() => {
                try {
                  const parsed = JSON.parse(invoiceJson) as Record<string, unknown>;
                  if (!parsed.encounter_id && encounterId) parsed.encounter_id = encounterId;
                  parsed.audit_reason = invoiceReason.trim();
                  setInvoiceErr(null);
                  issueInvoiceM.mutate(parsed);
                } catch {
                  setInvoiceErr("Invalid JSON payload.");
                }
              }}
            >
              {issueInvoiceM.isPending ? "Issuing invoice…" : "Issue invoice from estimate"}
            </button>
            {issueInvoiceM.data || issueInvoiceM.isError ? (
              <div className="mt-3">
                <JsonApiDataTable
                  data={issueInvoiceM.data}
                  columns={FINANCE_MUTATION_COLUMNS}
                  isLoading={issueInvoiceM.isPending}
                  error={issueInvoiceM.error as Error | null}
                  emptyTitle="Invoice issued"
                />
              </div>
            ) : null}
          </section>

          {/* Honest gap card — what we don't show yet and why */}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
                <CreditCard className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-slate-900">
                  Invoices, payments, and settlement
                </h2>
                <p className="mt-1 text-xs text-slate-500">
                  Most read linkage is now surfaced. The remaining gap is settlement detail
                  granularity beyond per-intent filtered settlement headers.
                </p>
                <ul className="mt-3 list-disc space-y-1 pl-5 text-xs text-slate-600">
                  <li>
                    <span className="font-medium text-slate-800">Invoice and intent linkage</span>{" "}
                    — now surfaced through billing-workspace invoice lifecycle rows
                    (including MusheX intent id + payment-status signal where present).
                  </li>
                  <li>
                    <span className="font-medium text-slate-800">Settlement detail granularity</span> —
                    timeline shows settlement headers linked by intent filter; per-batch/per-item settlement
                    drill-down stays on reconciliation surfaces.
                  </li>
                </ul>
                <div className="mt-3 flex flex-wrap gap-3 text-xs">
                  <Link
                    href={`/finance/billing${handoffSuffix}`}
                    className="inline-flex items-center gap-1 font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    <Receipt className="h-3 w-3" /> Open billing →
                  </Link>
                  <Link
                    href={`/finance/payments${handoffSuffix}`}
                    className="inline-flex items-center gap-1 font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    <Wallet className="h-3 w-3" /> Open payments →
                  </Link>
                  <Link
                    href="/finance/reconciliation"
                    className="inline-flex items-center gap-1 font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    <Layers className="h-3 w-3" /> Open reconciliation →
                  </Link>
                  <Link
                    href={`/finance/claims${handoffSuffix}`}
                    className="inline-flex items-center gap-1 font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    <ClipboardCheck className="h-3 w-3" /> Open claims →
                  </Link>
                </div>
              </div>
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
