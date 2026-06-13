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
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="max-w-3xl rounded-xl border border-border bg-card p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-foreground">Ledger query</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            GET <code className="text-[11px]">/internal/v1/finance/ledger?intentId=...</code>
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <input
              value={intentId}
              onChange={(event) => setIntentId(event.target.value)}
              className="min-w-[240px] flex-1 rounded-lg border border-border px-3 py-2 text-sm font-mono"
              placeholder="Payment intent id"
              aria-label="Ledger intent id"
            />
            <button
              type="button"
              className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
              onClick={() => setArmed(true)}
            >
              Load ledger
            </button>
          </div>
          {ledgerQ.isLoading ? (
            <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading ledger...
            </p>
          ) : ledgerQ.isError ? (
            <p className="mt-3 text-sm text-danger">Could not load ledger for that intent.</p>
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
            <p className="mt-3 text-xs text-muted-foreground">Enter an intent id to inspect the ledger response.</p>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
