"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
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
          <QueryResultPanel title="Accounts" isPending={accounts.isPending} isLoading={accounts.isPending} isError={accounts.isError} error={accounts.error} data={accounts.data} />
          <QueryResultPanel title="Journals" isPending={journals.isPending} isLoading={journals.isPending} isError={journals.isError} error={journals.error} data={journals.data} />
          <QueryResultPanel title="Trial balance" isPending={trial.isPending} isLoading={trial.isPending} isError={trial.isError} error={trial.error} data={trial.data} />
          <QueryResultPanel title="Income statement" isPending={income.isPending} isLoading={income.isPending} isError={income.isError} error={income.error} data={income.data} />
          <QueryResultPanel title="Balance sheet" isPending={balance.isPending} isLoading={balance.isPending} isError={balance.isError} error={balance.error} data={balance.data} />
          <QueryResultPanel title={`Budgets (${fy})`} isPending={budgets.isPending} isLoading={budgets.isPending} isError={budgets.isError} error={budgets.error} data={budgets.data} />
        </div>
      </PageShell>
    </AppLayout>
  );
}
