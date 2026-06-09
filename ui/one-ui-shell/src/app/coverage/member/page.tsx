"use client";

/**
 * Member coverage dashboard — self-service view of plans, eligibility, claims, contributions, preauths, appeals.
 * Route: /coverage/member
 */

import Link from "next/link";
import type { ReactNode } from "react";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  BarChart3,
  ClipboardList,
  Gavel,
  HeartPulse,
  History,
  Loader2,
  PiggyBank,
  RefreshCw,
  Shield,
  Stethoscope,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useCheckEligibility,
  useMyAppeals,
  useMyClaimHistory,
  useMyContributions,
  useMyCoveragePlans,
  useMyEligibility,
  useMyPreauths,
  useMyUtilization,
  useSubmitAppeal,
} from "@/hooks/queries/useMemberCoverage";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

function readNum(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

function formatMonthYear(iso: string) {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { month: "short", year: "numeric" });
}

function utilizationPct(used: number, limit: number): number | null {
  if (limit <= 0 || Number.isNaN(limit) || Number.isNaN(used)) return null;
  return Math.min(100, (used / limit) * 100);
}

function utilizationBarTone(pct: number) {
  if (pct < 50) return "bg-green-500";
  if (pct <= 80) return "bg-amber-500";
  return "bg-red-500";
}

const CLAIM_STATUS: Record<string, string> = {
  SUBMITTED: "bg-blue-100 text-blue-800",
  ADJUDICATED: "bg-amber-100 text-amber-800",
  PAID: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
  DRAFT: "bg-gray-100 text-gray-700",
};

const PREAUTH_STATUS: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  APPROVED: "bg-green-100 text-green-800",
  DENIED: "bg-red-100 text-red-800",
  EXPIRED: "bg-gray-100 text-gray-700",
};

const CONTRIB_STATUS: Record<string, string> = {
  DUE: "bg-amber-100 text-amber-800",
  PAID: "bg-green-100 text-green-800",
  OVERDUE: "bg-red-100 text-red-800",
  WAIVED: "bg-gray-100 text-gray-700",
};

const APPEAL_STATUS: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  UNDER_REVIEW: "bg-blue-100 text-blue-800",
  APPROVED: "bg-green-100 text-green-800",
  DENIED: "bg-red-100 text-red-800",
};

function Section({
  title,
  icon,
  isLoading,
  isError,
  children,
}: {
  title: string;
  icon: ReactNode;
  isLoading: boolean;
  isError: boolean;
  children: ReactNode;
}) {
  return (
    <section className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
      <div className="flex items-center gap-2 border-b border-gray-100 px-4 py-3 bg-violet-50/60">
        {icon}
        <h2 className="text-sm font-semibold text-gray-900">{title}</h2>
      </div>
      <div className="p-4">
        {isLoading && (
          <div className="flex items-center gap-2 text-gray-600 py-8 text-sm">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading…
          </div>
        )}
        {!isLoading && isError && (
          <p className="text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
            Could not load this section.
          </p>
        )}
        {!isLoading && !isError && children}
      </div>
    </section>
  );
}

