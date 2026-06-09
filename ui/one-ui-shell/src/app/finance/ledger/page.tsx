"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { FINANCE_LEDGER_COLUMNS } from "@/lib/json-api/finance-table-columns";
import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2, ReceiptText } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFinanceLedger } from "@/hooks/queries/useFinanceLedger";

export default function FinanceLedgerPage() {
  const [intentId, setIntentId] = useState("");
  const [armed, setArmed] = useState(false);
  const ledgerQ = useFinanceLedger({ intentId: intentId.trim() || undefined }, armed);

  return (
    <AppLayout>
      <PageShell
        title="Ledger"
        subtitle="Inspect MusheX ledger detail through the Experience BFF instead of the finance sidecar."
        icon={<ReceiptText className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="max-w-3xl rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Ledger query</h2>
          <p className="mt-1 text-xs text-slate-500">
            GET <code className="text-[11px]">/internal/v1/finance/ledger?intentId=...</code>
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <input
              value={intentId}
              onChange={(event) => setIntentId(event.target.value)}
              className="min-w-[240px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
              placeholder="Payment intent id"
              aria-label="Ledger intent id"
            />
            <button
              type="button"
              className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
              onClick={() => setArmed(true)}
            >
              Load ledger
            </button>
          </div>
          {ledgerQ.isLoading ? (
            <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading ledger...
            </p>
          ) : ledgerQ.isError ? (
            <p className="mt-3 text-sm text-red-700">Could not load ledger for that intent.</p>
          ) : ledgerQ.data != null ? (
            <div className="mt-3">
              <JsonApiDataTable
                data={ledgerQ.data}
                columns={FINANCE_LEDGER_COLUMNS}
                isLoading={ledgerQ.isPending}
                error={ledgerQ.error as Error | null}
                emptyTitle="No ledger entries"
              />
            </div>
          ) : (
            <p className="mt-3 text-xs text-slate-500">Enter an intent id to inspect the ledger response.</p>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
