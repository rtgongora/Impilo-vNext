"use client";

/**
 * Coverage Operations — Schemes, eligibility, claims, settlement, preauth.
 * Route: /coverage
 * Lovable: CoverageOperations with 11 tabs. Runtime: 5 core tabs backed by coverage-service.
 */

import { useState, type FormEvent } from "react";
import {
  Shield, UserCheck, FileText, DollarSign, Briefcase,
  Loader2, CheckCircle2, AlertCircle, Plus,
  Users, CreditCard, Scale, ShieldCheck,
} from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCoverageClaims,
  useCoveragePlans,
  useCoverageRemittances,
  useCreateCoverageClaim,
  useEnrollCoverageMember,
  type CoverageClaim,
} from "@/hooks/queries/useCoverage";
import { apiClient } from "@/lib/api-client";

type ActiveTab = "dashboard" | "schemes" | "membership" | "eligibility" | "contracting" | "preauth" | "claims" | "contributions" | "settlement" | "appeals" | "intelligence";

const TABS: { key: ActiveTab; label: string; icon: typeof Shield }[] = [
  { key: "dashboard", label: "Dashboard", icon: Shield },
  { key: "schemes", label: "Schemes", icon: Briefcase },
  { key: "membership", label: "Membership", icon: Users },
  { key: "eligibility", label: "Eligibility", icon: UserCheck },
  { key: "contracting", label: "Contracting", icon: FileText },
  { key: "preauth", label: "Pre-Auth", icon: ShieldCheck },
  { key: "claims", label: "Claims", icon: FileText },
  { key: "contributions", label: "Contributions", icon: CreditCard },
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
        {activeTab === "settlement" && <SettlementTab />}
        {activeTab === "appeals" && <AppealsTab />}
        {activeTab === "intelligence" && <IntelligenceTab />}
      </PageShell>
    </AppLayout>
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
            <input type="text" placeholder="Coverage ID" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="claim-coverage" />
            <input type="text" placeholder="Claim Type (e.g., OUTPATIENT)" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="claim-type" />
            <input type="text" placeholder="Facility ID" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="claim-facility" />
            <input type="number" placeholder="Total Amount" step="0.01" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="claim-amount" />
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button type="button" onClick={() => {
              const coverageId = (document.getElementById("claim-coverage") as HTMLInputElement)?.value;
              const claimType = (document.getElementById("claim-type") as HTMLInputElement)?.value;
              const facilityId = (document.getElementById("claim-facility") as HTMLInputElement)?.value;
              const totalAmount = (document.getElementById("claim-amount") as HTMLInputElement)?.value;
              submit.mutate(
                { coverageId, claimType, facilityId, totalAmount },
                {
                  onSuccess: () => {
                    setShowForm(false);
                    setListCoverageId(coverageId);
                    setAppliedCoverageId(coverageId.trim() || null);
                  },
                }
              );
            }} disabled={submit.isPending}
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
        Rows from <code className="text-xs">GET /internal/v1/coverage/remittances</code>. Downstream payers may use longer state machines than the fields returned here.
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
            <input type="text" placeholder="Client ID (CPID)" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="mem-client" />
            <input type="text" placeholder="Plan ID" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="mem-plan" />
            <input type="text" placeholder="Member Number" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="mem-number" />
            <select className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="mem-rel">
              <option value="SELF">Self</option><option value="SPOUSE">Spouse</option>
              <option value="CHILD">Child</option><option value="DEPENDENT">Dependent</option>
            </select>
          </div>
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button type="button" onClick={() => {
              const clientId = (document.getElementById("mem-client") as HTMLInputElement)?.value;
              const planId = (document.getElementById("mem-plan") as HTMLInputElement)?.value;
              const memberNumber = (document.getElementById("mem-number") as HTMLInputElement)?.value;
              const relationship = (document.getElementById("mem-rel") as HTMLSelectElement)?.value;
              enroll.mutate(
                { clientId, planId, memberNumber, relationship },
                { onSuccess: () => setShowForm(false) }
              );
            }} disabled={enroll.isPending}
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
      {showForm && (
        <div className="bg-white rounded-lg border border-blue-200 p-5 space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">Pre-Authorization Request</h4>
          <div className="grid grid-cols-2 gap-3">
            <input type="text" placeholder="Coverage ID" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="pa-coverage" />
            <input type="text" placeholder="Request Type (e.g., SURGERY)" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="pa-type" />
            <input type="text" placeholder="Facility ID" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="pa-facility" />
            <input type="text" placeholder="Provider ID" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" id="pa-provider" />
          </div>
          <textarea placeholder="Clinical information and justification..." rows={3} id="pa-clinical"
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => {
              const coverageId = (document.getElementById("pa-coverage") as HTMLInputElement)?.value;
              const requestType = (document.getElementById("pa-type") as HTMLInputElement)?.value;
              const facilityId = (document.getElementById("pa-facility") as HTMLInputElement)?.value;
              const providerId = (document.getElementById("pa-provider") as HTMLInputElement)?.value;
              const clinicalInfo = (document.getElementById("pa-clinical") as HTMLTextAreaElement)?.value;
              create.mutate({ coverageId, requestType, facilityId, providerId, clinicalInfo });
            }} disabled={create.isPending}
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
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Contributions & Premiums</h3>
      <p className="text-sm text-gray-500">Track member premium payments, contribution periods, and payment status.</p>
      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <div className="grid grid-cols-4 gap-4 mb-4">
          {([["PAID", "green"], ["DUE", "amber"], ["OVERDUE", "red"], ["WAIVED", "gray"]] as const).map(([status, color]) => (
            <div key={status} className={`bg-${color}-50 rounded-lg p-3 text-center`}>
              <p className={`text-lg font-bold text-${color}-700`}>—</p>
              <p className={`text-[10px] text-${color}-600`}>{status}</p>
            </div>
          ))}
        </div>
        <p className="text-xs text-gray-400">Lifecycle: DUE → PAID / OVERDUE → WAIVED / REFUNDED</p>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
        <CreditCard className="w-10 h-10 text-gray-300 mx-auto mb-3" />
        <p className="text-gray-400 text-sm">No contribution records yet</p>
      </div>
    </div>
  );
}

