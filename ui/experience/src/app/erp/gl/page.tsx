"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useGlAccounts,
  useGlBalanceSheet,
  useGlBudgets,
  useGlIncomeStatement,
  useGlJournals,
  useGlOpenPeriods,
  useGlSeedAccounts,
  useGlTrialBalance,
} from "@/hooks/queries/useGeneralLedger";

type PeriodRow = { periodId?: string };

export default function ErpGlPage() {
  const { data: periodsRaw, isLoading: periodsLoading } = useGlOpenPeriods();
  const periods = periodsRaw as PeriodRow[] | undefined;
  const defaultPeriod = useMemo(() => {
    if (!Array.isArray(periods) || periods.length === 0) return "";
    const id = periods[0].periodId;
    return id ?? "";
  }, [periods]);
  const [periodId, setPeriodId] = useState("");
  const effectivePeriod = periodId || defaultPeriod;
  const fy = new Date().getFullYear();

  const accounts = useGlAccounts();
  const journals = useGlJournals(effectivePeriod || null);
  const trial = useGlTrialBalance(effectivePeriod || null);
  const income = useGlIncomeStatement(effectivePeriod || null);
  const balance = useGlBalanceSheet(effectivePeriod || null);
  const budgets = useGlBudgets(fy);
  const seed = useGlSeedAccounts();

  return (
    <AppLayout>
      <PageShell title="General ledger" subtitle="Chart, periods, journals, and statements via BFF">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link href="/erp" className="text-sm text-impilo-500 hover:underline">
            ← ERP hub
          </Link>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            Period
            <select
              className="rounded border border-slate-300 px-2 py-1 text-sm"
              value={effectivePeriod}
              onChange={(e) => setPeriodId(e.target.value)}
            >
              {!periodsLoading &&
                Array.isArray(periods) &&
                periods.map((p) => (
                  <option key={p.periodId} value={p.periodId ?? ""}>
                    {p.periodId}
                  </option>
                ))}
            </select>
          </label>
          <button
            type="button"
            className="rounded bg-slate-800 px-3 py-1 text-sm text-white disabled:opacity-50"
            disabled={seed.isPending}
            onClick={() => seed.mutate()}
          >
            Seed default chart
          </button>
        </div>

        <div className="space-y-6">
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Accounts</h3>
            <pre className="mt-2 max-h-48 overflow-auto text-xs">
              {JSON.stringify(accounts.data ?? accounts.error ?? accounts.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Journals</h3>
            <pre className="mt-2 max-h-48 overflow-auto text-xs">
              {JSON.stringify(journals.data ?? journals.error ?? journals.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Trial balance</h3>
            <pre className="mt-2 max-h-48 overflow-auto text-xs">
              {JSON.stringify(trial.data ?? trial.error ?? trial.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Income statement</h3>
            <pre className="mt-2 max-h-48 overflow-auto text-xs">
              {JSON.stringify(income.data ?? income.error ?? income.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Balance sheet</h3>
            <pre className="mt-2 max-h-48 overflow-auto text-xs">
              {JSON.stringify(balance.data ?? balance.error ?? balance.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Budgets ({fy})</h3>
            <pre className="mt-2 max-h-48 overflow-auto text-xs">
              {JSON.stringify(budgets.data ?? budgets.error ?? budgets.isLoading, null, 2)}
            </pre>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
