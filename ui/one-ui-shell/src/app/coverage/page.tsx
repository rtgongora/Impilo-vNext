"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
/**
 * Coverage Operations — Schemes, eligibility, claims, settlement, preauth.
 * Route: /coverage
 * Lovable: CoverageOperations with 11 tabs. Runtime: 5 core tabs backed by coverage-service.
 */

import Link from "next/link";
import { useState, type FormEvent } from "react";
import {
  Shield, UserCheck, FileText, DollarSign, Briefcase,
  Loader2, CheckCircle2, AlertCircle, Plus,
  Users, CreditCard, Scale, ShieldCheck, UserCircle2, Network, PiggyBank,
} from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCoverageClaims,
  useCoverageAppealsList,
  useCoverageContributionsList,
  useCoveragePlans,
  useCoveragePreauthsList,
  useCoverageRemittances,
  useCoverageUtilizationList,
  useCoverageSubsidiesList,
  useCreateCoverageClaim,
  useDecideCoverageAppeal,
  useEnrollCoverageMember,
  useReviewCoverageAppeal,
  useRunCoverageCommand,
  useSubmitCoverageAppeal,
  type CoverageCommandKind,
  type CoverageClaim,
} from "@/hooks/queries/useCoverage";
import { useProviderContracts } from "@/hooks/queries/useProviderContracts";
import { apiClient } from "@/lib/api-client";
import { CoverageGeoMapPanel } from "@/components/coverage/CoverageGeoMapPanel";

type ActiveTab = "dashboard" | "schemes" | "membership" | "eligibility" | "contracting" | "preauth" | "claims" | "contributions" | "subsidies" | "settlement" | "appeals" | "intelligence";

const TABS: { key: ActiveTab; label: string; icon: typeof Shield }[] = [
  { key: "dashboard", label: "Dashboard", icon: Shield },
  { key: "schemes", label: "Schemes", icon: Briefcase },
  { key: "membership", label: "Membership", icon: Users },
  { key: "eligibility", label: "Eligibility", icon: UserCheck },
  { key: "contracting", label: "Contracting", icon: FileText },
  { key: "preauth", label: "Pre-Auth", icon: ShieldCheck },
  { key: "claims", label: "Claims", icon: FileText },
  { key: "contributions", label: "Contributions", icon: CreditCard },
  { key: "subsidies", label: "Subsidies", icon: PiggyBank },
  { key: "settlement", label: "Settlement", icon: DollarSign },
  { key: "appeals", label: "Appeals", icon: Scale },
  { key: "intelligence", label: "Intelligence", icon: Shield },
];

function formatCoverageDate(value?: string) {
  if (!value) return "Pending";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString();
}

function formatCoverageCurrency(value: number, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(value);
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function readUnknownString(row: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "string" && value.trim().length > 0) return value;
  }
  return "";
}

function readUnknownNumber(row: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "number") return value;
    if (typeof value === "string" && value.trim().length > 0) {
      const parsed = Number(value);
      if (!Number.isNaN(parsed)) return parsed;
    }
  }
  return 0;
}

function statusClass(status: string) {
  if (status === "ACTIVE" || status === "PAID" || status === "APPROVED" || status === "UPHELD") {
    return "bg-green-100 text-green-700";
  }
  if (status === "REJECTED" || status === "DENIED" || status === "OVERTURNED") {
    return "bg-red-100 text-red-700";
  }
  return "bg-amber-100 text-amber-700";
}

const COVERAGE_COMMAND_LABELS: Record<CoverageCommandKind, string> = {
  "eligibility-check": "Eligibility check",
  "member-enrollment": "Member enrollment",
  "claim-submission": "Claim submission",
  "preauth-request": "Pre-auth request",
  "appeal-submission": "Appeal submission",
};

const COVERAGE_COMMAND_DEFAULTS: Record<CoverageCommandKind, Record<string, string>> = {
  "eligibility-check": {
    memberCpid: "CPID-EXAMPLE",
    serviceCode: "CONSULTATION",
    coverageId: "COVERAGE-ID",
    idempotencyKey: "eligibility-COVERAGE-ID",
  },
  "member-enrollment": {
    clientId: "CPID-EXAMPLE",
    planId: "PLAN-ID",
    memberNumber: "MEMBER-001",
    relationship: "SELF",
    idempotencyKey: "enroll-MEMBER-001",
  },
  "claim-submission": {
    coverageId: "COVERAGE-ID",
    claimType: "OUTPATIENT",
    facilityId: "FACILITY-ID",
    totalAmount: "50.00",
    idempotencyKey: "claim-COVERAGE-ID-001",
  },
  "preauth-request": {
    coverageId: "COVERAGE-ID",
    requestType: "SURGERY",
    facilityId: "FACILITY-ID",
    providerId: "PROVIDER-ID",
    clinicalInfo: "Clinical justification",
    idempotencyKey: "preauth-COVERAGE-ID-001",
  },
  "appeal-submission": {
    claimId: "CLAIM-ID",
    appellantId: "CPID-EXAMPLE",
    reason: "Claim decision dispute",
    evidenceSummary: "Supporting evidence summary",
    idempotencyKey: "appeal-CLAIM-ID-001",
  },
};

const COVERAGE_COMMAND_REQUIRED: Record<CoverageCommandKind, string[]> = {
  "eligibility-check": ["memberCpid", "coverageId"],
  "member-enrollment": ["clientId", "planId", "memberNumber"],
  "claim-submission": ["coverageId", "claimType", "facilityId", "totalAmount"],
  "preauth-request": ["coverageId", "requestType", "facilityId", "providerId"],
  "appeal-submission": ["claimId", "appellantId", "reason"],
};

const COVERAGE_COMMAND_FIELDS: Record<CoverageCommandKind, Array<{ key: string; label: string; type?: string }>> = {
  "eligibility-check": [
    { key: "memberCpid", label: "Member CPID" },
    { key: "coverageId", label: "Coverage ID" },
    { key: "serviceCode", label: "Service code" },
    { key: "idempotencyKey", label: "Idempotency key" },
  ],
  "member-enrollment": [
    { key: "clientId", label: "Client ID / CPID" },
    { key: "planId", label: "Plan ID" },
    { key: "memberNumber", label: "Member number" },
    { key: "relationship", label: "Relationship" },
    { key: "idempotencyKey", label: "Idempotency key" },
  ],
  "claim-submission": [
    { key: "coverageId", label: "Coverage ID" },
    { key: "claimType", label: "Claim type" },
    { key: "facilityId", label: "Facility ID" },
    { key: "totalAmount", label: "Total amount", type: "number" },
    { key: "idempotencyKey", label: "Idempotency key" },
  ],
  "preauth-request": [
    { key: "coverageId", label: "Coverage ID" },
    { key: "requestType", label: "Request type" },
    { key: "facilityId", label: "Facility ID" },
    { key: "providerId", label: "Provider ID" },
    { key: "clinicalInfo", label: "Clinical info" },
    { key: "idempotencyKey", label: "Idempotency key" },
  ],
  "appeal-submission": [
    { key: "claimId", label: "Claim ID" },
    { key: "appellantId", label: "Appellant ID" },
    { key: "reason", label: "Reason" },
    { key: "evidenceSummary", label: "Evidence summary" },
    { key: "idempotencyKey", label: "Idempotency key" },
  ],
};

