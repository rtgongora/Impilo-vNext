"use client";

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Building2, FileSpreadsheet, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFinanceBillingCharges, useFinanceBillingInvoice } from "@/hooks/queries/useFinanceBillingWorkspace";
import { useFinanceMushexWallets } from "@/hooks/queries/useFinanceMushexPlatform";

export default function FinanceWorkspacePage() {
  const [invoiceId, setInvoiceId] = useState("");
  const [billId, setBillId] = useState("");
  const [ownerRef, setOwnerRef] = useState("");
  const [invoiceArmed, setInvoiceArmed] = useState(false);
  const [chargesArmed, setChargesArmed] = useState(false);
  const [walletsArmed, setWalletsArmed] = useState(false);

  const invQ = useFinanceBillingInvoice(invoiceId.trim(), invoiceArmed);
  const chQ = useFinanceBillingCharges(billId.trim(), chargesArmed);
  const wQ = useFinanceMushexWallets(ownerRef.trim(), walletsArmed);

  return (
    <AppLayout>
      <PageShell
        title="Finance workspace"
        subtitle="COSTA lifecycle and MusheX custodial platform through the Experience BFF finance plane."
        icon={<Building2 className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
              <FileSpreadsheet className="h-4 w-4" /> Billing workspace (COSTA)
            </h2>
            <p className="mt-1 text-xs text-slate-500">
              GET{" "}
              <code className="text-[11px]">/internal/v1/finance/billing-workspace/lifecycle/invoices/…</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                value={invoiceId}
                onChange={(e) => setInvoiceId(e.target.value)}
                className="min-w-[200px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                placeholder="Invoice id"
                aria-label="Invoice id"
              />
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
                onClick={() => setInvoiceArmed(true)}
              >
                Load invoice
              </button>
            </div>
            {invQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : invQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load invoice.</p>
            ) : invQ.data != null ? (
              <pre className="mt-3 max-h-64 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(invQ.data, null, 2)}
              </pre>
            ) : (
              <p className="mt-3 text-xs text-slate-500">Load an invoice to inspect COSTA lifecycle JSON.</p>
            )}

            <p className="mt-6 text-xs text-slate-500">
              GET{" "}
              <code className="text-[11px]">/internal/v1/finance/billing-workspace/lifecycle/charges?billId=…</code>
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
              <input
                value={billId}
                onChange={(e) => setBillId(e.target.value)}
                className="min-w-[200px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                placeholder="Bill id"
                aria-label="Bill id for charges"
              />
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
                onClick={() => setChargesArmed(true)}
              >
                Load charges
              </button>
            </div>
            {chQ.isLoading ? (
              <p className="mt-2 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading charges…
              </p>
            ) : chQ.isError ? (
              <p className="mt-2 text-sm text-red-700">Could not load charges.</p>
            ) : chQ.data != null ? (
              <pre className="mt-2 max-h-48 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(chQ.data, null, 2)}
              </pre>
            ) : null}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">MusheX platform (wallets)</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/mushex-platform/wallets?ownerRef=…</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                value={ownerRef}
                onChange={(e) => setOwnerRef(e.target.value)}
                className="min-w-[200px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm"
                placeholder="Owner ref"
                aria-label="Wallet owner ref"
              />
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
                onClick={() => setWalletsArmed(true)}
              >
                Load wallets
              </button>
            </div>
            {wQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : wQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load wallets.</p>
            ) : wQ.data != null ? (
              <pre className="mt-3 max-h-[28rem] overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(wQ.data, null, 2)}
              </pre>
            ) : (
              <p className="mt-3 text-xs text-slate-500">Enter an owner ref to list custodial wallets.</p>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
