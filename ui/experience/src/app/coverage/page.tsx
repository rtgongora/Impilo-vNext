"use client";

/**
 * Coverage Operations — Schemes, eligibility, claims, settlement, preauth.
 * Route: /coverage
 * Lovable: CoverageOperations with 11 tabs. Runtime: 5 core tabs backed by coverage-service.
 */

import { useState, type FormEvent } from "react";
import {
  Shield, UserCheck, FileText, DollarSign, Briefcase,
  Loader2, CheckCircle2, AlertCircle, Search, Plus, Clock,
  Users, CreditCard, Scale, ShieldCheck,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type ActiveTab = "dashboard" | "schemes" | "membership" | "eligibility" | "preauth" | "claims" | "contributions" | "settlement" | "appeals";

const TABS: { key: ActiveTab; label: string; icon: typeof Shield }[] = [
  { key: "dashboard", label: "Dashboard", icon: Shield },
  { key: "schemes", label: "Schemes", icon: Briefcase },
  { key: "membership", label: "Membership", icon: Users },
  { key: "eligibility", label: "Eligibility", icon: UserCheck },
  { key: "preauth", label: "Pre-Auth", icon: ShieldCheck },
  { key: "claims", label: "Claims", icon: FileText },
  { key: "contributions", label: "Contributions", icon: CreditCard },
  { key: "settlement", label: "Settlement", icon: DollarSign },
  { key: "appeals", label: "Appeals", icon: Scale },
];

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
        {activeTab === "preauth" && <PreauthTab />}
        {activeTab === "claims" && <ClaimsTab />}
        {activeTab === "contributions" && <ContributionsTab />}
        {activeTab === "settlement" && <SettlementTab />}
        {activeTab === "appeals" && <AppealsTab />}
      </PageShell>
    </AppLayout>
  );
}