// ── APPEALS TAB ──────────────────────────────────────────────────
function AppealsTab() {
  const [showForm, setShowForm] = useState(false);
  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Claims Appeals</h3>
        <button onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700">
          <Plus className="w-4 h-4" /> File Appeal
        </button>
      </div>
      {showForm && (
        <div className="bg-white rounded-lg border border-amber-200 p-5 space-y-3">
          <h4 className="text-sm font-semibold text-gray-900">New Appeal</h4>
          <div className="grid grid-cols-2 gap-3">
            <input type="text" placeholder="Claim ID" id="appeal-claim" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            <input type="text" placeholder="Coverage ID (optional)" id="appeal-coverage" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <textarea placeholder="Appeal reason and supporting evidence..." rows={3} id="appeal-reason"
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => {
              const claimId = (document.getElementById("appeal-claim") as HTMLInputElement)?.value;
              const coverageId = (document.getElementById("appeal-coverage") as HTMLInputElement)?.value;
              const reason = (document.getElementById("appeal-reason") as HTMLTextAreaElement)?.value;
              if (claimId && reason) apiClient.post("/internal/v1/coverage/claims", { coverageId, claimType: "APPEAL", totalAmount: "0", facilityId: claimId }).then(() => setShowForm(false));
            }} className="flex-1 py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700">Submit Appeal</button>
          </div>
        </div>
      )}
      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <p className="text-sm text-gray-500">Appeal workflow: <span className="font-mono text-xs">PENDING → UNDER_REVIEW → UPHELD / OVERTURNED / PARTIAL</span></p>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
        <Scale className="w-10 h-10 text-gray-300 mx-auto mb-3" />
        <p className="text-gray-400 text-sm">No appeals filed</p>
      </div>
    </div>
  );
}

// ── PROVIDER CONTRACTING TAB ─────────────────────────────────────
function ContractingTab() {
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Provider Contracting & Network Management</h3>
      <p className="text-sm text-gray-500">
        No contracting or network API is exposed on the Experience BFF yet. This tab is intentionally empty instead of showing demo providers.
      </p>
      <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
        <Briefcase className="w-10 h-10 text-gray-300 mx-auto mb-3" />
        <p className="text-gray-400 text-sm">Contract directory not wired</p>
      </div>
    </div>
  );
}

// ── PAYER INTELLIGENCE TAB ───────────────────────────────────────
function IntelligenceTab() {
  const { data: remittances = [], isLoading } = useCoverageRemittances();
  const n = remittances.length;
  const total = remittances.reduce((s, r) => s + r.amount, 0);
  const avg = n > 0 ? total / n : 0;
  const cur = remittances[0]?.currency ?? "USD";

  return (
    <div className="space-y-6">
      <h3 className="text-base font-semibold text-gray-900">Payer Intelligence & Analytics</h3>
      <p className="text-sm text-gray-500">
        Fraud, loss ratio, and timing KPIs need a reporting service. Below is a simple slice derived only from remittance rows already loaded for coverage operations.
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
          <p className="text-2xl font-bold text-amber-700">—</p>
          <p className="text-xs text-gray-500">Fraud / MLR / timing</p>
          <p className="text-[10px] text-gray-400 mt-1">Not in BFF</p>
        </div>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <h4 className="text-sm font-semibold text-gray-900 mb-2">Future analytics domains</h4>
        <p className="text-xs text-gray-600">
          When reporting APIs exist, this area can host fraud, utilization, benchmarking, and forecasting without placeholder numbers.
        </p>
      </div>
    </div>
  );
}
