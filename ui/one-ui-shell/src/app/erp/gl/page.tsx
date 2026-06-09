"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
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
          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Accounts</h2>
            <JsonApiDataTable
              data={accounts.data}
              columns={[
                { key: "code", header: "Code", fields: ["accountCode", "code"] },
                { key: "name", header: "Name", fields: ["name", "accountName"] },
                { key: "type", header: "Type", fields: ["accountType", "type"] },
                { key: "balance", header: "Balance", fields: ["balance", "currentBalance"] },
              ]}
              isLoading={accounts.isPending}
              error={accounts.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Journals</h2>
            <JsonApiDataTable
              data={journals.data}
              columns={[
                { key: "journal", header: "Journal", fields: ["journalId", "id"] },
                { key: "date", header: "Date", fields: ["postedAt", "date", "journalDate"] },
                { key: "memo", header: "Memo", fields: ["memo", "description"] },
                { key: "status", header: "Status", fields: ["status"] },
              ]}
              isLoading={journals.isPending}
              error={journals.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Trial balance</h2>
            <JsonApiDataTable
              data={trial.data}
              columns={[
                { key: "account", header: "Account", fields: ["accountCode", "accountId", "code"] },
                { key: "debit", header: "Debit", fields: ["debit", "debitTotal"] },
                { key: "credit", header: "Credit", fields: ["credit", "creditTotal"] },
              ]}
              isLoading={trial.isPending}
              error={trial.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Income statement</h2>
            <JsonApiDataTable
              data={income.data}
              columns={[
                { key: "line", header: "Line", fields: ["lineItem", "label", "name"] },
                { key: "amount", header: "Amount", fields: ["amount", "value"] },
                { key: "category", header: "Category", fields: ["category", "section"] },
              ]}
              isLoading={income.isPending}
              error={income.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Balance sheet</h2>
            <JsonApiDataTable
              data={balance.data}
              columns={[
                { key: "line", header: "Line", fields: ["lineItem", "label", "name"] },
                { key: "amount", header: "Amount", fields: ["amount", "value"] },
                { key: "section", header: "Section", fields: ["section", "category"] },
              ]}
              isLoading={balance.isPending}
              error={balance.error as Error | null}
            />
          </section>
          <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Budgets ({fy})</h2>
            <JsonApiDataTable
              data={budgets.data}
              columns={[
                { key: "account", header: "Account", fields: ["accountCode", "accountId"] },
                { key: "budget", header: "Budget", fields: ["budgetAmount", "amount"] },
                { key: "actual", header: "Actual", fields: ["actualAmount", "actual"] },
                { key: "variance", header: "Variance", fields: ["variance"] },
              ]}
              isLoading={budgets.isPending}
              error={budgets.error as Error | null}
            />
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