function validateCoverageCommand(command: CoverageCommandKind, payload: Record<string, string>) {
  const missing = COVERAGE_COMMAND_REQUIRED[command].filter((key) => !payload[key]?.trim());
  if (missing.length > 0) return `Missing required field(s): ${missing.join(", ")}.`;
  if (payload.idempotencyKey && payload.idempotencyKey.trim().length < 8) {
    return "Idempotency key must be at least 8 characters for audit-safe retry.";
  }
  if (command === "claim-submission") {
    const amount = Number(payload.totalAmount);
    if (!Number.isFinite(amount) || amount <= 0) return "Claim amount must be greater than zero.";
    if (amount > 100_000) return "Claim amount exceeds the operator guardrail; escalate to payer supervisor.";
  }
  if (command === "member-enrollment" && !["SELF", "SPOUSE", "CHILD", "DEPENDENT"].includes(payload.relationship)) {
    return "Relationship must be SELF, SPOUSE, CHILD, or DEPENDENT.";
  }
  if (command === "appeal-submission" && payload.reason.trim().length < 12) {
    return "Appeal reason must include enough detail for review.";
  }
  if (command === "preauth-request" && payload.clinicalInfo.trim().length < 10) {
    return "Pre-auth requires clinical justification before submission.";
  }
  return null;
}

const ELIGIBILITY_SUMMARY_KEYS = new Set(["result_code", "result_message"]);

function EligibilityExtraFields({ payload }: { payload: Record<string, unknown> | undefined }) {
  if (!payload) return null;
  const entries = Object.entries(payload).filter(([k]: [string, unknown]) => !ELIGIBILITY_SUMMARY_KEYS.has(k));
  if (entries.length === 0) {
    return (
      <p className="mt-3 text-xs text-gray-500">
        No additional fields in this response. GOP and packaged benefit lines are not simulated in the UI.
      </p>
    );
  }
  return (
    <dl className="mt-3 grid grid-cols-1 gap-2 rounded-lg border border-white/60 bg-white/50 p-3 text-xs">
      {entries.slice(0, 16).map(([k, v]: [string, unknown]) => (
        <div key={k} className="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
          <dt className="shrink-0 font-medium text-gray-600">{k}</dt>
          <dd className="font-mono text-gray-800 break-all">
            {v !== null && typeof v === "object" ? JSON.stringify(v) : String(v)}
          </dd>
        </div>
      ))}
    </dl>
  );
}

export default function CoveragePage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("dashboard");

  return (
    <AppLayout>
      <PageShell title="Coverage Operations" subtitle="Schemes, membership, eligibility, claims & settlement">
        <div className="mb-6 grid gap-4 md:grid-cols-2">
          <Link
            href="/coverage/member"
            className="block rounded-xl border-2 border-violet-200 bg-gradient-to-r from-violet-50 to-indigo-50 p-5 shadow-sm hover:border-violet-400 hover:shadow-md transition-all group"
          >
            <div className="flex flex-wrap items-center gap-4">
              <div className="rounded-lg bg-violet-600 p-3 text-white shadow">
                <UserCircle2 className="h-7 w-7" />
              </div>
              <div className="min-w-0 flex-1">
                <h2 className="text-lg font-semibold text-gray-900 group-hover:text-violet-700 transition-colors">
                  My Coverage (member view)
                </h2>
                <p className="text-sm text-gray-600 mt-0.5">
                  See your plans, eligibility checks, claims, contributions, preauths, and appeals in one dashboard.
                </p>
              </div>
              <span className="text-sm font-medium text-violet-700 whitespace-nowrap">Open →</span>
            </div>
          </Link>
          <Link
            href="/coverage/contracts"
            className="block rounded-xl border-2 border-slate-200 bg-gradient-to-r from-slate-50 to-zinc-50 p-5 shadow-sm hover:border-violet-400 hover:shadow-md transition-all group"
          >
            <div className="flex flex-wrap items-center gap-4">
              <div className="rounded-lg bg-slate-700 p-3 text-white shadow">
                <Network className="h-7 w-7" />
              </div>
              <div className="min-w-0 flex-1">
                <h2 className="text-lg font-semibold text-gray-900 group-hover:text-violet-700 transition-colors">
                  Provider contracts
                </h2>
                <p className="text-sm text-gray-600 mt-0.5">
                  Payer admin: contracts, suspend/reinstate, and provider networks with membership.
                </p>
              </div>
              <span className="text-sm font-medium text-violet-700 whitespace-nowrap">Open →</span>
            </div>
          </Link>
        </div>
        <CoverageCommandConsole />
        <div className="flex gap-1 mb-6 border-b border-gray-200">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === tab.key ? "border-violet-600 text-violet-600" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                <Icon className="w-4 h-4" /> {tab.label}
              </button>
            );
          })}
        </div>

        {activeTab === "dashboard" && <DashboardTab />}
        {activeTab === "schemes" && <SchemesTab />}
        {activeTab === "membership" && <MembershipTab />}
        {activeTab === "eligibility" && <EligibilityTab />}
        {activeTab === "contracting" && <ContractingTab />}
        {activeTab === "preauth" && <PreauthTab />}
        {activeTab === "claims" && <ClaimsTab />}
        {activeTab === "contributions" && <ContributionsTab />}
        {activeTab === "subsidies" && <SubsidiesTab />}
        {activeTab === "settlement" && <SettlementTab />}
        {activeTab === "appeals" && <AppealsTab />}
        {activeTab === "intelligence" && <IntelligenceTab />}
      </PageShell>
    </AppLayout>
  );
}

