"use client";

/**
 * Institutional financial reporting — revenue, receivables, budgets, payer claims expenditure.
 * Route: /finance/reports
 */

import { useMemo, useState } from "react";
import {
  AlertTriangle,
  BarChart3,
  CreditCard,
  Loader2,
  PiggyBank,
  Receipt,
  RefreshCw,
  Shield,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useBudgetAllocations,
  useBudgetVsActual,
  useClaimsExpenditure,
  useClosePeriod,
  useCreateBudget,
  useDebtAging,
  useProviderPayments,
  useRecordBudgetSpend,
  useRevenueSummary,
} from "@/hooks/queries/useFinancialReports";

type TabId = "revenue" | "receivables" | "budget" | "claims";

const money = (n: unknown) => {
  const v = typeof n === "number" ? n : Number(n);
  if (Number.isNaN(v)) return "—";
  return new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v);
};

const pct = (n: unknown) => {
  const v = typeof n === "number" ? n : Number(n);
  if (Number.isNaN(v)) return "—";
  return `${v.toFixed(1)}%`;
};

function asList(v: unknown): Record<string, unknown>[] {
  if (!Array.isArray(v)) return [];
  return v.filter((x) => x && typeof x === "object") as Record<string, unknown>[];
}

function bucketColor(bucket: string): string {
  const b = (bucket || "").toUpperCase();
  if (b.includes("120")) return "bg-rose-600";
  if (b.includes("90")) return "bg-red-500";
  if (b.includes("60")) return "bg-orange-500";
  if (b.includes("30")) return "bg-amber-400";
  return "bg-emerald-500";
}

