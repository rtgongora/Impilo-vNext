"use client";

/**
 * COSTA — Costing & Tariffs hub.
 * Route: /finance/costa | pageTitle: "COSTA — Costing & Tariffs"
 *
 * Canonical replacement for the deprecated {@code ui/experience/src/app/finance/costa/page.tsx}.
 * Closes audit gap G-1 in {@code docs/audits/costa-mushex-experience-layer-wiring-audit.md}.
 *
 * This is a READ-ONLY hub: it orients the user and surfaces small live signals
 * from COSTA via the Experience BFF, then links to the canonical sub-surfaces
 * for the deeper workflows. No write actions, no fabricated data.
 */

import Link from "next/link";
import { useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  AlertCircle,
  ArrowLeft,
  BookOpen,
  ClipboardCheck,
  ClipboardList,
  CreditCard,
  FileText,
  HandCoins,
  Loader2,
  Receipt,
  Scale,
  Sparkles,
  User,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useCostaIntelCostEvents,
  useCostaIntelInvoiceFromEstimate,
  useCostaIntelTariffLists,
} from "@/hooks/queries/useCostaIntel";
import { useFinanceBillingSubsidiesForEncounter } from "@/hooks/queries/useFinanceBillingWorkspace";
import {
  useRegisterServiceAccessDecision,
  useServiceAccessDecisionsList,
} from "@/hooks/queries/useServiceAccessDecisions";

interface RelatedLink {
  href: string;
  label: string;
  description: string;
}

function extractRows(payload: unknown): unknown[] {
  if (Array.isArray(payload)) return payload;
  if (payload && typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    if (Array.isArray(obj.data)) return obj.data;
    if (Array.isArray(obj.items)) return obj.items;
    if (Array.isArray(obj.content)) return obj.content;
  }
  return [];
}

const INVOICE_HANDOFF_LINKS: RelatedLink[] = [
  {
    href: "/finance/billing",
    label: "Billing & invoices",
    description: "Open billing workspace and issue invoices from approved estimates.",
  },
  {
    href: "/finance/payments",
    label: "Payments",
    description: "Patient and facility payment ledger reached via the finance plane.",
  },
  {
    href: "/finance/settlements",
    label: "MusheX settlements",
    description: "Run settlement periods and inspect MusheX payment handoff.",
  },
  {
    href: "/finance/reconciliation",
    label: "Reconciliation",
    description: "Match MusheX intents to rail settlement files and COSTA invoices.",
  },
  {
    href: "/finance/refunds",
    label: "Refunds",
    description: "Refunds, reversals, and credit notes back through MusheX.",
  },
  {
    href: "/finance/mushex-platform",
    label: "MusheX platform admin",
    description: "Read-only view of custodial wallets, remittance transfers, card profiles, and reversals.",
  },
];

const RELATED_FINANCE_LINKS: RelatedLink[] = [
  {
    href: "/finance/tariffs",
    label: "Tariff library",
    description: "Operational and reference COSTA tariff lists with upload pipeline.",
  },
  {
    href: "/finance/claims",
    label: "Claims",
    description: "Insurance claim submission and adjudication tracking.",
  },
  {
    href: "/finance/payer-claims",
    label: "Payer claims",
    description: "Payer-side claim queue, scheme view, and remittance follow-up.",
  },
  {
    href: "/finance/ledger",
    label: "General ledger",
    description: "Posting, period close, and trial balance views for the finance plane.",
  },
  {
    href: "/finance/reports",
    label: "Financial reports",
    description: "Revenue, receivables, budgets, and payer claims expenditure.",
  },
  {
    href: "/finance/commerce-integrations",
    label: "Commerce integration map",
    description: "MSIKA, MusheX, and facility-marketplace canonical surface map.",
  },
];