function CoverageCommandConsole() {
  const [command, setCommand] = useState<CoverageCommandKind>("eligibility-check");
  const [payloads, setPayloads] = useState(COVERAGE_COMMAND_DEFAULTS);
  const [feedback, setFeedback] = useState<string | null>(null);
  const mutation = useRunCoverageCommand();
  const currentPayload = payloads[command];

  function selectCommand(nextCommand: CoverageCommandKind) {
    setCommand(nextCommand);
    setFeedback(null);
  }

  function updatePayloadField(key: string, value: string) {
    setPayloads((current) => ({
      ...current,
      [command]: {
        ...current[command],
        [key]: value,
      },
    }));
  }

  function submitCommand() {
    setFeedback(null);
    const validationMessage = validateCoverageCommand(command, currentPayload);
    if (validationMessage) {
      setFeedback(validationMessage);
      return;
    }

    const { idempotencyKey, evidenceSummary, ...commandPayload } = currentPayload;
    mutation.mutate(
      {
        command,
        payload: {
          ...commandPayload,
          ...(command === "appeal-submission"
            ? { evidence: { summary: evidenceSummary } }
            : {}),
        },
        idempotencyKey: idempotencyKey?.trim() || undefined,
      },
      {
        onSuccess: () => setFeedback(`${COVERAGE_COMMAND_LABELS[command]} accepted by Coverage BFF.`),
        onError: () => setFeedback(`${COVERAGE_COMMAND_LABELS[command]} failed. Verify payload and trust context.`),
      },
    );
  }

  return (
    <section className="mb-6 rounded-xl border border-violet-200 bg-violet-50/70 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-violet-700">Coverage service commands</p>
          <h2 className="mt-1 text-base font-semibold text-gray-900">Action console</h2>
          <p className="mt-1 max-w-3xl text-sm text-gray-600">
            Additive command surface for the real Coverage BFF paths. Existing tabs below remain intact for guided workflows.
          </p>
        </div>
        <span className="rounded-full border border-violet-200 bg-white px-3 py-1 text-xs font-medium text-violet-700">
          live commands
        </span>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-[260px_1fr]">
        <div className="space-y-2">
          {(Object.keys(COVERAGE_COMMAND_LABELS) as CoverageCommandKind[]).map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => selectCommand(option)}
              className={`w-full rounded-lg border px-3 py-2 text-left text-sm font-medium ${
                command === option
                  ? "border-violet-500 bg-white text-violet-700"
                  : "border-violet-100 bg-white/60 text-gray-700 hover:bg-white"
              }`}
            >
              {COVERAGE_COMMAND_LABELS[option]}
            </button>
          ))}
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900">
            Refunds and settlement payouts stay on the dedicated finance routes to preserve separation of duties.
          </div>
        </div>

        <div className="rounded-lg border border-violet-100 bg-white p-4">
          <div className="grid gap-3 md:grid-cols-2">
            {COVERAGE_COMMAND_FIELDS[command].map((field) => (
              <label key={field.key} className="block text-xs font-medium text-gray-600">
                {field.label}
                <input
                  type={field.type ?? "text"}
                  className="mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-800"
                  value={currentPayload[field.key] ?? ""}
                  onChange={(event) => updatePayloadField(field.key, event.target.value)}
                />
              </label>
            ))}
          </div>
          <details className="mt-3 rounded-lg border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600">
            <summary className="cursor-pointer font-medium text-slate-700">Payload preview</summary>
            <QueryResultPanel title="Coverage payload" data={currentPayload} />
          </details>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={submitCommand}
              disabled={mutation.isPending}
              className="rounded-lg bg-violet-700 px-4 py-2 text-sm font-medium text-white hover:bg-violet-800 disabled:opacity-50"
            >
              {mutation.isPending ? "Submitting..." : `Run ${COVERAGE_COMMAND_LABELS[command]}`}
            </button>
            {feedback ? <span className="text-xs text-gray-700">{feedback}</span> : null}
          </div>
          {mutation.data ? (
            <QueryResultPanel title="Mutation" isPending={mutation.isPending} isLoading={mutation.isPending} isError={mutation.isError} error={mutation.error} data={mutation.data} />
          ) : null}
        </div>
      </div>
    </section>
  );
}

// ── DASHBOARD TAB ────────────────────────────────────────────────
function DashboardTab() {
  const plansQ = useCoveragePlans();
  const remQ = useCoverageRemittances();
  const plans = plansQ.data ?? [];
  const remittances = remQ.data ?? [];
  const remittanceTotal = remittances.reduce((sum, r) => sum + r.amount, 0);
  const primaryCurrency = remittances[0]?.currency ?? "USD";

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-violet-50 rounded-lg border border-violet-200 p-4 text-center">
          <p className="text-2xl font-bold text-violet-700">{plansQ.isLoading ? "…" : plans.length}</p>
          <p className="text-xs text-violet-600">Configured plans</p>
          <p className="text-[10px] text-violet-500/90 mt-1">GET /internal/v1/coverage/plans</p>
        </div>
        <div className="bg-green-50 rounded-lg border border-green-200 p-4 text-center">
          <p className="text-2xl font-bold text-green-700">{remQ.isLoading ? "…" : remittances.length}</p>
          <p className="text-xs text-green-600">Remittance rows</p>
          <p className="text-[10px] text-green-600/90 mt-1">Not a claims ledger total</p>
        </div>
        <div className="bg-blue-50 rounded-lg border border-blue-200 p-4 text-center">
          <p className="text-2xl font-bold text-blue-700">
            {remQ.isLoading ? "…" : formatCoverageCurrency(remittanceTotal, primaryCurrency)}
          </p>
          <p className="text-xs text-blue-600">Sum of remittance amounts</p>
        </div>
        <div className="bg-amber-50 rounded-lg border border-amber-200 p-4 text-center">
          <p className="text-2xl font-bold text-amber-700">—</p>
          <p className="text-xs text-amber-600">Claim mix / approval rate</p>
          <p className="text-[10px] text-amber-600/90 mt-1">Needs aggregated reporting API</p>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-900 mb-2">Settlement lifecycle</h3>
        <p className="text-xs text-gray-600 leading-relaxed">
          Stage-level volumes are not aggregated in this UI. Use the{" "}
          <span className="font-medium text-gray-800">Settlement</span> tab for remittance rows from{" "}
          <code className="text-[10px]">GET /internal/v1/coverage/remittances</code>, and the{" "}
          <span className="font-medium text-gray-800">Claims</span> tab with a coverage id to load claims.
        </p>
      </div>
    </div>
  );
}