export default function FinanceReportsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id;

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [tab, setTab] = useState<TabId>("revenue");
  const [payerId, setPayerId] = useState("");
  const [budgetCategory, setBudgetCategory] = useState("OPERATING");
  const [budgetAmount, setBudgetAmount] = useState("");
  const [budgetDept, setBudgetDept] = useState("");
  const [spendId, setSpendId] = useState("");
  const [spendAmount, setSpendAmount] = useState("");
  const [closeType, setCloseType] = useState("MONTHLY");

  const revenueQ = useRevenueSummary(facilityId, year, month);
  const debtQ = useDebtAging(facilityId);
  const budgetVaQ = useBudgetVsActual(facilityId, year);
  const budgetsListQ = useBudgetAllocations(facilityId, year);
  const claimsQ = useClaimsExpenditure(payerId.trim() || undefined, year, month);
  const providerQ = useProviderPayments(facilityId, year, month);

  const createBudgetM = useCreateBudget();
  const spendM = useRecordBudgetSpend();
  const closeM = useClosePeriod();

  const revenue = revenueQ.data;
  const byPayer = useMemo(() => asList(revenue?.byPayerType), [revenue]);
  const byDept = useMemo(() => asList(revenue?.byDepartment), [revenue]);
  const trend = useMemo(() => asList(revenue?.monthlyTrend), [revenue]);
  const trendMax = useMemo(() => {
    let m = 0;
    for (const row of trend) {
      const t = Number(row.total);
      if (!Number.isNaN(t)) m = Math.max(m, t);
    }
    return m || 1;
  }, [trend]);

  const debt = debtQ.data;
  const aging = useMemo(() => asList(debt?.agingBuckets), [debt]);
  const debtors = useMemo(() => asList(debt?.topDebtors), [debt]);

  const bvaRows = useMemo(() => asList(budgetVaQ.data?.rows), [budgetVaQ.data]);
  const claimsByStatus = useMemo(() => asList(claimsQ.data?.claimsByStatus), [claimsQ.data]);
  const payTypes = useMemo(() => asList(providerQ.data?.byPaymentType), [providerQ.data]);

  const errorBanner = (err: unknown) => {
    const msg =
      err && typeof err === "object" && "error" in err
        ? String((err as { error?: { message?: string } }).error?.message ?? "Request failed")
        : "Request failed";
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800 flex items-center gap-2">
        <AlertTriangle className="h-4 w-4 shrink-0" />
        {msg}
      </div>
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Institutional financial reports"
        subtitle="Consolidated revenue, receivables, budgets, and payer-facing claims expenditure"
      >
        <OrganizationPlaneContextBar />
        <div className="space-y-6">
          <div className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4">
            <label className="text-xs font-medium text-slate-600">
              Year
              <input
                type="number"
                className="mt-1 block w-24 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                value={year}
                onChange={(e) => setYear(Number(e.target.value))}
              />
            </label>
            <label className="text-xs font-medium text-slate-600">
              Month
              <input
                type="number"
                min={1}
                max={12}
                className="mt-1 block w-20 rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                value={month}
                onChange={(e) => setMonth(Number(e.target.value))}
              />
            </label>
            <div className="text-xs text-slate-500">
              Facility:{" "}
              <span className="font-medium text-slate-800">{facility?.name ?? "None selected (tenant-wide where supported)"}</span>
            </div>
          </div>

          <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-2">
            {(
              [
                ["revenue", "Revenue", Receipt],
                ["receivables", "Receivables", CreditCard],
                ["budget", "Budget", PiggyBank],
                ["claims", "Claims expenditure", Shield],
              ] as const
            ).map(([id, label, Icon]) => (
              <button
                key={id}
                type="button"
                onClick={() => setTab(id)}
                className={`inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  tab === id ? "bg-indigo-100 text-indigo-900" : "text-slate-600 hover:bg-slate-100"
                }`}
              >
                <Icon className="h-4 w-4" />
                {label}
              </button>
            ))}
          </div>

          {tab === "revenue" && (
            <div className="space-y-6">
              {revenueQ.isLoading && (
                <div className="flex items-center gap-2 text-sm text-slate-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading revenue summary…
                </div>
              )}
              {revenueQ.isError && errorBanner(revenueQ.error)}
              {!revenueQ.isLoading && !revenueQ.data && !revenueQ.isError && (
                <p className="text-sm text-slate-500">No revenue data for this period.</p>
              )}
              {revenue && (
                <>
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    <div className="rounded-xl border border-slate-200 bg-white p-4">
                      <p className="text-xs uppercase tracking-wide text-slate-500">Total net revenue</p>
                      <p className="mt-1 text-2xl font-semibold text-slate-900">{money(revenue.totalRevenue)}</p>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-white p-4">
                      <p className="text-xs uppercase tracking-wide text-slate-500">Encounters</p>
                      <p className="mt-1 text-2xl font-semibold text-slate-900">{String(revenue.encounterCount ?? 0)}</p>
                    </div>
                    <div className="rounded-xl border border-indigo-100 bg-indigo-50/60 p-4">
                      <p className="text-xs uppercase tracking-wide text-indigo-800">Period close</p>
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <select
                          className="rounded-md border border-indigo-200 bg-white px-2 py-1 text-xs"
                          value={closeType}
                          onChange={(e) => setCloseType(e.target.value)}
                        >
                          <option value="MONTHLY">MONTHLY</option>
                          <option value="QUARTERLY">QUARTERLY</option>
                        </select>
                        <button
                          type="button"
                          disabled={closeM.isPending}
                          onClick={() => closeM.mutate({ periodType: closeType, year, month })}
                          className="inline-flex items-center gap-1 rounded-md bg-indigo-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-800 disabled:opacity-50"
                        >
                          {closeM.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <RefreshCw className="h-3 w-3" />}
                          Close period
                        </button>
                      </div>
                      {closeM.isError && <p className="mt-2 text-xs text-red-600">Close failed — check roles and upstream COSTA.</p>}
                      {closeM.isSuccess && <p className="mt-2 text-xs text-emerald-700">Period close accepted.</p>}
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 bg-white p-4">
                    <div className="mb-3 flex items-center gap-2">
                      <BarChart3 className="h-4 w-4 text-slate-500" />
                      <h3 className="text-sm font-semibold text-slate-900">Monthly revenue trend ({year})</h3>
                    </div>
                    {trend.length === 0 ? (
                      <p className="text-sm text-slate-500">No trend rows for this facility/year.</p>
                    ) : (
                      <div className="space-y-2">
                        {trend.map((row) => {
                          const total = Number(row.total);
                          const w = Math.round((Number.isNaN(total) ? 0 : total / trendMax) * 100);
                          return (
                            <div key={String(row.month)} className="flex items-center gap-3 text-sm">
                              <span className="w-10 text-slate-500">M{String(row.month)}</span>
                              <div className="h-3 flex-1 rounded-full bg-slate-100">
                                <div
                                  className="h-3 rounded-full bg-indigo-500"
                                  style={{ width: `${Math.max(w, 2)}%` }}
                                  title={money(row.total)}
                                />
                              </div>
                              <span className="w-24 text-right text-slate-700">{money(row.total)}</span>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>

                  <div className="grid gap-4 lg:grid-cols-2">
                    <div className="rounded-xl border border-slate-200 bg-white p-4">
                      <h3 className="text-sm font-semibold text-slate-900">By payer type</h3>
                      {byPayer.length === 0 ? (
                        <p className="mt-2 text-sm text-slate-500">No payer-type breakdown.</p>
                      ) : (
                        <table className="mt-3 w-full text-sm">
                          <thead>
                            <tr className="text-left text-xs text-slate-500">
                              <th className="pb-2">Type</th>
                              <th className="pb-2 text-right">Net</th>
                            </tr>
                          </thead>
                          <tbody>
                            {byPayer.map((row) => (
                              <tr key={String(row.bucket)} className="border-t border-slate-100">
                                <td className="py-2">{String(row.bucket)}</td>
                                <td className="py-2 text-right font-medium">{money(row.total)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      )}
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-white p-4">
                      <h3 className="text-sm font-semibold text-slate-900">By department</h3>
                      {byDept.length === 0 ? (
                        <p className="mt-2 text-sm text-slate-500">No department breakdown.</p>
                      ) : (
                        <table className="mt-3 w-full text-sm">
                          <thead>
                            <tr className="text-left text-xs text-slate-500">
                              <th className="pb-2">Department</th>
                              <th className="pb-2 text-right">Net</th>
                            </tr>
                          </thead>
                          <tbody>
                            {byDept.map((row) => (
                              <tr key={String(row.bucket)} className="border-t border-slate-100">
                                <td className="py-2">{String(row.bucket)}</td>
                                <td className="py-2 text-right font-medium">{money(row.total)}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      )}
                    </div>
                  </div>
                </>
              )}
            </div>
          )}

          {tab === "receivables" && (
            <div className="space-y-6">
              {debtQ.isLoading && (
                <div className="flex items-center gap-2 text-sm text-slate-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading receivables…
                </div>
              )}
              {debtQ.isError && errorBanner(debtQ.error)}
              {debt && (
                <>
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                    {aging.length === 0 ? (
                      <p className="text-sm text-slate-500 col-span-full">No open receivables buckets.</p>
                    ) : (
                      aging.map((row) => (
                        <div
                          key={String(row.bucket)}
                          className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm"
                        >
                          <div className={`mb-2 h-1.5 rounded-full ${bucketColor(String(row.bucket))}`} />
                          <p className="text-xs font-medium uppercase text-slate-500">{String(row.bucket)}</p>
                          <p className="mt-1 text-lg font-semibold text-slate-900">{money(row.outstanding)}</p>
                          <p className="text-xs text-slate-500">{String(row.rowCount ?? 0)} lines</p>
                        </div>
                      ))
                    )}
                  </div>
                  <div className="rounded-xl border border-slate-200 bg-white p-4">
                    <h3 className="text-sm font-semibold text-slate-900">Write-off total (linked receivables)</h3>
                    <p className="mt-2 text-2xl font-semibold text-rose-700">{money(debt.writeOffTotal)}</p>
                  </div>
                  <div className="rounded-xl border border-slate-200 bg-white p-4">
                    <h3 className="text-sm font-semibold text-slate-900">Top debtors</h3>
                    {debtors.length === 0 ? (
                      <p className="mt-2 text-sm text-slate-500">No debtor rows.</p>
                    ) : (
                      <table className="mt-3 w-full text-sm">
                        <thead>
                          <tr className="text-left text-xs text-slate-500">
                            <th className="pb-2">Name</th>
                            <th className="pb-2">Ref</th>
                            <th className="pb-2 text-right">Outstanding</th>
                          </tr>
                        </thead>
                        <tbody>
                          {debtors.map((row, i) => (
                            <tr key={`${String(row.debtorRef)}-${i}`} className="border-t border-slate-100">
                              <td className="py-2">{String(row.debtorName ?? "—")}</td>
                              <td className="py-2 text-slate-600">{String(row.debtorRef ?? "")}</td>
                              <td className="py-2 text-right font-medium">{money(row.outstanding)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                </>
              )}
            </div>
          )}

          {tab === "budget" && (
            <div className="space-y-6">
              {!facilityId && (
                <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
                  Select a facility in context to load budget vs actual and allocations.
                </p>
              )}
              {budgetVaQ.isLoading && (
                <div className="flex items-center gap-2 text-sm text-slate-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading budget vs actual…
                </div>
              )}
              {budgetVaQ.isError && errorBanner(budgetVaQ.error)}
              {budgetVaQ.data && (
                <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
                  <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left text-xs text-slate-600">
                      <tr>
                        <th className="px-3 py-2">Category</th>
                        <th className="px-3 py-2 text-right">Allocated</th>
                        <th className="px-3 py-2 text-right">Spent</th>
                        <th className="px-3 py-2 text-right">Committed</th>
                        <th className="px-3 py-2 text-right">Available</th>
                        <th className="px-3 py-2">% used</th>
                      </tr>
                    </thead>
                    <tbody>
                      {bvaRows.length === 0 ? (
                        <tr>
                          <td colSpan={6} className="px-3 py-6 text-center text-slate-500">
                            No budget allocations for this year. Create one below.
                          </td>
                        </tr>
                      ) : (
                        bvaRows.map((row) => {
                          const used = Number(row.percentUsed ?? 0);
                          return (
                            <tr key={String(row.allocationId)} className="border-t border-slate-100">
                              <td className="px-3 py-2 font-medium">{String(row.category)}</td>
                              <td className="px-3 py-2 text-right">{money(row.allocated)}</td>
                              <td className="px-3 py-2 text-right">{money(row.spent)}</td>
                              <td className="px-3 py-2 text-right">{money(row.committed)}</td>
                              <td className="px-3 py-2 text-right">{money(row.available)}</td>
                              <td className="px-3 py-2">
                                <div className="flex items-center gap-2">
                                  <div className="h-2 w-24 overflow-hidden rounded-full bg-slate-100">
                                    <div
                                      className="h-2 rounded-full bg-teal-500"
                                      style={{ width: `${Math.min(used, 100)}%` }}
                                    />
                                  </div>
                                  <span className="text-xs text-slate-600">{pct(row.percentUsed)}</span>
                                </div>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              )}

              <div className="grid gap-4 lg:grid-cols-2">
                <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
                  <h3 className="text-sm font-semibold text-slate-900">Create allocation</h3>
                  <input
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                    placeholder="Department id (optional)"
                    value={budgetDept}
                    onChange={(e) => setBudgetDept(e.target.value)}
                  />
                  <input
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                    placeholder="Category"
                    value={budgetCategory}
                    onChange={(e) => setBudgetCategory(e.target.value)}
                  />
                  <input
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                    placeholder="Allocated amount"
                    value={budgetAmount}
                    onChange={(e) => setBudgetAmount(e.target.value)}
                  />
                  <button
                    type="button"
                    disabled={!facilityId || createBudgetM.isPending}
                    onClick={() => {
                      if (!facilityId) return;
                      createBudgetM.mutate({
                        facilityId,
                        departmentId: budgetDept || undefined,
                        budgetCategory,
                        periodYear: year,
                        allocatedAmount: Number(budgetAmount),
                      });
                    }}
                    className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
                  >
                    {createBudgetM.isPending ? "Saving…" : "Create allocation"}
                  </button>
                  {createBudgetM.isError && errorBanner(createBudgetM.error)}
                  {createBudgetM.isSuccess && <p className="text-xs text-emerald-700">Allocation created.</p>}
                </div>
                <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
                  <h3 className="text-sm font-semibold text-slate-900">Record spend (by allocation id)</h3>
                  <p className="text-xs text-slate-500">Use numeric id from COSTA row or list below.</p>
                  <input
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                    placeholder="Allocation id (number)"
                    value={spendId}
                    onChange={(e) => setSpendId(e.target.value)}
                  />
                  <input
                    className="w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                    placeholder="Amount"
                    value={spendAmount}
                    onChange={(e) => setSpendAmount(e.target.value)}
                  />
                  <button
                    type="button"
                    disabled={!facilityId || spendM.isPending}
                    onClick={() => {
                      if (!facilityId) return;
                      spendM.mutate({
                        id: Number(spendId),
                        amount: Number(spendAmount),
                        facilityId,
                        year,
                      });
                    }}
                    className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-50"
                  >
                    {spendM.isPending ? "Recording…" : "Record spend"}
                  </button>
                  {spendM.isError && errorBanner(spendM.error)}
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-white p-4">
                <h3 className="text-sm font-semibold text-slate-900">Allocations ({year})</h3>
                {budgetsListQ.isLoading && (
                  <p className="mt-2 text-sm text-slate-500 flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                  </p>
                )}
                {budgetsListQ.isError && errorBanner(budgetsListQ.error)}
                {Array.isArray(budgetsListQ.data) && budgetsListQ.data.length === 0 && (
                  <p className="mt-2 text-sm text-slate-500">No rows.</p>
                )}
                {Array.isArray(budgetsListQ.data) && budgetsListQ.data.length > 0 && (
                  <ul className="mt-2 divide-y divide-slate-100 text-sm">
                    {budgetsListQ.data.map((row: unknown) => {
                      const r = row as Record<string, unknown>;
                      return (
                        <li key={String(r.id)} className="py-2 flex flex-wrap justify-between gap-2">
                          <span className="font-medium">{String(r.budgetCategory)}</span>
                          <span className="text-slate-600">
                            id {String(r.id)} · avail {money(r.availableAmount)}
                          </span>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            </div>
          )}

          {tab === "claims" && (
            <div className="space-y-6">
              <label className="block text-xs font-medium text-slate-600">
                Payer id (insurer ref) — optional filter
                <input
                  className="mt-1 block max-w-md rounded-md border border-slate-300 px-2 py-1.5 text-sm w-full"
                  value={payerId}
                  onChange={(e) => setPayerId(e.target.value)}
                  placeholder="UUID or insurer reference"
                />
              </label>
              {claimsQ.isLoading && (
                <div className="flex items-center gap-2 text-sm text-slate-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading claims expenditure…
                </div>
              )}
              {claimsQ.isError && errorBanner(claimsQ.error)}
              {claimsQ.data && (
                <div className="grid gap-4 lg:grid-cols-3">
                  <div className="rounded-xl border border-slate-200 bg-white p-4 lg:col-span-1">
                    <p className="text-xs uppercase text-slate-500">Loss ratio</p>
                    <p className="mt-1 text-2xl font-semibold text-slate-900">
                      {claimsQ.data.lossRatio != null
                        ? `${(Number(claimsQ.data.lossRatio) * 100).toFixed(2)}%`
                        : "N/A"}
                    </p>
                    <p className="mt-2 text-xs text-slate-500">
                      Claims exposure / premiums collected (premium rows use revenue_type containing PREMIUM).
                    </p>
                    <p className="mt-1 text-xs text-slate-600">Exposure: {money(claimsQ.data.claimsExposure)}</p>
                    <p className="text-xs text-slate-600">Premiums: {money(claimsQ.data.premiumsCollected)}</p>
                  </div>
                  <div className="rounded-xl border border-slate-200 bg-white p-4 lg:col-span-2">
                    <h3 className="text-sm font-semibold text-slate-900">Claims by status</h3>
                    {claimsByStatus.length === 0 ? (
                      <p className="mt-2 text-sm text-slate-500">No claim packs in period.</p>
                    ) : (
                      <table className="mt-3 w-full text-sm">
                        <thead>
                          <tr className="text-left text-xs text-slate-500">
                            <th className="pb-2">Status</th>
                            <th className="pb-2 text-right">Count</th>
                            <th className="pb-2 text-right">Insurer payable</th>
                          </tr>
                        </thead>
                        <tbody>
                          {claimsByStatus.map((row) => (
                            <tr key={String(row.status)} className="border-t border-slate-100">
                              <td className="py-2">{String(row.status)}</td>
                              <td className="py-2 text-right">{String(row.claimCount ?? 0)}</td>
                              <td className="py-2 text-right font-medium">{money(row.amount)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                </div>
              )}

              <div className="rounded-xl border border-slate-200 bg-white p-4">
                <h3 className="text-sm font-semibold text-slate-900">Provider payment summary</h3>
                <p className="text-xs text-slate-500 mt-1">
                  Using selected facility id as provider (facility) anchor for paid MusheX-linked payments in period.
                </p>
                {providerQ.isLoading && (
                  <p className="mt-2 text-sm text-slate-500 flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                  </p>
                )}
                {providerQ.isError && errorBanner(providerQ.error)}
                {providerQ.data && (
                  <>
                    <p className="mt-3 text-lg font-semibold">Total paid: {money(providerQ.data.totalPaid)}</p>
                    {payTypes.length === 0 ? (
                      <p className="mt-2 text-sm text-slate-500">No paid payments in period.</p>
                    ) : (
                      <table className="mt-3 w-full text-sm">
                        <thead>
                          <tr className="text-left text-xs text-slate-500">
                            <th className="pb-2">Payment type</th>
                            <th className="pb-2 text-right">Paid</th>
                            <th className="pb-2 text-right">Count</th>
                          </tr>
                        </thead>
                        <tbody>
                          {payTypes.map((row) => (
                            <tr key={String(row.paymentType)} className="border-t border-slate-100">
                              <td className="py-2">{String(row.paymentType)}</td>
                              <td className="py-2 text-right font-medium">{money(row.paid)}</td>
                              <td className="py-2 text-right">{String(row.paymentCount ?? 0)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </>
                )}
              </div>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
