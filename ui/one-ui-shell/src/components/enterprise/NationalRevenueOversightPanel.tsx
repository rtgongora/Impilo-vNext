"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AlertTriangle, Loader2, Wallet } from "lucide-react";
import {
  useClaimsExpenditure,
  useDebtAging,
  useRevenueSummary,
} from "@/hooks/queries/useFinancialReports";
import { extractCount, useMushexPlatformRemittanceTransfers } from "@/hooks/queries/useMushexPlatformAdmin";

const money = (n: unknown) => {
  const v = typeof n === "number" ? n : Number(n);
  if (Number.isNaN(v)) return "—";
  return new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v);
};

function asList(v: unknown): Record<string, unknown>[] {
  if (!Array.isArray(v)) return [];
  return v.filter((x) => x && typeof x === "object") as Record<string, unknown>[];
}

function errorMessage(err: unknown): string {
  if (err && typeof err === "object" && "error" in err) {
    return String((err as { error?: { message?: string } }).error?.message ?? "Request failed");
  }
  return "Request failed";
}

export function NationalRevenueOversightPanel() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  const revenueQ = useRevenueSummary(undefined, year, month);
  const claimsQ = useClaimsExpenditure(undefined, year, month);
  const remittancesQ = useMushexPlatformRemittanceTransfers();
  const debtQ = useDebtAging(undefined);

  const revenue = revenueQ.data;
  const byPayer = useMemo(() => asList(revenue?.byPayerType), [revenue]);
  const aging = useMemo(() => asList(debtQ.data?.agingBuckets), [debtQ.data]);
  const totalOutstanding = useMemo(() => {
    let sum = 0;
    let hasValue = false;
    for (const row of aging) {
      const v = Number(row.outstanding);
      if (!Number.isNaN(v)) {
        sum += v;
        hasValue = true;
      }
    }
    return hasValue ? sum : null;
  }, [aging]);
  const settlementCount = extractCount(remittancesQ.data);

  const anyLoading = revenueQ.isLoading || claimsQ.isLoading || remittancesQ.isLoading || debtQ.isLoading;

  return (
    <section
      className="rounded-2xl border border-violet-200 bg-white p-5 shadow-sm"
      data-testid="national-revenue-oversight-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <Wallet className="mt-0.5 h-5 w-5 text-violet-600" aria-hidden />
          <div>
            <h2 className="text-sm font-semibold text-slate-900">National revenue oversight</h2>
            <p className="mt-1 text-xs text-slate-500">
              Costa national aggregate, claims exposure, MusheX settlement rows, and receivables — no synthetic KPIs.
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-end gap-3">
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
        </div>
      </div>

      {anyLoading ? (
        <p className="mt-4 flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading national finance signals…
        </p>
      ) : null}

      <div className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-slate-50/80 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-500">Net revenue (national)</p>
          {revenueQ.isError ? (
            <p className="mt-2 flex items-start gap-1.5 text-sm text-red-700">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              {errorMessage(revenueQ.error)}
            </p>
          ) : revenue ? (
            <>
              <p className="mt-1 text-2xl font-semibold text-slate-900">{money(revenue.totalRevenue)}</p>
              <p className="mt-1 text-xs text-slate-600">{String(revenue.encounterCount ?? 0)} encounters</p>
              {byPayer.length > 0 ? (
                <p className="mt-1 text-xs text-violet-700">{byPayer.length} payer type buckets</p>
              ) : (
                <p className="mt-1 text-xs text-slate-500">No payer breakdown for period.</p>
              )}
            </>
          ) : (
            <p className="mt-2 text-sm text-slate-500">No revenue data for this period.</p>
          )}
        </div>

        <div className="rounded-xl border border-slate-200 bg-slate-50/80 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-500">Claims exposure</p>
          {claimsQ.isError ? (
            <p className="mt-2 flex items-start gap-1.5 text-sm text-red-700">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              {errorMessage(claimsQ.error)}
            </p>
          ) : claimsQ.data ? (
            <>
              <p className="mt-1 text-2xl font-semibold text-slate-900">{money(claimsQ.data.claimsExposure)}</p>
              <p className="mt-1 text-xs text-slate-600">
                Loss ratio:{" "}
                {claimsQ.data.lossRatio != null
                  ? `${(Number(claimsQ.data.lossRatio) * 100).toFixed(2)}%`
                  : "N/A"}
              </p>
            </>
          ) : (
            <p className="mt-2 text-sm text-slate-500">No claims expenditure for this period.</p>
          )}
        </div>

        <div className="rounded-xl border border-slate-200 bg-slate-50/80 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-500">MusheX remittance rows</p>
          {remittancesQ.isError ? (
            <p className="mt-2 flex items-start gap-1.5 text-sm text-red-700">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              {errorMessage(remittancesQ.error)}
            </p>
          ) : settlementCount != null ? (
            <p className="mt-1 text-2xl font-semibold text-slate-900">{String(settlementCount)}</p>
          ) : (
            <p className="mt-2 text-sm text-slate-500">Remittance response shape not recognised.</p>
          )}
          <p className="mt-1 text-xs text-slate-500">Settlement-adjacent transfer rows from mushex-platform BFF.</p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-slate-50/80 p-4">
          <p className="text-xs uppercase tracking-wide text-slate-500">National receivables</p>
          {debtQ.isError ? (
            <p className="mt-2 flex items-start gap-1.5 text-sm text-red-700">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              {errorMessage(debtQ.error)}
            </p>
          ) : debtQ.data ? (
            <>
              <p className="mt-1 text-2xl font-semibold text-slate-900">
                {totalOutstanding != null ? money(totalOutstanding) : "—"}
              </p>
              <p className="mt-1 text-xs text-slate-600">
                {aging.length > 0 ? `${aging.length} aging buckets` : "No aging buckets on record"}
                {debtQ.data.writeOffTotal != null ? ` · write-offs ${money(debtQ.data.writeOffTotal)}` : ""}
              </p>
            </>
          ) : (
            <p className="mt-2 text-sm text-slate-500">No receivables snapshot available.</p>
          )}
        </div>
      </div>

      {byPayer.length > 0 && !revenueQ.isError ? (
        <div className="mt-4 rounded-xl border border-slate-100 bg-slate-50/60 p-4">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600">Revenue by payer type</h3>
          <table className="mt-2 w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-500">
                <th className="pb-2">Payer type</th>
                <th className="pb-2 text-right">Net</th>
              </tr>
            </thead>
            <tbody>
              {byPayer.map((row) => (
                <tr key={String(row.bucket ?? row.payerType)} className="border-t border-slate-100">
                  <td className="py-2">{String(row.bucket ?? row.payerType ?? "—")}</td>
                  <td className="py-2 text-right font-medium">{money(row.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      <div className="mt-4 flex flex-wrap gap-3 text-xs">
        <Link href="/finance/reports" className="font-medium text-violet-700 hover:underline">
          Institutional financial reports
        </Link>
        <Link href="/finance/settlements" className="font-medium text-violet-700 hover:underline">
          Settlements
        </Link>
        <Link href="/coverage" className="font-medium text-violet-700 hover:underline">
          Coverage operations
        </Link>
      </div>
    </section>
  );
}