// ── DASHBOARD TAB ────────────────────────────────────────────────
function DashboardTab() {
  const { data: plansData } = useQuery<{ data: unknown[] }>({
    queryKey: ["coverage-plans"], queryFn: () => apiClient.get("/internal/v1/coverage/plans"),
  });
  const { data: remittancesData } = useQuery<{ data: unknown[] }>({
    queryKey: ["coverage-remittances"], queryFn: () => apiClient.get("/internal/v1/coverage/remittances"),
  });

  const plans = (plansData?.data ?? []) as Array<Record<string, unknown>>;
  const remittances = (remittancesData?.data ?? []) as Array<Record<string, unknown>>;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-4 gap-4">
        <div className="bg-violet-50 rounded-lg border border-violet-200 p-4 text-center">
          <p className="text-2xl font-bold text-violet-700">{plans.length}</p>
          <p className="text-xs text-violet-600">Active Schemes</p>
        </div>
        <div className="bg-green-50 rounded-lg border border-green-200 p-4 text-center">
          <p className="text-2xl font-bold text-green-700">—</p>
          <p className="text-xs text-green-600">Active Members</p>
        </div>
        <div className="bg-blue-50 rounded-lg border border-blue-200 p-4 text-center">
          <p className="text-2xl font-bold text-blue-700">—</p>
          <p className="text-xs text-blue-600">Pending Claims</p>
        </div>
        <div className="bg-amber-50 rounded-lg border border-amber-200 p-4 text-center">
          <p className="text-2xl font-bold text-amber-700">{remittances.length}</p>
          <p className="text-xs text-amber-600">Remittances</p>
        </div>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <h3 className="text-sm font-semibold text-gray-900 mb-3">Coverage Pipeline</h3>
        <div className="flex items-center gap-1 text-[10px] overflow-x-auto pb-2">
          {["INITIATED", "VERIFIED", "PREAUTH", "SUBMITTED", "ADJUDICATED", "APPROVED", "REMITTED", "PAID", "SETTLED", "RECONCILED"].map((stage, i) => (
            <div key={stage} className="flex items-center gap-1">
              <div className="px-2 py-1 bg-violet-100 text-violet-700 rounded whitespace-nowrap">{stage}</div>
              {i < 9 && <span className="text-gray-300">→</span>}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── SCHEMES TAB ──────────────────────────────────────────────────
function SchemesTab() {
  const { data, isLoading } = useQuery<{ data: Array<{ id?: string; plan_code?: string; plan_name?: string; payer_id?: string; plan_type?: string; status?: string; [k: string]: unknown }> }>({
    queryKey: ["coverage-plans"], queryFn: () => apiClient.get("/internal/v1/coverage/plans"),
  });
  const plans = data?.data ?? [];

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
          {plans.map((plan, i) => (
            <div key={plan.id ?? i} className="bg-white rounded-lg border border-gray-200 p-5 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-900">{plan.plan_name ?? "Unnamed Plan"}</p>
                <p className="text-xs text-gray-500">{plan.plan_code ?? "—"} · {plan.plan_type ?? "STANDARD"} · Payer: {plan.payer_id ?? "—"}</p>
              </div>
              <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${plan.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"}`}>
                {plan.status ?? "—"}
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
  const queryClient = useQueryClient();

  const submit = useMutation({
    mutationFn: (body: Record<string, string>) =>
      apiClient.post("/internal/v1/coverage/claims", body),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ["coverage-claims"] }); },
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Claims & Adjudication</h3>
        <button onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
          <Plus className="w-4 h-4" /> Submit Claim
        </button>
      </div>

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
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => {
              const coverageId = (document.getElementById("claim-coverage") as HTMLInputElement)?.value;
              const claimType = (document.getElementById("claim-type") as HTMLInputElement)?.value;
              const facilityId = (document.getElementById("claim-facility") as HTMLInputElement)?.value;
              const totalAmount = (document.getElementById("claim-amount") as HTMLInputElement)?.value;
              submit.mutate({ coverageId, claimType, facilityId, totalAmount });
            }} disabled={submit.isPending}
              className="flex-1 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
              {submit.isPending ? "Submitting..." : "Submit Claim"}
            </button>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
        <FileText className="w-10 h-10 text-gray-300 mx-auto mb-3" />
        <p className="text-gray-400 text-sm">Claims will appear here after submission</p>
        <p className="text-gray-400 text-xs mt-1">Lifecycle: SUBMITTED → ADJUDICATED → APPROVED → REMITTED</p>
      </div>
    </div>
  );
}

// ── SETTLEMENT TAB ───────────────────────────────────────────────
function SettlementTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["coverage-remittances"], queryFn: () => apiClient.get("/internal/v1/coverage/remittances"),
  });
  const remittances = data?.data ?? [];

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Settlement & Remittance</h3>
      <p className="text-sm text-gray-500">13-state settlement lifecycle: INITIATED → VERIFIED → PREAUTHORIZED → SUBMITTED → ADJUDICATED → APPROVED → REMITTED → PAID → SETTLED → RECONCILED</p>

      {isLoading ? (
        <div className="flex items-center justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-gray-400" /></div>
      ) : remittances.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <DollarSign className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-gray-400 text-sm">No remittances recorded</p>
        </div>
      ) : (
        <div className="space-y-3">
          {remittances.map((rem, i) => (
            <div key={String(rem.id ?? i)} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-900">Remittance {rem.reference_number ?? rem.id}</p>
                <p className="text-xs text-gray-500">Payer: {String(rem.payer_id ?? "—")} · Provider: {String(rem.provider_ref ?? "—")}</p>
              </div>
              <div className="text-right">
                <p className="text-sm font-mono font-bold text-gray-900">{rem.currency ?? "USD"} {Number(rem.amount ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}</p>
                <span className={`px-2 py-0.5 text-xs rounded-full ${rem.status === "PAID" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"}`}>
                  {String(rem.status ?? "PENDING")}
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
  const queryClient = useQueryClient();
  const enroll = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/coverage/members", body),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ["coverage-members"] }); },
  });

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
            <button onClick={() => {
              const clientId = (document.getElementById("mem-client") as HTMLInputElement)?.value;
              const planId = (document.getElementById("mem-plan") as HTMLInputElement)?.value;
              const memberNumber = (document.getElementById("mem-number") as HTMLInputElement)?.value;
              const relationship = (document.getElementById("mem-rel") as HTMLSelectElement)?.value;
              enroll.mutate({ clientId, planId, memberNumber, relationship });
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
            <input type="text" placeholder="Claim ID" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            <input type="text" placeholder="Coverage ID (optional)" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          </div>
          <textarea placeholder="Appeal reason and supporting evidence..." rows={3}
            className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button className="flex-1 py-2 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-700">Submit Appeal</button>
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
