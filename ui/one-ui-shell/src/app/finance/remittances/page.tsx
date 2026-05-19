"use client";

import Link from "next/link";
import { ArrowLeft, Loader2, Wallet } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCoverageRemittances } from "@/hooks/queries/useCoverage";

function formatDate(value?: string) {
  if (!value) return "Pending";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString();
}

function formatAmount(amount: number, currency: string) {
  return `${currency} ${amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export default function FinanceRemittancesPage() {
  const remittancesQ = useCoverageRemittances();
  const remittances = remittancesQ.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Remittances"
        subtitle="Read-only remittance hub over the canonical coverage remittance feed."
        icon={<Wallet className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Back to finance
          </Link>
        </div>

        <div className="space-y-4">
          <p className="text-sm text-slate-600">
            Data source: <code className="text-xs">GET /internal/v1/coverage/remittances</code>.
          </p>

          {remittancesQ.isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
            </div>
          ) : remittancesQ.isError ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              Could not load remittance rows.
            </div>
          ) : remittances.length === 0 ? (
            <div className="rounded-lg border border-slate-200 bg-white p-10 text-center">
              <p className="text-sm text-slate-500">No remittance rows returned for the current tenant scope.</p>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left">
                  <tr>
                    <th className="px-3 py-2 font-medium text-slate-700">Remittance</th>
                    <th className="px-3 py-2 font-medium text-slate-700">Coverage</th>
                    <th className="px-3 py-2 font-medium text-slate-700">Amount</th>
                    <th className="px-3 py-2 font-medium text-slate-700">Status</th>
                    <th className="px-3 py-2 font-medium text-slate-700">Remitted</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {remittances.map((row) => (
                    <tr key={row.id}>
                      <td className="px-3 py-3 font-mono text-xs text-slate-700">{row.remittanceNumber || row.id}</td>
                      <td className="px-3 py-3 text-slate-700">{row.coverageId || "—"}</td>
                      <td className="px-3 py-3 text-slate-700">{formatAmount(row.amount, row.currency || "USD")}</td>
                      <td className="px-3 py-3">
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                          {row.status || "UNKNOWN"}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-xs text-slate-500">{formatDate(row.remittedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