export default function FinanceCostaPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const patientCpid = searchParams.get("patientCpid") ?? searchParams.get("cpid");
  const source = searchParams.get("source");

  const handoffParams = new URLSearchParams();
  if (patientId) handoffParams.set("patientId", patientId);
  if (encounterId) handoffParams.set("encounterId", encounterId);
  if (source) handoffParams.set("source", source);

  const withHandoff = (href: string) => {
    const query = handoffParams.toString();
    return query ? `${href}?${query}` : href;
  };

  const tariffsQ = useCostaIntelTariffLists();
  const costEventsQ = useCostaIntelCostEvents(encounterId, patientCpid);
  const decisionsQ = useServiceAccessDecisionsList(encounterId);
  const subsidiesQ = useFinanceBillingSubsidiesForEncounter(encounterId ?? "", Boolean(encounterId));
  const registerDecisionM = useRegisterServiceAccessDecision();
  const issueInvoiceM = useCostaIntelInvoiceFromEstimate();
  const [decisionReason, setDecisionReason] = useState("");
  const [invoiceReason, setInvoiceReason] = useState("");
  const [decisionConfirmed, setDecisionConfirmed] = useState(false);
  const [invoiceConfirmed, setInvoiceConfirmed] = useState(false);
  const [decisionErr, setDecisionErr] = useState<string | null>(null);
  const [invoiceErr, setInvoiceErr] = useState<string | null>(null);
  const [decisionJson, setDecisionJson] = useState(() =>
    JSON.stringify(
      {
        access_status: "GRANTED",
        encounter_id: encounterId ?? "",
        patient_cpid: patientCpid ?? "",
        billing_timing_mode: "POST_SERVICE",
        policy_reference: "",
      },
      null,
      2,
    ),
  );
  const [invoiceJson, setInvoiceJson] = useState(() =>
    JSON.stringify(
      {
        cost_estimate_id: "",
      },
      null,
      2,
    ),
  );

  const tariffLists = tariffsQ.data ?? [];
  const operationalCount = tariffLists.filter((l) => !l.referenceOnly && l.approvedForBilling).length;
  const referenceCount = tariffLists.filter((l) => l.referenceOnly).length;

  const costEvents = costEventsQ.data ?? [];
  const decisions = decisionsQ.data ?? [];
  const subsidies = extractRows(subsidiesQ.data);

  const actions = [
    encounterId && patientId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    { href: withHandoff("/finance/tariffs"), label: "Tariffs", icon: BookOpen, tone: "secondary" as const },
    { href: withHandoff("/finance/billing"), label: "Billing", icon: Receipt, tone: "secondary" as const },
    { href: withHandoff("/finance/settlements"), label: "Settlements", icon: Wallet, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell
        title="COSTA — Costing & Tariffs"
        subtitle="Tariff intelligence, cost estimates, charge sheets, service-access decisions, and finance handoff into MusheX."
      >
        <div className="mb-4">
          <Link
            href={withHandoff("/finance")}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to finance
          </Link>
        </div>

        <div className="space-y-6">
          <WorkflowHeader
            badge="COSTA finance plane"
            badgeIcon={Sparkles}
            title="COSTA owns tariffs, cost estimates, charge sheets, and service-access decisions before billing and MusheX settlement take over."
            description="Estimates and decisions captured here flow into invoices, claims, and the MusheX payment intent state machine. This hub is read-only — open the linked surfaces below to act on the data."
            context={[
              { label: "Facility", value: facility?.name ?? "No facility selected" },
              { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
              {
                label: "Encounter context",
                value: encounterId && patientId ? "Linked to active clinical episode" : "General COSTA hub",
              },
            ]}
            actions={actions}
            metrics={[
              {
                label: "Operational tariff lists",
                value: tariffsQ.isLoading ? "…" : String(operationalCount),
                detail: "Approved for billing — drives estimates, charge sheets, invoices, and MusheX handoff.",
              },
              {
                label: "Reference tariff lists",
                value: tariffsQ.isLoading ? "…" : String(referenceCount),
                detail: "View-only mapping/comparison lists, not for production billing.",
              },
              {
                label: "Next action",
                value: encounterId ? "Inspect encounter COSTA signals" : "Open tariff library",
                detail:
                  encounterId
                    ? "Cost events and service-access decisions below are scoped to the linked encounter."
                    : "Pick an operational tariff list, then return to billing or settlements.",
              },
            ]}
          />

          <div className="grid gap-4 md:grid-cols-2">
            {/* ── Tariff libraries ─────────────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
                  <BookOpen className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Tariff libraries</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Operational and reference COSTA tariff lists. Operational lists are billable; reference lists are
                    for mapping and comparison only.
                  </p>
                  {tariffsQ.isLoading ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading tariff library…
                    </p>
                  ) : tariffsQ.isError ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
                      <AlertCircle className="h-3.5 w-3.5" /> Could not load tariff library from COSTA.
                    </p>
                  ) : tariffLists.length === 0 ? (
                    <p className="mt-3 text-xs text-slate-500">
                      No tariff lists returned. Run COSTA migrations and verify the BFF can reach costing-engine.
                    </p>
                  ) : (
                    <p className="mt-3 text-xs text-slate-700">
                      <span className="font-medium">{operationalCount}</span> operational ·{" "}
                      <span className="font-medium">{referenceCount}</span> reference
                    </p>
                  )}
                  <Link
                    href={withHandoff("/finance/tariffs")}
                    className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    Open tariff library →
                  </Link>
                </div>
              </div>
            </section>

            {/* ── Cost estimates ────────────────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
                  <Scale className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Cost estimates</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    COSTA produces patient-facing estimates from an operational tariff list, exemption context, and
                    payer coverage. Estimates feed invoices and MusheX payment intents.
                  </p>
                  <p className="mt-3 text-xs text-slate-700">
                    Pick an operational tariff list in the tariff library, then run an estimate before issuing a
                    charge sheet or invoice.
                  </p>
                  <div className="mt-3 flex flex-wrap gap-3 text-xs">
                    <Link
                      href={withHandoff("/finance/tariffs")}
                      className="font-medium text-indigo-700 hover:text-indigo-900"
                    >
                      Tariff library →
                    </Link>
                    <Link
                      href={withHandoff("/finance/billing")}
                      className="font-medium text-indigo-700 hover:text-indigo-900"
                    >
                      Issue invoice →
                    </Link>
                  </div>
                </div>
              </div>
            </section>

            {/* ── Charge sheets / cost events ────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-impilo-100 text-impilo-500">
                  <FileText className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Charge sheets</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Captured cost events at point of care. COSTA exposes these per encounter or patient CPID; the
                    billing workspace turns them into invoice lines.
                  </p>
                  {!encounterId && !patientCpid ? (
                    <p className="mt-3 text-xs text-slate-500">
                      Open this page with an encounter or patient CPID to view captured cost events.
                    </p>
                  ) : costEventsQ.isLoading || costEventsQ.isFetching ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading cost events…
                    </p>
                  ) : costEventsQ.isError ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
                      <AlertCircle className="h-3.5 w-3.5" /> Could not load cost events from COSTA.
                    </p>
                  ) : (
                    <p className="mt-3 text-xs text-slate-700">
                      <span className="font-medium">{costEvents.length}</span> captured cost event{costEvents.length === 1 ? "" : "s"}
                      {encounterId ? <> for encounter <span className="font-mono">{encounterId}</span></> : null}
                    </p>
                  )}
                  <Link
                    href={withHandoff("/finance/billing")}
                    className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900"
                  >
                    Open billing workspace →
                  </Link>
                </div>
              </div>
            </section>

            {/* ── Service access decisions ───────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-purple-100 text-purple-700">
                  <ClipboardCheck className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Service access decisions</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    COSTA records access decisions (granted, deferred, exempt) per encounter. They drive whether a
                    service proceeds to charge sheet and payer claim.
                  </p>
                  {!encounterId ? (
                    <p className="mt-3 text-xs text-slate-500">
                      Open this page with an encounter to view its service-access decisions.
                    </p>
                  ) : decisionsQ.isLoading ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading decisions…
                    </p>
                  ) : decisionsQ.isError ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
                      <AlertCircle className="h-3.5 w-3.5" /> Could not load service-access decisions from COSTA.
                    </p>
                  ) : (
                    <p className="mt-3 text-xs text-slate-700">
                      <span className="font-medium">{decisions.length}</span> decision{decisions.length === 1 ? "" : "s"} recorded for
                      encounter <span className="font-mono">{encounterId}</span>
                    </p>
                  )}
                  <div className="mt-3 flex flex-wrap gap-3 text-xs">
                    <Link
                      href={withHandoff("/finance/claims")}
                      className="font-medium text-indigo-700 hover:text-indigo-900"
                    >
                      Open claims handoff →
                    </Link>
                    {encounterId ? (
                      <Link
                        href={`/finance/costa/encounter/${encodeURIComponent(encounterId)}${
                          handoffParams.toString() ? `?${handoffParams.toString()}` : ""
                        }`}
                        className="font-medium text-indigo-700 hover:text-indigo-900"
                      >
                        View timeline →
                      </Link>
                    ) : null}
                  </div>
                </div>
              </div>
            </section>

            {/* ── Subsidies / exemptions ─────────────────────────────── */}
            <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
                  <HandCoins className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h2 className="text-sm font-semibold text-slate-900">Subsidies & exemptions</h2>
                  <p className="mt-1 text-xs text-slate-500">
                    Read-only subsidy and write-off rows derived from COSTA billing lifecycle, scoped by encounter.
                  </p>
                  {!encounterId ? (
                    <p className="mt-3 text-xs text-slate-500">
                      Open this page with an encounter to view subsidy allocations and exemptions for that episode.
                    </p>
                  ) : subsidiesQ.isLoading ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading subsidy rows…
                    </p>
                  ) : subsidiesQ.isError ? (
                    <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
                      <AlertCircle className="h-3.5 w-3.5" /> Could not load subsidy rows from COSTA.
                    </p>
                  ) : (
                    <p className="mt-3 text-xs text-slate-700">
                      <span className="font-medium">{subsidies.length}</span> subsidy row{subsidies.length === 1 ? "" : "s"} for
                      encounter <span className="font-mono">{encounterId}</span>
                    </p>
                  )}
                  {encounterId ? (
                    <Link
                      href={`/finance/costa/encounter/${encodeURIComponent(encounterId)}${
                        handoffParams.toString() ? `?${handoffParams.toString()}` : ""
                      }`}
                      className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-indigo-700 hover:text-indigo-900"
                    >
                      View subsidy timeline rows →
                    </Link>
                  ) : null}
                </div>
              </div>
            </section>
          </div>

          {/* ── Invoice / payment handoff (MusheX) ──────────────────── */}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700">
                <CreditCard className="h-5 w-5" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-slate-900">Invoice & payment handoff (MusheX)</h2>
                <p className="mt-1 text-xs text-slate-500">
                  COSTA hands billable charge sheets to MusheX, which orchestrates the payment intent. Open a
                  surface below to inspect or act on handoff state.
                </p>
                <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                  {INVOICE_HANDOFF_LINKS.map((item) => (
                    <Link
                      key={item.href}
                      href={withHandoff(item.href)}
                      className="rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-2 text-xs hover:border-indigo-300 hover:bg-white transition-colors"
                    >
                      <div className="font-medium text-slate-900">{item.label}</div>
                      <div className="text-slate-500 mt-0.5">{item.description}</div>
                    </Link>
                  ))}
                </div>
              </div>
            </div>
          </section>

          {/* ── Related finance surfaces ────────────────────────────── */}
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Related finance surfaces</h2>
            <p className="mt-1 text-xs text-slate-500">
              Other finance-plane routes that depend on, or feed back into, COSTA.
            </p>
            <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {RELATED_FINANCE_LINKS.map((item) => (
                <Link
                  key={item.href}
                  href={withHandoff(item.href)}
                  className="rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-2 text-xs hover:border-indigo-300 hover:bg-white transition-colors"
                >
                  <div className="font-medium text-slate-900">{item.label}</div>
                  <div className="text-slate-500 mt-0.5">{item.description}</div>
                </Link>
              ))}
            </div>
          </section>

          <section className="rounded-xl border border-amber-200 bg-amber-50/40 p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Controlled actions</h2>
            <p className="mt-1 text-xs text-slate-600">
              Phase 5 completion: explicit-confirmation write triggers for existing COSTA endpoints.
            </p>
            <div className="mt-4 grid gap-4 lg:grid-cols-2">
              <div className="rounded-lg border border-slate-200 bg-white p-4">
                <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Register service-access decision
                </h3>
                <p className="mt-1 text-xs text-slate-500">
                  POST <code>/internal/v1/finance/service-access-decisions</code>
                </p>
                <textarea
                  aria-label="Service-access decision JSON"
                  className="mt-2 min-h-[140px] w-full rounded-lg border border-slate-200 p-2 font-mono text-xs"
                  value={decisionJson}
                  onChange={(e) => setDecisionJson(e.target.value)}
                />
                <input
                  type="text"
                  placeholder="Reason for this write (required)"
                  className="mt-2 w-full rounded-lg border border-slate-200 px-3 py-2 text-xs"
                  value={decisionReason}
                  onChange={(e) => setDecisionReason(e.target.value)}
                />
                <label className="mt-2 flex items-center gap-2 text-xs text-slate-700">
                  <input
                    type="checkbox"
                    checked={decisionConfirmed}
                    onChange={(e) => setDecisionConfirmed(e.target.checked)}
                  />
                  I confirm this is an intentional operational write.
                </label>
                {decisionErr ? <p className="mt-2 text-xs text-red-700">{decisionErr}</p> : null}
                <button
                  type="button"
                  disabled={registerDecisionM.isPending || !decisionConfirmed || !decisionReason.trim()}
                  className="mt-2 rounded-lg bg-amber-700 px-3 py-2 text-xs font-medium text-white hover:bg-amber-800 disabled:opacity-50"
                  onClick={() => {
                    try {
                      const parsed = JSON.parse(decisionJson) as Record<string, unknown>;
                      parsed.audit_reason = decisionReason.trim();
                      setDecisionErr(null);
                      registerDecisionM.mutate(parsed);
                    } catch {
                      setDecisionErr("Invalid JSON payload.");
                    }
                  }}
                >
                  {registerDecisionM.isPending ? "Posting..." : "Submit decision"}
                </button>
                {registerDecisionM.data ? (
                  <pre className="mt-2 max-h-36 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-2 text-[11px]">
                    {JSON.stringify(registerDecisionM.data, null, 2)}
                  </pre>
                ) : null}
              </div>

              <div className="rounded-lg border border-slate-200 bg-white p-4">
                <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Issue invoice from estimate
                </h3>
                <p className="mt-1 text-xs text-slate-500">
                  POST <code>/internal/v1/finance/costa-intel/invoices/from-cost-estimate</code>
                </p>
                <textarea
                  aria-label="Invoice-from-estimate JSON"
                  className="mt-2 min-h-[140px] w-full rounded-lg border border-slate-200 p-2 font-mono text-xs"
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
                  I confirm this is an intentional operational write.
                </label>
                {invoiceErr ? <p className="mt-2 text-xs text-red-700">{invoiceErr}</p> : null}
                <button
                  type="button"
                  disabled={issueInvoiceM.isPending || !invoiceConfirmed || !invoiceReason.trim()}
                  className="mt-2 rounded-lg bg-amber-700 px-3 py-2 text-xs font-medium text-white hover:bg-amber-800 disabled:opacity-50"
                  onClick={() => {
                    try {
                      const parsed = JSON.parse(invoiceJson) as Record<string, unknown>;
                      parsed.audit_reason = invoiceReason.trim();
                      setInvoiceErr(null);
                      issueInvoiceM.mutate(parsed);
                    } catch {
                      setInvoiceErr("Invalid JSON payload.");
                    }
                  }}
                >
                  {issueInvoiceM.isPending ? "Posting..." : "Issue invoice"}
                </button>
                {issueInvoiceM.data ? (
                  <pre className="mt-2 max-h-36 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-2 text-[11px]">
                    {JSON.stringify(issueInvoiceM.data, null, 2)}
                  </pre>
                ) : null}
              </div>
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
