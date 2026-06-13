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
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to finance
          </Link>
        </div>

        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Data source: <code className="text-xs">GET /internal/v1/coverage/remittances</code>.
          </p>

          {remittancesQ.isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : remittancesQ.isError ? (
            <div className="rounded-lg border border-danger/28 bg-danger-soft p-4 text-sm text-danger">
              Could not load remittance rows.
            </div>
          ) : remittances.length === 0 ? (
            <div className="rounded-lg border border-border bg-card p-10 text-center">
              <p className="text-sm text-muted-foreground">No remittance rows returned for the current tenant scope.</p>
            </div>
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border bg-card">
              <table className="w-full text-sm">
                <thead className="bg-background text-left">
                  <tr>
                    <th className="px-3 py-2 font-medium text-foreground">Remittance</th>
                    <th className="px-3 py-2 font-medium text-foreground">Coverage</th>
                    <th className="px-3 py-2 font-medium text-foreground">Amount</th>
                    <th className="px-3 py-2 font-medium text-foreground">Status</th>
                    <th className="px-3 py-2 font-medium text-foreground">Remitted</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {remittances.map((row) => (
                    <tr key={row.id}>
                      <td className="px-3 py-3 font-mono text-xs text-foreground">{row.remittanceNumber || row.id}</td>
                      <td className="px-3 py-3 text-foreground">{row.coverageId || "—"}</td>
                      <td className="px-3 py-3 text-foreground">{formatAmount(row.amount, row.currency || "USD")}</td>
                      <td className="px-3 py-3">
                        <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-foreground">
                          {row.status || "UNKNOWN"}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-xs text-muted-foreground">{formatDate(row.remittedAt)}</td>
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
