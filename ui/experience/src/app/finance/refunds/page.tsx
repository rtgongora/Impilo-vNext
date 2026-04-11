"use client";

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FinanceRefundsNotice } from "@/components/FinanceAccessNotice";
import { useRefundCreate, useRefundPaymentIntent } from "@/hooks/queries/useFinanceRefunds";

export default function FinanceRefundsPage() {
  const [intentInput, setIntentInput] = useState("");
  const [activeIntentId, setActiveIntentId] = useState<string | undefined>();
  const intentQ = useRefundPaymentIntent(activeIntentId);

  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const refundM = useRefundCreate();

  return (
    <AppLayout>
      <PageShell
        title="Refunds"
        subtitle="Load a MusheX payment intent and create a refund — proxied under /internal/v1/finance/refunds."
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="max-w-3xl space-y-6">
          <FinanceRefundsNotice />

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Payment intent</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/refunds/payment-intents/&#123;intentId&#125;</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                type="text"
                value={intentInput}
                onChange={(e) => setIntentInput(e.target.value)}
                placeholder="Payment intent id"
                className="min-w-[220px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                aria-label="Payment intent id"
              />
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
                onClick={() => setActiveIntentId(intentInput.trim() || undefined)}
              >
                Load intent
              </button>
            </div>
            {intentQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : intentQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load intent (check id and roles).</p>
            ) : intentQ.data ? (
              <pre className="mt-3 max-h-64 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(intentQ.data, null, 2)}
              </pre>
            ) : (
              <p className="mt-3 text-xs text-slate-500">Enter an id and choose Load intent.</p>
            )}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Create refund</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST{" "}
              <code className="text-[11px]">/internal/v1/finance/refunds/payment-intents/&#123;intentId&#125;/refund</code>{" "}
              with JSON body (MusheX typically expects <code className="text-[11px]">amount</code> and{" "}
              <code className="text-[11px]">reason</code>).
            </p>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="text-xs text-slate-600">
                Amount
                <input
                  type="number"
                  step="0.01"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                />
              </label>
              <label className="text-xs text-slate-600 sm:col-span-2">
                Reason
                <input
                  type="text"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                />
              </label>
            </div>
            <button
              type="button"
              disabled={refundM.isPending || !activeIntentId || !amount}
              className="mt-4 rounded-lg bg-amber-700 px-4 py-2 text-sm font-medium text-white hover:bg-amber-800 disabled:opacity-50"
              onClick={() => {
                if (!activeIntentId) return;
                const amt = Number(amount);
                if (!Number.isFinite(amt)) return;
                refundM.mutate({
                  intentId: activeIntentId,
                  body: { amount: amt, reason: reason.trim() || "Operator refund" },
                });
              }}
            >
              {refundM.isPending ? (
                <span className="inline-flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Submitting…
                </span>
              ) : (
                "Submit refund"
              )}
            </button>
            {refundM.data != null ? (
              <pre className="mt-3 max-h-48 overflow-auto rounded-lg border border-slate-100 bg-slate-50 p-3 text-[11px]">
                {JSON.stringify(refundM.data, null, 2)}
              </pre>
            ) : null}
          </div>

          <p className="text-xs text-slate-500">
            <Link href="/finance/commerce-integrations" className="text-indigo-700 hover:underline">
              Integration map
            </Link>
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