// ── SCHEMES TAB ──────────────────────────────────────────────────
function SchemesTab() {
  const { data: plans = [], isLoading } = useCoveragePlans();

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Coverage Schemes & Products</h3>
      {isLoading ? (
        <div className="flex items-center justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-gray-400" /></div>
      ) : plans.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <Briefcase className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-400 text-sm">No coverage schemes configured</p>
        </div>
      ) : (
        <div className="space-y-3">
          {plans.map((plan) => (
            <div key={plan.id} className="bg-white rounded-lg border border-gray-200 p-5 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-900">{plan.planName}</p>
                <p className="text-xs text-gray-500">{plan.planCode || "—"} · {plan.planType} · Payer: {plan.payerId || "—"}</p>
              </div>
              <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${plan.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"}`}>
                {plan.status}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── ELIGIBILITY TAB ──────────────────────────────────────────────
function EligibilityTab() {
  const [patientRef, setPatientRef] = useState("");
  const [serviceCode, setServiceCode] = useState("");
  const [coverageId, setCoverageId] = useState("");

  const check = useMutation({
    mutationFn: (body: Record<string, string>) =>
      apiClient.post<{ data: Record<string, unknown> }>("/internal/v1/coverage/eligibility", body),
  });

  function handleCheck(e: FormEvent) {
    e.preventDefault();
    check.mutate({ patientRef, serviceCode, coverageId });
  }

  return (
    <div className="space-y-6">
      <form onSubmit={handleCheck} className="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
        <h3 className="text-base font-semibold text-gray-900">Real-time Eligibility Check</h3>
        <p className="text-sm text-gray-500">Verify patient coverage eligibility and entitlement before service delivery.</p>
        <div className="grid grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Patient Reference</label>
            <input type="text" value={patientRef} onChange={(e) => setPatientRef(e.target.value)} required
              placeholder="CPID or PHID" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Coverage ID</label>
            <input type="text" value={coverageId} onChange={(e) => setCoverageId(e.target.value)} required
              placeholder="Plan membership ID" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Service Code</label>
            <input type="text" value={serviceCode} onChange={(e) => setServiceCode(e.target.value)}
              placeholder="Optional service code" className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
        </div>
        <button type="submit" disabled={check.isPending || !patientRef || !coverageId}
          className="w-full py-2.5 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700 disabled:opacity-50 flex items-center justify-center gap-2">
          {check.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserCheck className="w-4 h-4" />}
          Check Eligibility
        </button>
      </form>

      {check.isSuccess && (
        <div className="space-y-4">
          <div className={`rounded-lg border-2 p-5 ${
            (check.data?.data as Record<string, unknown>)?.result_code === "ELIGIBLE"
              ? "border-green-300 bg-green-50" : "border-red-300 bg-red-50"
          }`}>
            <div className="flex items-center gap-2 mb-2">
              {(check.data?.data as Record<string, unknown>)?.result_code === "ELIGIBLE"
                ? <CheckCircle2 className="w-6 h-6 text-green-600" />
                : <AlertCircle className="w-6 h-6 text-red-600" />}
              <h4 className="text-base font-semibold">{String((check.data?.data as Record<string, unknown>)?.result_code ?? "UNKNOWN")}</h4>
            </div>
            <p className="text-sm text-gray-700">{String((check.data?.data as Record<string, unknown>)?.result_message ?? "")}</p>
            <EligibilityExtraFields payload={check.data?.data as Record<string, unknown> | undefined} />
          </div>
        </div>
      )}
      {check.isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-500" /><p className="text-sm text-red-700">Eligibility check failed.</p>
        </div>
      )}
    </div>
  );
}

// ── CLAIMS TAB ───────────────────────────────────────────────────
function ClaimsTab() {
  const [showForm, setShowForm] = useState(false);
  const [listCoverageId, setListCoverageId] = useState("");
  const [appliedCoverageId, setAppliedCoverageId] = useState<string | null>(null);
  const [claimForm, setClaimForm] = useState({
    coverageId: "",
    claimType: "OUTPATIENT",
    facilityId: "",
    totalAmount: "",
  });
  const submit = useCreateCoverageClaim();
  const claimsQ = useCoverageClaims(appliedCoverageId);

  function applyListFilter() {
    const id = listCoverageId.trim();
    setAppliedCoverageId(id.length > 0 ? id : null);
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Claims & Adjudication</h3>
        <button type="button" onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
          <Plus className="w-4 h-4" /> Submit Claim
        </button>
      </div>
      <p className="text-sm text-gray-600">
        Scheme-scoped claims use coverage-service. MusheX adjudication and payer queues live on{" "}
        <Link href="/finance/claims" className="font-medium text-impilo-700 hover:text-impilo-900">
          /finance/claims
        </Link>{" "}
        and{" "}
        <Link href="/finance/payer-claims" className="font-medium text-impilo-700 hover:text-impilo-900">
          /finance/payer-claims
        </Link>
        .
      </p>

      <div className="bg-slate-50 rounded-lg border border-slate-200 p-4 flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="claims-list-coverage" className="block text-xs font-medium text-gray-600 mb-1">
            Coverage ID (list claims)
          </label>
          <input
            id="claims-list-coverage"
            type="text"
            value={listCoverageId}
            onChange={(e) => setListCoverageId(e.target.value)}
            placeholder="Same id used when submitting claims"
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg"
          />
        </div>
        <button
          type="button"
          onClick={applyListFilter}
          className="px-4 py-2 bg-slate-800 text-white text-sm font-medium rounded-lg hover:bg-slate-900"
        >
          Load claims
        </button>
      </div>
      <p className="text-xs text-gray-500">
        Uses <code className="text-[10px]">GET /internal/v1/coverage/claims?coverageId=…</code> via Experience BFF.
      </p>

      {showForm && (
        <div className="bg-white rounded-lg border border-blue-200 p-5 space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">New Claim Submission</h4>
          <div className="grid grid-cols-2 gap-3">
            <input
              type="text"
              placeholder="Coverage ID"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={claimForm.coverageId}
              onChange={(event) => setClaimForm((current) => ({ ...current, coverageId: event.target.value }))}
            />
            <input
              type="text"
              placeholder="Claim Type (e.g., OUTPATIENT)"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={claimForm.claimType}
              onChange={(event) => setClaimForm((current) => ({ ...current, claimType: event.target.value }))}
            />
            <input
              type="text"
              placeholder="Facility ID"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={claimForm.facilityId}
              onChange={(event) => setClaimForm((current) => ({ ...current, facilityId: event.target.value }))}
            />
            <input
              type="number"
              placeholder="Total Amount"
              step="0.01"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={claimForm.totalAmount}
              onChange={(event) => setClaimForm((current) => ({ ...current, totalAmount: event.target.value }))}
            />
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button type="button" onClick={() => {
              submit.mutate(
                claimForm,
                {
                  onSuccess: () => {
                    setShowForm(false);
                    setListCoverageId(claimForm.coverageId);
                    setAppliedCoverageId(claimForm.coverageId.trim() || null);
                  },
                }
              );
            }} disabled={submit.isPending || !claimForm.coverageId.trim() || !claimForm.claimType.trim() || !claimForm.facilityId.trim() || !claimForm.totalAmount.trim()}
              className="flex-1 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
              {submit.isPending ? "Submitting..." : "Submit Claim"}
            </button>
          </div>
        </div>
      )}

      {appliedCoverageId && claimsQ.isLoading && (
        <div className="flex items-center justify-center py-12 gap-2 text-gray-500 text-sm">
          <Loader2 className="w-5 h-5 animate-spin" /> Loading claims…
        </div>
      )}
      {appliedCoverageId && claimsQ.isError && (
        <div className="text-sm text-red-600 border border-red-200 rounded-lg p-3">Could not load claims for this coverage id.</div>
      )}
      {appliedCoverageId && !claimsQ.isLoading && !claimsQ.isError && (claimsQ.data?.length ?? 0) === 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-400 text-sm">No claims returned for this coverage id</p>
          <p className="text-gray-400 text-xs mt-1">Lifecycle: SUBMITTED → ADJUDICATED → APPROVED → REMITTED</p>
        </div>
      )}
      {appliedCoverageId && !claimsQ.isLoading && (claimsQ.data?.length ?? 0) > 0 && claimsQ.data && (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50 text-left">
                <th className="px-4 py-2 font-medium text-gray-600">Claim #</th>
                <th className="px-4 py-2 font-medium text-gray-600">Type</th>
                <th className="px-4 py-2 font-medium text-gray-600">Amount</th>
                <th className="px-4 py-2 font-medium text-gray-600">Status</th>
                <th className="px-4 py-2 font-medium text-gray-600">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {claimsQ.data.map((c: CoverageClaim) => (
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-4 py-2 font-mono text-xs">{c.claimNumber}</td>
                  <td className="px-4 py-2">{c.claimType}</td>
                  <td className="px-4 py-2">{formatCoverageCurrency(c.totalAmount, "USD")}</td>
                  <td className="px-4 py-2"><span className="text-xs rounded-full bg-gray-100 px-2 py-0.5">{c.status}</span></td>
                  <td className="px-4 py-2 text-xs text-gray-500">{formatCoverageDate(c.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {!appliedCoverageId && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center text-sm text-gray-500">
          Enter a coverage id and choose <span className="font-medium text-gray-700">Load claims</span> to list rows from the coverage service.
        </div>
      )}
    </div>
  );
}

// ── SETTLEMENT TAB ───────────────────────────────────────────────
function SettlementTab() {
  const { data: remittances = [], isLoading } = useCoverageRemittances();

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Settlement & Remittance</h3>
      <p className="text-sm text-gray-500">
        Rows from <code className="text-xs">GET /internal/v1/coverage/remittances</code>. Finance operators can also use the dedicated{" "}
        <Link href="/finance/remittances" className="font-medium text-impilo-700 hover:text-impilo-900">
          /finance/remittances
        </Link>{" "}
        hub for separation-of-duties reporting.
      </p>

      {isLoading ? (
        <div className="flex items-center justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-gray-400" /></div>
      ) : remittances.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <DollarSign className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-400 text-sm">No remittances recorded</p>
        </div>
      ) : (
        <div className="space-y-3">
          {remittances.map((rem) => (
            <div key={rem.id} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-900">Remittance {rem.remittanceNumber}</p>
                <p className="text-xs text-gray-500">Coverage: {rem.coverageId || "—"} · Remitted: {formatCoverageDate(rem.remittedAt)}</p>
              </div>
              <div className="text-right">
                <p className="text-sm font-mono font-bold text-gray-900">
                  {rem.currency}{" "}
                  {rem.amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </p>
                <span className={`px-2 py-0.5 text-xs rounded-full ${rem.status === "PAID" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}`}>
                  {rem.status}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── MEMBERSHIP TAB ───────────────────────────────────────────────
function MembershipTab() {
  const [showForm, setShowForm] = useState(false);
  const [memberForm, setMemberForm] = useState({
    clientId: "",
    planId: "",
    memberNumber: "",
    relationship: "SELF",
  });
  const enroll = useEnrollCoverageMember();

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Membership Administration</h3>
        <button onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
          <Plus className="w-4 h-4" /> Enroll Member
        </button>
      </div>
      {showForm && (
        <div className="bg-white rounded-lg border border-blue-200 p-5 space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">New Member Enrollment</h4>
          <div className="grid grid-cols-2 gap-3">
            <input
              type="text"
              placeholder="Client ID (CPID)"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={memberForm.clientId}
              onChange={(event) => setMemberForm((current) => ({ ...current, clientId: event.target.value }))}
            />
            <input
              type="text"
              placeholder="Plan ID"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={memberForm.planId}
              onChange={(event) => setMemberForm((current) => ({ ...current, planId: event.target.value }))}
            />
            <input
              type="text"
              placeholder="Member Number"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={memberForm.memberNumber}
              onChange={(event) => setMemberForm((current) => ({ ...current, memberNumber: event.target.value }))}
            />
            <select
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={memberForm.relationship}
              onChange={(event) => setMemberForm((current) => ({ ...current, relationship: event.target.value }))}
            >
              <option value="SELF">Self</option><option value="SPOUSE">Spouse</option>
              <option value="CHILD">Child</option><option value="DEPENDENT">Dependent</option>
            </select>
          </div>
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button type="button" onClick={() => {
              enroll.mutate(
                memberForm,
                { onSuccess: () => setShowForm(false) }
              );
            }} disabled={enroll.isPending || !memberForm.clientId.trim() || !memberForm.planId.trim() || !memberForm.memberNumber.trim()}
              className="flex-1 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
              {enroll.isPending ? "Enrolling..." : "Enroll"}
            </button>
          </div>
          {enroll.isSuccess && <p className="text-xs text-green-600">Member enrolled successfully.</p>}
          {enroll.isError && <p className="text-xs text-red-600">Enrollment failed.</p>}
        </div>
      )}
      <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
        <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
        <p className="text-gray-400 text-sm">Search by plan or member to view enrollments</p>
      </div>
    </div>
  );
}

// ── PREAUTH TAB ──────────────────────────────────────────────────
function PreauthTab() {
  const [showForm, setShowForm] = useState(false);
  const [preauthForm, setPreauthForm] = useState({
    coverageId: "",
    requestType: "SURGERY",
    facilityId: "",
    providerId: "",
    clinicalInfo: "",
  });
  const preauthListQ = useCoveragePreauthsList();
  const preauthRows = (preauthListQ.data ?? []).map(asRecord);
  const create = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/coverage/preauth", body),
    onSuccess: () => setShowForm(false),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Pre-Authorization Requests</h3>
        <button onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
          <Plus className="w-4 h-4" /> New Pre-Auth
        </button>
      </div>
      <p className="text-sm text-gray-500">
        Read rows from <code className="text-xs">GET /internal/v1/coverage/preauths</code>. Create form continues to use{" "}
        <code className="text-xs">POST /internal/v1/coverage/preauth</code>.
      </p>

      {preauthListQ.isLoading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
        </div>
      ) : preauthListQ.isError ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          Could not load pre-authorization rows.
        </div>
      ) : preauthRows.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white p-4 text-sm text-gray-500">
          No pre-authorization rows returned by the coverage service.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50 text-left">
                <th className="px-3 py-2 font-medium text-gray-600">Preauth</th>
                <th className="px-3 py-2 font-medium text-gray-600">Coverage</th>
                <th className="px-3 py-2 font-medium text-gray-600">Type</th>
                <th className="px-3 py-2 font-medium text-gray-600">Status</th>
                <th className="px-3 py-2 font-medium text-gray-600">Requested</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {preauthRows.map((row, idx) => {
                const id = readUnknownString(row, "id", "preauth_id", "preauthId") || `row-${idx}`;
                const status =
                  readUnknownString(row, "status", "decision", "result", "state").toUpperCase() || "UNKNOWN";
                return (
                  <tr key={`${id}-${idx}`}>
                    <td className="px-3 py-2 font-mono text-xs text-gray-700">{id}</td>
                    <td className="px-3 py-2 text-gray-700">
                      {readUnknownString(row, "coverage_id", "coverageId", "member_id", "memberId") || "—"}
                    </td>
                    <td className="px-3 py-2 text-gray-700">
                      {readUnknownString(row, "request_type", "requestType", "type", "service_type", "serviceType") ||
                        "—"}
                    </td>
                    <td className="px-3 py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs ${statusClass(status)}`}>{status}</span>
                    </td>
                    <td className="px-3 py-2 text-xs text-gray-500">
                      {formatCoverageDate(
                        readUnknownString(row, "requested_at", "requestedAt", "created_at", "createdAt"),
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {showForm && (
        <div className="bg-white rounded-lg border border-blue-200 p-5 space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">Pre-Authorization Request</h4>
          <div className="grid grid-cols-2 gap-3">
            <input
              type="text"
              placeholder="Coverage ID"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={preauthForm.coverageId}
              onChange={(event) => setPreauthForm((current) => ({ ...current, coverageId: event.target.value }))}
            />
            <input
              type="text"
              placeholder="Request Type (e.g., SURGERY)"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={preauthForm.requestType}
              onChange={(event) => setPreauthForm((current) => ({ ...current, requestType: event.target.value }))}
            />
            <input
              type="text"
              placeholder="Facility ID"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={preauthForm.facilityId}
              onChange={(event) => setPreauthForm((current) => ({ ...current, facilityId: event.target.value }))}
            />
            <input
              type="text"
              placeholder="Provider ID"
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
              value={preauthForm.providerId}
              onChange={(event) => setPreauthForm((current) => ({ ...current, providerId: event.target.value }))}
            />
          </div>
          <textarea
            placeholder="Clinical information and justification..."
            rows={3}
            value={preauthForm.clinicalInfo}
            onChange={(event) => setPreauthForm((current) => ({ ...current, clinicalInfo: event.target.value }))}
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg"
          />
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => {
              create.mutate(preauthForm);
            }} disabled={create.isPending || !preauthForm.coverageId.trim() || !preauthForm.requestType.trim() || !preauthForm.facilityId.trim() || !preauthForm.providerId.trim()}
              className="flex-1 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
              {create.isPending ? "Submitting..." : "Submit Pre-Auth"}
            </button>
          </div>
          {create.isSuccess && <p className="text-xs text-green-600">Pre-authorization submitted.</p>}
        </div>
      )}
      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <p className="text-sm text-gray-500">Lifecycle: <span className="font-mono text-xs">PENDING → APPROVED / DENIED → EXPIRED</span></p>
      </div>
    </div>
  );
}

// ── CONTRIBUTIONS TAB ────────────────────────────────────────────
function ContributionsTab() {
  const contributionsQ = useCoverageContributionsList();
  const contributionRows = (contributionsQ.data ?? []).map(asRecord);
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Contributions & Premiums</h3>
      <p className="text-sm text-gray-500">
        Read rows from <code className="text-xs">GET /internal/v1/coverage/contributions</code>.
      </p>

      {contributionsQ.isLoading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
        </div>
      ) : contributionsQ.isError ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          Could not load contribution rows.
        </div>
      ) : contributionRows.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <CreditCard className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-400 text-sm">No contribution records yet</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50 text-left">
                <th className="px-3 py-2 font-medium text-gray-600">Contribution</th>
                <th className="px-3 py-2 font-medium text-gray-600">Member</th>
                <th className="px-3 py-2 font-medium text-gray-600">Period</th>
                <th className="px-3 py-2 font-medium text-gray-600">Amount</th>
                <th className="px-3 py-2 font-medium text-gray-600">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {contributionRows.map((row, idx) => {
                const id = readUnknownString(row, "id", "contribution_id", "contributionId") || `row-${idx}`;
                const status = readUnknownString(row, "status", "payment_status", "state").toUpperCase() || "UNKNOWN";
                const currency = readUnknownString(row, "currency") || "USD";
                const start = readUnknownString(row, "period_start", "periodStart", "contribution_start");
                const end = readUnknownString(row, "period_end", "periodEnd", "contribution_end");
                return (
                  <tr key={`${id}-${idx}`}>
                    <td className="px-3 py-2 font-mono text-xs text-gray-700">{id}</td>
                    <td className="px-3 py-2 text-gray-700">
                      {readUnknownString(row, "member_id", "memberId", "coverage_id", "coverageId") || "—"}
                    </td>
                    <td className="px-3 py-2 text-gray-700">
                      {start || end ? `${start || "?"} → ${end || "?"}` : "—"}
                    </td>
                    <td className="px-3 py-2 text-gray-700">
                      {formatCoverageCurrency(readUnknownNumber(row, "amount", "total_amount", "paid_amount"), currency)}
                    </td>
                    <td className="px-3 py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs ${statusClass(status)}`}>{status}</span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ── SUBSIDIES TAB ────────────────────────────────────────────────
function SubsidiesTab() {
  const subsidiesQ = useCoverageSubsidiesList();
  const subsidyRows = (subsidiesQ.data ?? []).map(asRecord);

  return (
    <div className="space-y-4" data-testid="coverage-subsidies-tab">
      <h3 className="text-base font-semibold text-gray-900">Subsidy programmes</h3>
      <p className="text-sm text-gray-500">
        Governed programmes from <code className="text-xs">GET /internal/v1/coverage/subsidies</code> — government, donor, and facility assistance rails.
      </p>
      {subsidiesQ.isLoading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
        </div>
      ) : subsidiesQ.isError ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          Could not load subsidy programmes.
        </div>
      ) : subsidyRows.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-5 text-sm text-gray-500">
          No active subsidy programmes for this tenant.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50 text-left">
                <th className="px-3 py-2 font-medium text-gray-600">Programme</th>
                <th className="px-3 py-2 font-medium text-gray-600">Type</th>
                <th className="px-3 py-2 font-medium text-gray-600">Annual cap</th>
                <th className="px-3 py-2 font-medium text-gray-600">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {subsidyRows.map((row, idx) => (
                <tr key={`${readUnknownString(row, "id", "programCode", "program_code") || "sub"}-${idx}`}>
                  <td className="px-3 py-2 text-gray-700">
                    {readUnknownString(row, "programName", "program_name") || "—"}
                    <span className="ml-2 font-mono text-[10px] text-gray-400">
                      {readUnknownString(row, "programCode", "program_code")}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-gray-700">{readUnknownString(row, "subsidyType", "subsidy_type") || "—"}</td>
                  <td className="px-3 py-2 text-gray-700">
                    {formatCoverageCurrency(
                      readUnknownNumber(row, "annualCap", "annual_cap"),
                      readUnknownString(row, "currency") || "USD",
                    )}
                  </td>
                  <td className="px-3 py-2">
                    <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-800">
                      {readUnknownString(row, "status") || "ACTIVE"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ── APPEALS TAB ──────────────────────────────────────────────────
function AppealsTab() {
  const [showForm, setShowForm] = useState(false);
  const [selectedAppealId, setSelectedAppealId] = useState("");
  const [reviewerId, setReviewerId] = useState("");
  const [decision, setDecision] = useState<"UPHELD" | "OVERTURNED" | "PARTIAL">("UPHELD");
  const [decisionReason, setDecisionReason] = useState("");
  const [decidedBy, setDecidedBy] = useState("");
  const [appealForm, setAppealForm] = useState({
    claimId: "",
    coverageId: "",
    appellantId: "",
    reason: "",
    evidenceSummary: "",
  });
  const submitAppeal = useSubmitCoverageAppeal();
  const reviewAppeal = useReviewCoverageAppeal();
  const decideAppeal = useDecideCoverageAppeal();
  const appealsQ = useCoverageAppealsList();
  const appealRows = (appealsQ.data ?? []).map(asRecord);
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Claims Appeals</h3>
        <button onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700">
          <Plus className="w-4 h-4" /> File Appeal
        </button>
      </div>
      <p className="text-sm text-gray-500">
        Read rows from <code className="text-xs">GET /internal/v1/coverage/appeals</code>. New appeals submit through{" "}
        <code className="text-xs">POST /internal/v1/appeals</code>; operator review/decision uses{" "}
        <code className="text-xs">PUT /internal/v1/appeals/&#123;id&#125;/review|decide</code>.
      </p>
      {appealsQ.isLoading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
        </div>
      ) : appealsQ.isError ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          Could not load appeal rows.
        </div>
      ) : appealRows.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-5 text-sm text-gray-500">
          No appeal rows returned by the coverage service.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50 text-left">
                <th className="px-3 py-2 font-medium text-gray-600">Appeal</th>
                <th className="px-3 py-2 font-medium text-gray-600">Claim</th>
                <th className="px-3 py-2 font-medium text-gray-600">Coverage</th>
                <th className="px-3 py-2 font-medium text-gray-600">Status</th>
                <th className="px-3 py-2 font-medium text-gray-600">Filed</th>
                <th className="px-3 py-2 font-medium text-gray-600">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {appealRows.map((row, idx) => {
                const id = readUnknownString(row, "id", "appeal_id", "appealId") || `row-${idx}`;
                const status = readUnknownString(row, "status", "decision", "state").toUpperCase() || "UNKNOWN";
                return (
                  <tr key={`${id}-${idx}`}>
                    <td className="px-3 py-2 font-mono text-xs text-gray-700">{id}</td>
                    <td className="px-3 py-2 text-gray-700">{readUnknownString(row, "claim_id", "claimId") || "—"}</td>
                    <td className="px-3 py-2 text-gray-700">
                      {readUnknownString(row, "coverage_id", "coverageId", "member_id", "memberId") || "—"}
                    </td>
                    <td className="px-3 py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs ${statusClass(status)}`}>{status}</span>
                    </td>
                    <td className="px-3 py-2 text-xs text-gray-500">
                      {formatCoverageDate(readUnknownString(row, "filed_at", "filedAt", "created_at", "createdAt"))}
                    </td>
                    <td className="px-3 py-2">
                      <button
                        type="button"
                        className="rounded border border-amber-200 px-2 py-1 text-xs text-amber-800 hover:bg-amber-50"
                        onClick={() => setSelectedAppealId(id)}
                      >
                        Use
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {showForm && (
        <div className="bg-white rounded-lg border border-amber-200 p-5 space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">New Appeal</h4>
          <div className="grid grid-cols-2 gap-3">
            <input
              type="text"
              placeholder="Claim ID"
              value={appealForm.claimId}
              onChange={(event) => setAppealForm((current) => ({ ...current, claimId: event.target.value }))}
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
            />
            <input
              type="text"
              placeholder="Coverage ID (optional)"
              value={appealForm.coverageId}
              onChange={(event) => setAppealForm((current) => ({ ...current, coverageId: event.target.value }))}
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
            />
            <input
              type="text"
              placeholder="Appellant ID"
              value={appealForm.appellantId}
              onChange={(event) => setAppealForm((current) => ({ ...current, appellantId: event.target.value }))}
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
            />
            <input
              type="text"
              placeholder="Evidence summary"
              value={appealForm.evidenceSummary}
              onChange={(event) => setAppealForm((current) => ({ ...current, evidenceSummary: event.target.value }))}
              className="px-3 py-2 text-sm border border-gray-300 rounded-lg"
            />
          </div>
          <textarea
            placeholder="Appeal reason and supporting evidence..."
            rows={3}
            value={appealForm.reason}
            onChange={(event) => setAppealForm((current) => ({ ...current, reason: event.target.value }))}
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg"
          />
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => {
              submitAppeal.mutate(
                {
                  claimId: appealForm.claimId,
                  appellantId: appealForm.appellantId,
                  reason: appealForm.reason,
                  coverageId: appealForm.coverageId || undefined,
                  evidence: { summary: appealForm.evidenceSummary },
                },
                { onSuccess: () => setShowForm(false) },
              );
            }} disabled={submitAppeal.isPending || !appealForm.claimId.trim() || !appealForm.appellantId.trim() || !appealForm.reason.trim()}
              className="flex-1 py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700 disabled:opacity-50">
              {submitAppeal.isPending ? "Submitting..." : "Submit Appeal"}
            </button>
          </div>
          {submitAppeal.isSuccess ? <p className="text-xs text-green-600">Appeal submitted.</p> : null}
          {submitAppeal.isError ? <p className="text-xs text-red-600">Appeal submission failed.</p> : null}
        </div>
      )}
      <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-5">
        <h4 className="text-sm font-semibold text-gray-900">Appeal operator actions</h4>
        <p className="mt-1 text-xs text-amber-900">
          Review moves an appeal from PENDING to UNDER_REVIEW. Decisions on OVERTURNED or PARTIAL trigger the backend
          resubmission notification for the linked claim.
        </p>
        <div className="mt-3 grid gap-3 md:grid-cols-2">
          <input
            type="text"
            placeholder="Appeal ID"
            value={selectedAppealId}
            onChange={(event) => setSelectedAppealId(event.target.value)}
            className="px-3 py-2 text-sm border border-amber-200 rounded-lg"
          />
          <input
            type="text"
            placeholder="Reviewer ID"
            value={reviewerId}
            onChange={(event) => setReviewerId(event.target.value)}
            className="px-3 py-2 text-sm border border-amber-200 rounded-lg"
          />
          <select
            value={decision}
            onChange={(event) => setDecision(event.target.value as "UPHELD" | "OVERTURNED" | "PARTIAL")}
            className="px-3 py-2 text-sm border border-amber-200 rounded-lg"
          >
            <option value="UPHELD">UPHELD</option>
            <option value="OVERTURNED">OVERTURNED</option>
            <option value="PARTIAL">PARTIAL</option>
          </select>
          <input
            type="text"
            placeholder="Decided by"
            value={decidedBy}
            onChange={(event) => setDecidedBy(event.target.value)}
            className="px-3 py-2 text-sm border border-amber-200 rounded-lg"
          />
        </div>
        <textarea
          placeholder="Decision reason"
          rows={2}
          value={decisionReason}
          onChange={(event) => setDecisionReason(event.target.value)}
          className="mt-3 w-full px-3 py-2 text-sm border border-amber-200 rounded-lg"
        />
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            disabled={reviewAppeal.isPending || !selectedAppealId.trim() || !reviewerId.trim()}
            onClick={() => reviewAppeal.mutate({ appealId: selectedAppealId, reviewerId })}
            className="rounded-lg bg-amber-700 px-3 py-2 text-sm font-medium text-white hover:bg-amber-800 disabled:opacity-50"
          >
            {reviewAppeal.isPending ? "Marking..." : "Mark under review"}
          </button>
          <button
            type="button"
            disabled={decideAppeal.isPending || !selectedAppealId.trim() || !decisionReason.trim() || !decidedBy.trim()}
            onClick={() => decideAppeal.mutate({ appealId: selectedAppealId, decision, decisionReason, decidedBy })}
            className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {decideAppeal.isPending ? "Deciding..." : "Record decision"}
          </button>
        </div>
        {reviewAppeal.isError ? <p className="mt-2 text-xs text-red-700">Review transition failed.</p> : null}
        {decideAppeal.isError ? <p className="mt-2 text-xs text-red-700">Decision failed; check status is UNDER_REVIEW.</p> : null}
        {reviewAppeal.isSuccess || decideAppeal.isSuccess ? (
          <p className="mt-2 text-xs text-green-700">Appeal action accepted and rows will refresh.</p>
        ) : null}
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <p className="text-sm text-gray-500">Appeal workflow: <span className="font-mono text-xs">PENDING → UNDER_REVIEW → UPHELD / OVERTURNED / PARTIAL</span></p>
      </div>
      {appealRows.length === 0 ? <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
        <Scale className="w-10 h-10 text-gray-300 mx-auto mb-3" />
        <p className="text-gray-400 text-sm">No appeals filed</p>
      </div> : null}
    </div>
  );
}

// ── PROVIDER CONTRACTING TAB ─────────────────────────────────────
function ContractingTab() {
  const contractsQ = useProviderContracts();
  const rows = (contractsQ.data ?? []).map((row) =>
    row && typeof row === "object" ? (row as Record<string, unknown>) : {},
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-semibold text-gray-900">Provider Contracting & Network Management</h3>
        <Link
          href="/coverage/contracts"
          className="text-sm font-medium text-impilo-700 hover:text-impilo-800"
        >
          Open full contracts admin →
        </Link>
      </div>
      {contractsQ.isLoading ? (
        <p className="text-sm text-gray-500">Loading payer–provider contracts from coverage BFF…</p>
      ) : rows.length === 0 ? (
        <div className="rounded-lg border border-gray-200 bg-white p-8 text-center text-sm text-gray-500">
          No active contracts in the current scope. Use contracts admin to create payer–provider agreements.
        </div>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
          {rows.slice(0, 8).map((row, idx) => {
            const id = String(row.id ?? row.contractId ?? idx);
            const provider = String(row.providerId ?? row.providerName ?? "Provider");
            const status = String(row.status ?? "UNKNOWN");
            return (
              <li key={id} className="flex items-center justify-between px-4 py-3 text-sm">
                <span className="font-medium text-gray-900">{provider}</span>
                <span className="text-xs text-gray-500">{status}</span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

// ── PAYER INTELLIGENCE TAB ───────────────────────────────────────
function IntelligenceTab() {
  const [intelView, setIntelView] = useState<"analytics" | "geography">("analytics");
  const utilizationQ = useCoverageUtilizationList();
  const utilizationRows = (utilizationQ.data ?? []).map(asRecord);
  const { data: remittances = [], isLoading } = useCoverageRemittances();
  const n = remittances.length;
  const total = remittances.reduce((s, r) => s + r.amount, 0);
  const avg = n > 0 ? total / n : 0;
  const cur = remittances[0]?.currency ?? "USD";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-semibold text-gray-900">Payer Intelligence & Analytics</h3>
        <div className="flex gap-1 rounded-lg border border-gray-200 bg-gray-50 p-1 text-xs">
          <button
            type="button"
            onClick={() => setIntelView("analytics")}
            className={`rounded-md px-3 py-1.5 font-medium ${intelView === "analytics" ? "bg-white text-violet-700 shadow-sm" : "text-gray-600"}`}
          >
            Analytics
          </button>
          <button
            type="button"
            onClick={() => setIntelView("geography")}
            className={`rounded-md px-3 py-1.5 font-medium ${intelView === "geography" ? "bg-white text-violet-700 shadow-sm" : "text-gray-600"}`}
          >
            Geography
          </button>
        </div>
      </div>

      {intelView === "geography" ? <CoverageGeoMapPanel /> : null}

      {intelView === "analytics" ? (
        <>
      <p className="text-sm text-gray-500">
        Remittance snapshot plus utilization rows from{" "}
        <code className="text-xs">GET /internal/v1/coverage/utilization</code>. Fraud and MLR KPIs still require a
        dedicated reporting service.
      </p>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-2xl font-bold text-gray-900">{isLoading ? "…" : n}</p>
          <p className="text-xs text-gray-500">Remittance rows</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-2xl font-bold text-gray-900">{isLoading ? "…" : formatCoverageCurrency(total, cur)}</p>
          <p className="text-xs text-gray-500">Total remitted (sum)</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-2xl font-bold text-green-700">{isLoading || n === 0 ? "—" : formatCoverageCurrency(avg, cur)}</p>
          <p className="text-xs text-gray-500">Avg remittance</p>
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-2xl font-bold text-amber-700">{utilizationQ.isLoading ? "…" : utilizationRows.length}</p>
          <p className="text-xs text-gray-500">Utilization rows</p>
          <p className="text-[10px] text-gray-400 mt-1">BFF coverage utilization list</p>
        </div>
      </div>
      {utilizationQ.isError ? (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          Could not load utilization rows.
        </div>
      ) : null}
      {!utilizationQ.isLoading && !utilizationQ.isError && utilizationRows.length > 0 ? (
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50 text-left">
                <th className="px-3 py-2 font-medium text-gray-600">Metric</th>
                <th className="px-3 py-2 font-medium text-gray-600">Value</th>
                <th className="px-3 py-2 font-medium text-gray-600">Coverage</th>
                <th className="px-3 py-2 font-medium text-gray-600">Period</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {utilizationRows.slice(0, 25).map((row, idx) => (
                <tr key={`${readUnknownString(row, "id", "metric_id", "metricId") || "metric"}-${idx}`}>
                  <td className="px-3 py-2 text-gray-700">
                    {readUnknownString(row, "metric", "metric_name", "metricName", "name") || "—"}
                  </td>
                  <td className="px-3 py-2 text-gray-700">
                    {readUnknownString(row, "value", "metric_value", "metricValue") ||
                      String(readUnknownNumber(row, "value", "metric_value", "metricValue"))}
                  </td>
                  <td className="px-3 py-2 text-gray-700">
                    {readUnknownString(row, "coverage_id", "coverageId", "scheme_id", "schemeId") || "—"}
                  </td>
                  <td className="px-3 py-2 text-xs text-gray-500">
                    {readUnknownString(row, "period", "period_label", "periodLabel") ||
                      [
                        readUnknownString(row, "period_start", "periodStart"),
                        readUnknownString(row, "period_end", "periodEnd"),
                      ]
                        .filter(Boolean)
                        .join(" → ") ||
                      "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      <div className="bg-white rounded-lg border border-gray-200 p-5" data-testid="coverage-intelligence-advanced-note">
        <h4 className="text-sm font-semibold text-gray-900 mb-2">Advanced analytics (deferred)</h4>
        <p className="text-xs text-gray-600">
          Remittance and utilization KPIs above are live from coverage-service. Fraud scoring, MLR benchmarking, and forecasting remain on the reporting-service roadmap.
        </p>
      </div>
        </>
      ) : null}
    </div>
  );
}