export default function MemberCoveragePage() {
  const cpid = useAuthStore((s) => s.user?.id) ?? "";
  const [claimPage, setClaimPage] = useState(0);
  const pageSize = 10;

  const plansQ = useMyCoveragePlans(cpid || null);
  const eligQ = useMyEligibility(cpid || null);
  const claimsQ = useMyClaimHistory(cpid || null, claimPage, pageSize);
  const contribQ = useMyContributions(cpid || null);
  const preauthQ = useMyPreauths(cpid || null);
  const utilizationQ = useMyUtilization(cpid || null);
  const appealsQ = useMyAppeals(cpid || null);

  const checkEligibility = useCheckEligibility();
  const submitAppeal = useSubmitAppeal();

  const plans = useMemo(() => (plansQ.data ?? []).map(asRecord), [plansQ.data]);
  const firstCoverageId = readStr(plans[0] ?? {}, "id");

  const [eligCoverageId, setEligCoverageId] = useState("");
  const [eligService, setEligService] = useState("");

  const [appealOpen, setAppealOpen] = useState(false);
  const [appealForm, setAppealForm] = useState({ claimId: "", coverageId: "", reason: "" });

  const claimsRaw = useMemo(() => (claimsQ.data ?? []).map(asRecord), [claimsQ.data]);
  const claimsSlice = useMemo(() => {
    const from = claimPage * pageSize;
    return claimsRaw.slice(from, from + pageSize);
  }, [claimsRaw, claimPage, pageSize]);

  const contributions = useMemo(() => (contribQ.data ?? []).map(asRecord), [contribQ.data]);
  const preauths = useMemo(() => (preauthQ.data ?? []).map(asRecord), [preauthQ.data]);
  const utilizationRows = useMemo(() => (utilizationQ.data ?? []).map(asRecord), [utilizationQ.data]);
  const appeals = useMemo(() => (appealsQ.data ?? []).map(asRecord), [appealsQ.data]);
  const eligibilityRows = useMemo(() => (eligQ.data ?? []).map(asRecord), [eligQ.data]);
  const latestEligibility = eligibilityRows[0];

  const runCheck = () => {
    const cov = eligCoverageId.trim() || firstCoverageId;
    if (!cov || !cpid) return;
    checkEligibility.mutate({
      coverageId: cov,
      patientRef: cpid,
      serviceCode: eligService.trim() || undefined,
    });
  };

  const sendAppeal = () => {
    if (!appealForm.claimId.trim() || !appealForm.reason.trim() || !cpid) return;
    submitAppeal.mutate(
      {
        claimId: appealForm.claimId.trim(),
        coverageId: appealForm.coverageId.trim() || undefined,
        reason: appealForm.reason.trim(),
        appellantType: "MEMBER",
        appellantId: cpid,
      },
      {
        onSuccess: () => {
          setAppealOpen(false);
          setAppealForm({ claimId: "", coverageId: "", reason: "" });
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="My Coverage"
        subtitle="Plans, eligibility, claims, contributions, pre-authorizations, benefit utilization, and appeals"
        icon={<Shield className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/coverage"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to coverage operations
          </Link>
        </div>

        {!cpid && (
          <p className="mb-4 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-4 py-3">
            Sign in to load your member coverage. A Health ID (CPID) is required for these APIs.
          </p>
        )}

        <div className="flex flex-col gap-6">
          <Section
            title="My Plans"
            icon={<Shield className="h-4 w-4 text-violet-600" />}
            isLoading={plansQ.isLoading}
            isError={plansQ.isError}
          >
            {plans.length === 0 ? (
              <p className="text-sm text-gray-500">No active coverage plans found for your profile.</p>
            ) : (
              <div className="overflow-x-auto" data-testid="coverage-member-plans-table">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left text-gray-600">
                      <th className="pb-2 pr-2">Plan</th>
                      <th className="pb-2 pr-2">Payer</th>
                      <th className="pb-2 pr-2">Status</th>
                      <th className="pb-2">Effective</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {plans.map((row, i) => {
                      const name = readStr(row, "planName", "plan_name");
                      const payer = readStr(row, "payerId", "payer_id");
                      const status = readStr(row, "status") || "ACTIVE";
                      const from = readStr(row, "effectiveFrom", "effective_from");
                      const to = readStr(row, "effectiveTo", "effective_to");
                      return (
                        <tr key={readStr(row, "id") || i}>
                          <td className="py-2 pr-2 font-medium text-gray-900">{name || "—"}</td>
                          <td className="py-2 pr-2 text-gray-700">{payer || "—"}</td>
                          <td className="py-2 pr-2">
                            <span
                              className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                                status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-700"
                              }`}
                            >
                              {status}
                            </span>
                          </td>
                          <td className="py-2 text-gray-600 whitespace-nowrap">
                            {from || "—"} {to ? `→ ${to}` : ""}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Section>

          <Section
            title="Eligibility status"
            icon={<HeartPulse className="h-4 w-4 text-rose-600" />}
            isLoading={eligQ.isLoading}
            isError={eligQ.isError}
          >
            {latestEligibility ? (
              <div className="space-y-2 text-sm">
                <p>
                  <span className="font-medium text-gray-800">Last check: </span>
                  <span
                    className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                      readStr(latestEligibility, "resultCode", "result_code") === "ELIGIBLE"
                        ? "bg-green-100 text-green-800"
                        : "bg-amber-100 text-amber-800"
                    }`}
                  >
                    {readStr(latestEligibility, "resultCode", "result_code") || "—"}
                  </span>
                </p>
                <p className="text-gray-700">
                  {readStr(latestEligibility, "resultMessage", "result_message") || "—"}
                </p>
              </div>
            ) : (
              <p className="text-sm text-gray-500">No eligibility checks on file yet.</p>
            )}
            <div className="mt-4 space-y-2 border-t border-gray-100 pt-4">
              <p className="text-xs font-semibold text-gray-700">Run a new check</p>
              <div className="flex flex-col sm:flex-row gap-2">
                <input
                  className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  placeholder="Coverage UUID (defaults to first plan)"
                  value={eligCoverageId}
                  onChange={(e) => setEligCoverageId(e.target.value)}
                />
                <input
                  className="sm:w-40 rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  placeholder="Service code"
                  value={eligService}
                  onChange={(e) => setEligService(e.target.value)}
                />
                <button
                  type="button"
                  disabled={!cpid || checkEligibility.isPending}
                  onClick={runCheck}
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                >
                  {checkEligibility.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <RefreshCw className="h-4 w-4" />
                  )}
                  Check now
                </button>
              </div>
              {checkEligibility.isError && (
                <p className="text-xs text-red-600">Eligibility check failed. Verify coverage UUID.</p>
              )}
            </div>
          </Section>

          <Section
            title="Claim history"
            icon={<History className="h-4 w-4 text-blue-600" />}
            isLoading={claimsQ.isLoading}
            isError={claimsQ.isError}
          >
            {claimsRaw.length === 0 ? (
              <p className="text-sm text-gray-500">No claims found.</p>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left text-gray-600">
                        <th className="pb-2 pr-2">Claim ID</th>
                        <th className="pb-2 pr-2">Submitted</th>
                        <th className="pb-2 pr-2">Service</th>
                        <th className="pb-2 pr-2 text-right">Amount</th>
                        <th className="pb-2">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {claimsSlice.map((c, i) => {
                        const id = readStr(c, "id");
                        const st = readStr(c, "status") || "SUBMITTED";
                        const badge = CLAIM_STATUS[st] ?? "bg-gray-100 text-gray-700";
                        return (
                          <tr key={id || i}>
                            <td className="py-2 pr-2 font-mono text-xs">{id.slice(0, 8)}…</td>
                            <td className="py-2 pr-2 text-gray-600 whitespace-nowrap">
                              {readStr(c, "submittedAt", "submitted_at")
                                ? new Date(readStr(c, "submittedAt", "submitted_at")).toLocaleString()
                                : "—"}
                            </td>
                            <td className="py-2 pr-2">{readStr(c, "claimType", "claim_type") || "—"}</td>
                            <td className="py-2 pr-2 text-right">
                              {readNum(c, "totalAmount", "total_amount").toLocaleString()}
                            </td>
                            <td className="py-2">
                              <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${badge}`}>
                                {st}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                <div className="mt-3 flex items-center justify-between text-xs text-gray-600">
                  <button
                    type="button"
                    disabled={claimPage <= 0}
                    onClick={() => setClaimPage((p) => Math.max(0, p - 1))}
                    className="text-violet-700 disabled:opacity-40"
                  >
                    Previous
                  </button>
                  <span>
                    Page {claimPage + 1} · {claimsRaw.length} total
                  </span>
                  <button
                    type="button"
                    disabled={(claimPage + 1) * pageSize >= claimsRaw.length}
                    onClick={() => setClaimPage((p) => p + 1)}
                    className="text-violet-700 disabled:opacity-40"
                  >
                    Next
                  </button>
                </div>
              </>
            )}
          </Section>

          <Section
            title="Contributions"
            icon={<PiggyBank className="h-4 w-4 text-emerald-600" />}
            isLoading={contribQ.isLoading}
            isError={contribQ.isError}
          >
            {contributions.length === 0 ? (
              <p className="text-sm text-gray-500">No contribution records.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-gray-600">
                    <th className="pb-2 pr-2">Period</th>
                    <th className="pb-2 pr-2 text-right">Amount</th>
                    <th className="pb-2 pr-2 text-right">Paid</th>
                    <th className="pb-2">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {contributions.map((row, i) => {
                    const st = readStr(row, "status") || "DUE";
                    const badge = CONTRIB_STATUS[st] ?? "bg-gray-100 text-gray-700";
                    const ps = readStr(row, "periodStart", "period_start");
                    const pe = readStr(row, "periodEnd", "period_end");
                    return (
                      <tr key={readStr(row, "id") || i}>
                        <td className="py-2 pr-2 whitespace-nowrap">
                          {ps} → {pe}
                        </td>
                        <td className="py-2 pr-2 text-right">{readNum(row, "amount").toLocaleString()}</td>
                        <td className="py-2 pr-2 text-right text-gray-600">
                          {readStr(row, "paidAt", "paid_at")
                            ? new Date(readStr(row, "paidAt", "paid_at")).toLocaleDateString()
                            : "—"}
                        </td>
                        <td className="py-2">
                          <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${badge}`}>
                            {st}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </Section>

          <Section
            title="Preauthorizations"
            icon={<Stethoscope className="h-4 w-4 text-teal-600" />}
            isLoading={preauthQ.isLoading}
            isError={preauthQ.isError}
          >
            {preauths.length === 0 ? (
              <p className="text-sm text-gray-500">No preauthorizations.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-gray-600">
                    <th className="pb-2 pr-2">Procedure</th>
                    <th className="pb-2 pr-2">Status</th>
                    <th className="pb-2 pr-2 text-right">Approved qty</th>
                    <th className="pb-2 text-right">Used qty</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {preauths.map((row, i) => {
                    const st = readStr(row, "status") || "PENDING";
                    const badge = PREAUTH_STATUS[st] ?? "bg-gray-100 text-gray-700";
                    return (
                      <tr key={readStr(row, "id") || i}>
                        <td className="py-2 pr-2">{readStr(row, "requestType", "request_type") || "—"}</td>
                        <td className="py-2 pr-2">
                          <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${badge}`}>
                            {st}
                          </span>
                        </td>
                        <td className="py-2 pr-2 text-right">
                          {readNum(row, "quantityApproved", "quantity_approved") || "—"}
                        </td>
                        <td className="py-2 text-right">
                          {readNum(row, "quantityConsumed", "quantity_consumed") || "—"}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </Section>

          <Section
            title="Benefit Utilization"
            icon={<BarChart3 className="h-4 w-4 text-indigo-600" />}
            isLoading={utilizationQ.isLoading}
            isError={utilizationQ.isError}
          >
            {!utilizationQ.isLoading && !utilizationQ.isError && utilizationRows.length === 0 ? (
              <p className="text-sm text-gray-500">No benefit utilization tracked yet</p>
            ) : null}
            {!utilizationQ.isLoading && !utilizationQ.isError && utilizationRows.length > 0 ? (
              <div className="grid gap-4 md:grid-cols-2">
                {utilizationRows.map((row, i) => {
                  const benefit = readStr(row, "benefitCode", "benefit_code");
                  const plan = readStr(row, "planCode", "plan_code");
                  const limit = readNum(row, "limitAmount", "limit_amount");
                  const used = readNum(row, "usedAmount", "used_amount");
                  const ps = readStr(row, "periodStart", "period_start");
                  const pe = readStr(row, "periodEnd", "period_end");
                  const period =
                    ps && pe ? `${formatMonthYear(ps)} - ${formatMonthYear(pe)}` : ps || pe || "—";
                  const remaining = limit > 0 ? Math.max(0, limit - used) : null;
                  const pct = utilizationPct(used, limit);
                  const barClass = pct === null ? "bg-gray-300" : utilizationBarTone(pct);
                  const width = pct === null ? 0 : pct;
                  return (
                    <div
                      key={readStr(row, "id") || `${benefit}-${i}`}
                      className="rounded-lg border border-gray-100 bg-gray-50/80 p-4 space-y-3"
                    >
                      <div>
                        <p className="text-sm font-semibold text-gray-900">{benefit || "Benefit"}</p>
                        {plan ? <p className="text-xs text-gray-500 font-mono mt-0.5">{plan}</p> : null}
                        <p className="text-xs text-gray-600 mt-1">{period}</p>
                      </div>
                      <dl className="grid grid-cols-3 gap-2 text-xs">
                        <div>
                          <dt className="text-gray-500">Annual limit</dt>
                          <dd className="font-medium text-gray-900">{limit > 0 ? limit.toLocaleString() : "—"}</dd>
                        </div>
                        <div>
                          <dt className="text-gray-500">Used</dt>
                          <dd className="font-medium text-gray-900">{used.toLocaleString()}</dd>
                        </div>
                        <div>
                          <dt className="text-gray-500">Remaining</dt>
                          <dd className="font-medium text-gray-900">
                            {remaining === null ? "—" : remaining.toLocaleString()}
                          </dd>
                        </div>
                      </dl>
                      <div>
                        <div className="h-2 w-full rounded-full bg-gray-200 overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all ${barClass}`}
                            style={{ width: `${width}%` }}
                          />
                        </div>
                        {pct !== null ? (
                          <p className="text-[10px] text-gray-500 mt-1">{pct.toFixed(0)}% of annual limit used</p>
                        ) : (
                          <p className="text-[10px] text-gray-500 mt-1">No annual limit set for this benefit</p>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : null}
          </Section>

          <Section
            title="Appeals"
            icon={<Gavel className="h-4 w-4 text-slate-700" />}
            isLoading={appealsQ.isLoading}
            isError={appealsQ.isError}
          >
            <div className="flex justify-end mb-3">
              <button
                type="button"
                onClick={() => setAppealOpen(!appealOpen)}
                className="inline-flex items-center gap-2 rounded-lg border border-violet-300 bg-violet-50 px-3 py-1.5 text-sm font-medium text-violet-800 hover:bg-violet-100"
              >
                <ClipboardList className="h-4 w-4" />
                {appealOpen ? "Cancel" : "Submit appeal"}
              </button>
            </div>
            {appealOpen && (
              <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-4 space-y-2">
                <input
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  placeholder="Claim ID *"
                  value={appealForm.claimId}
                  onChange={(e) => setAppealForm({ ...appealForm, claimId: e.target.value })}
                />
                <input
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  placeholder="Coverage UUID (optional)"
                  value={appealForm.coverageId}
                  onChange={(e) => setAppealForm({ ...appealForm, coverageId: e.target.value })}
                />
                <textarea
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  rows={3}
                  placeholder="Reason *"
                  value={appealForm.reason}
                  onChange={(e) => setAppealForm({ ...appealForm, reason: e.target.value })}
                />
                <button
                  type="button"
                  disabled={submitAppeal.isPending}
                  onClick={sendAppeal}
                  className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                >
                  {submitAppeal.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : null} Submit
                </button>
                {submitAppeal.isError && (
                  <p className="text-xs text-red-600">Could not submit appeal.</p>
                )}
              </div>
            )}
            {appeals.length === 0 ? (
              <p className="text-sm text-gray-500">No appeals filed.</p>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-gray-600">
                    <th className="pb-2 pr-2">Claim</th>
                    <th className="pb-2 pr-2">Reason</th>
                    <th className="pb-2 pr-2">Status</th>
                    <th className="pb-2">Decision</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {appeals.map((row, i) => {
                    const st = readStr(row, "status") || "PENDING";
                    const badge = APPEAL_STATUS[st] ?? "bg-gray-100 text-gray-700";
                    return (
                      <tr key={readStr(row, "id") || i}>
                        <td className="py-2 pr-2 font-mono text-xs">{readStr(row, "claimId", "claim_id")}</td>
                        <td className="py-2 pr-2 text-gray-700 max-w-xs truncate">
                          {readStr(row, "reason", "appealReason", "appeal_reason") || "—"}
                        </td>
                        <td className="py-2 pr-2">
                          <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${badge}`}>
                            {st}
                          </span>
                        </td>
                        <td className="py-2 text-gray-700">{readStr(row, "decision") || "—"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </Section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
