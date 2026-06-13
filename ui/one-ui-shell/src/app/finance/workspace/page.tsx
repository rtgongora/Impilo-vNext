"use client";
import {
  FinanceChargesTable,
  FinanceInvoicePanel,
  FinanceWalletsTable,
} from "@/components/finance/FinanceWorkspacePanels";
import Link from "next/link";
import { useState } from "react";
import { AlertCircle, ArrowLeft, Building2, FileSpreadsheet, HandCoins, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFinanceBillingCharges,
  useFinanceBillingInvoice,
  useFinanceBillingSubsidiesForEncounter,
} from "@/hooks/queries/useFinanceBillingWorkspace";
import { useFinanceMushexWallets } from "@/hooks/queries/useFinanceMushexPlatform";

function extractSubsidyRows(payload: unknown): Record<string, unknown>[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    if (Array.isArray(obj.data)) return obj.data as Record<string, unknown>[];
  }
  return [];
}

export default function FinanceWorkspacePage() {
  const [invoiceId, setInvoiceId] = useState("");
  const [billId, setBillId] = useState("");
  const [encounterId, setEncounterId] = useState("");
  const [ownerRef, setOwnerRef] = useState("");
  const [invoiceArmed, setInvoiceArmed] = useState(false);
  const [chargesArmed, setChargesArmed] = useState(false);
  const [subsidiesArmed, setSubsidiesArmed] = useState(false);
  const [walletsArmed, setWalletsArmed] = useState(false);

  const invQ = useFinanceBillingInvoice(invoiceId.trim(), invoiceArmed);
  const chQ = useFinanceBillingCharges(billId.trim(), chargesArmed);
  const subsidiesQ = useFinanceBillingSubsidiesForEncounter(
    encounterId.trim(),
    subsidiesArmed && Boolean(encounterId.trim()),
  );
  const wQ = useFinanceMushexWallets(ownerRef.trim(), walletsArmed);
  const subsidyRows = extractSubsidyRows(subsidiesQ.data);

  return (
    <AppLayout>
      <PageShell
        title="Finance workspace"
        subtitle="COSTA lifecycle and MusheX custodial platform through the Experience BFF enterprise plane (finance domain)."
        icon={<Building2 className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <FileSpreadsheet className="h-4 w-4" /> Billing workspace (COSTA)
            </h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET{" "}
              <code className="text-[11px]">/internal/v1/finance/billing-workspace/lifecycle/invoices/…</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                value={invoiceId}
                onChange={(e) => setInvoiceId(e.target.value)}
                className="min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm font-mono"
                placeholder="Invoice id"
                aria-label="Invoice id"
              />
              <button
                type="button"
                className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
                onClick={() => setInvoiceArmed(true)}
              >
                Load invoice
              </button>
            </div>
            {invQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : invQ.isError ? (
              <p className="mt-3 text-sm text-danger">Could not load invoice.</p>
            ) : invQ.data != null ? (
              <FinanceInvoicePanel
                data={invQ.data}
                isLoading={invQ.isPending}
                error={invQ.error as Error | null}
              />
            ) : (
              <p className="mt-3 text-xs text-muted-foreground">Load an invoice to inspect COSTA lifecycle JSON.</p>
            )}

            <p className="mt-6 text-xs text-muted-foreground">
              GET{" "}
              <code className="text-[11px]">/internal/v1/finance/billing-workspace/lifecycle/charges?billId=…</code>
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
              <input
                value={billId}
                onChange={(e) => setBillId(e.target.value)}
                className="min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm font-mono"
                placeholder="Bill id"
                aria-label="Bill id for charges"
              />
              <button
                type="button"
                className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
                onClick={() => setChargesArmed(true)}
              >
                Load charges
              </button>
            </div>
            {chQ.isLoading ? (
              <p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading charges…
              </p>
            ) : chQ.isError ? (
              <p className="mt-2 text-sm text-danger">Could not load charges.</p>
            ) : chQ.data != null ? (
              <div className="mt-2">
                <FinanceChargesTable
                  data={chQ.data}
                  isLoading={chQ.isPending}
                  error={chQ.error as Error | null}
                />
              </div>
            ) : null}

            <div className="mt-6 rounded-lg border border-amber-100 bg-warning-soft/50 p-4">
              <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <HandCoins className="h-4 w-4 text-warning-foreground" /> Subsidies by encounter
              </h3>
              <p className="mt-1 text-xs text-muted-foreground">
                GET{" "}
                <code className="text-[11px]">/internal/v1/finance/billing-workspace/lifecycle/subsidies?encounterId=…</code>
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <input
                  value={encounterId}
                  onChange={(e) => setEncounterId(e.target.value)}
                  className="min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm font-mono"
                  placeholder="Encounter id"
                  aria-label="Encounter id for subsidies"
                />
                <button
                  type="button"
                  className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
                  onClick={() => setSubsidiesArmed(true)}
                >
                  Load subsidies
                </button>
              </div>
              {subsidiesArmed && encounterId.trim() ? (
                subsidiesQ.isLoading ? (
                  <p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading subsidies…
                  </p>
                ) : subsidiesQ.isError ? (
                  <p className="mt-2 flex items-center gap-2 text-sm text-danger">
                    <AlertCircle className="h-4 w-4" /> Could not load subsidy rows.
                  </p>
                ) : subsidyRows.length === 0 ? (
                  <p className="mt-2 text-xs text-muted-foreground">No subsidy rows returned for this encounter.</p>
                ) : (
                  <div className="mt-2 overflow-x-auto rounded-lg border border-amber-100 bg-card">
                    <table className="w-full text-xs">
                      <thead className="bg-warning-soft/80 text-muted-foreground">
                        <tr>
                          <th className="px-3 py-2 text-left font-medium">Bill</th>
                          <th className="px-3 py-2 text-right font-medium">Subsidy</th>
                          <th className="px-3 py-2 text-left font-medium">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-amber-50">
                        {subsidyRows.map((row, idx) => (
                          <tr key={String(row.bill_id ?? row.id ?? idx)}>
                            <td className="px-3 py-2 font-mono text-[10px]">{String(row.bill_id ?? "—")}</td>
                            <td className="px-3 py-2 text-right">{String(row.subsidy_amount ?? row.amount ?? "—")}</td>
                            <td className="px-3 py-2">{String(row.status ?? "—")}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )
              ) : null}
            </div>
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">MusheX platform (wallets)</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/finance/mushex-platform/wallets?ownerRef=…</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                value={ownerRef}
                onChange={(e) => setOwnerRef(e.target.value)}
                className="min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm"
                placeholder="Owner ref"
                aria-label="Wallet owner ref"
              />
              <button
                type="button"
                className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
                onClick={() => setWalletsArmed(true)}
              >
                Load wallets
              </button>
            </div>
            {wQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : wQ.isError ? (
              <p className="mt-3 text-sm text-danger">Could not load wallets.</p>
            ) : wQ.data != null ? (
              <div className="mt-3">
                <FinanceWalletsTable
                  data={wQ.data}
                  isLoading={wQ.isPending}
                  error={wQ.error as Error | null}
                />
              </div>
            ) : (
              <p className="mt-3 text-xs text-muted-foreground">Enter an owner ref to list custodial wallets.</p>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
