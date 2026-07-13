"use client";

/**
 * Order payment — real money leg for the buyer lane (M7).
 *
 * Order summary → pay action (msika-flow creates a REAL MusheX payment intent) →
 * wallet-composed confirm (useCommercePaymentConfirm, the single confirm seam) →
 * poll order status to PAID → success links to wallet transactions + tracking.
 * Route: /marketplace/orders/[id]/pay | Guard: auth
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { ArrowLeft, CheckCircle2, CreditCard, Loader2, Receipt, Truck, Wallet } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  unwrapCommerceData,
  useCommerceOrder,
  useCommerceOrderAction,
  useCommercePaymentConfirm,
} from "@/hooks/queries/useCommerceFlow";
import { useBalance, useWalletByOwner } from "@/hooks/queries/useMusheWallet";
import { useAuthStore } from "@/hooks/useAuthStore";
import { findIntentId } from "@/lib/marketplace/find-intent-id";

const SETTLED_STATUSES = new Set(["PAID", "IN_PROGRESS", "OUT_FOR_DELIVERY", "DELIVERED", "COLLECTED", "COMPLETED"]);

function asNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value))) return Number(value);
  return null;
}

function errorMessage(error: unknown): string {
  const err = error as { error?: { message?: string }; message?: string } | null;
  return err?.error?.message ?? err?.message ?? "The payment step failed.";
}

export default function OrderPayPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const orderId = String(params?.id ?? "");
  const method = searchParams.get("method") ?? "MUSHE_WALLET";

  const user = useAuthStore((state) => state.user);
  const [intentId, setIntentId] = useState<string | undefined>(undefined);
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const payAction = useCommerceOrderAction();
  const confirm = useCommercePaymentConfirm();

  // Poll the order while a confirm has been sent and the loop has not closed yet.
  const orderQ = useCommerceOrder(orderId, { refetchIntervalMs: confirmed ? 4000 : undefined });
  const order = useMemo(() => unwrapCommerceData(orderQ.data), [orderQ.data]);
  const status = typeof order?.status === "string" ? order.status : undefined;
  const amount = asNumber(order?.amountTotal ?? order?.totalAmount ?? order?.amount);
  const currency = typeof order?.currency === "string" ? order.currency : "USD";
  const paid = status != null && SETTLED_STATUSES.has(status);

  // Stop polling once the loop closes.
  useEffect(() => {
    if (paid && confirmed) setConfirmed(false);
  }, [paid, confirmed]);

  const walletQ = useWalletByOwner("PERSON", user?.id ?? null);
  const wallet = unwrapCommerceData(walletQ.data) as Record<string, unknown> | null;
  const walletId = typeof wallet?.id === "string" ? wallet.id : undefined;
  const balanceQ = useBalance(walletId);
  const balance = asNumber(unwrapCommerceData(balanceQ.data)?.balance ?? (balanceQ.data as { data?: { balance?: unknown } } | undefined)?.data?.balance);

  function runPay() {
    setError(null);
    payAction.mutate(
      { orderId, action: "pay" },
      {
        onSuccess: (res) => {
          const found = findIntentId(res);
          if (!found) {
            setError("Payment intent was created but no intent id came back — retry or check My Orders.");
            void orderQ.refetch();
            return;
          }
          setIntentId(found);
        },
        onError: (err) => setError(errorMessage(err)),
      },
    );
  }

  function runConfirm() {
    setError(null);
    confirm.mutate(
      { orderId, intentId, method, amount: amount ?? undefined, currency },
      {
        onSuccess: () => {
          setConfirmed(true);
          void orderQ.refetch();
        },
        onError: (err) => setError(errorMessage(err)),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Pay for order" subtitle="Complete payment through your Mushe wallet or another supported method." icon={<CreditCard className="h-6 w-6" />}>
        <div className="mb-4">
          <Link href={`/marketplace/orders/${encodeURIComponent(orderId)}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to order
          </Link>
        </div>

        {orderQ.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading order...
          </div>
        ) : !order ? (
          <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
            This order could not be loaded, so payment cannot start.
          </div>
        ) : paid ? (
          <div className="max-w-xl space-y-4">
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-6 text-center">
              <CheckCircle2 className="mx-auto mb-2 h-10 w-10 text-emerald-600" />
              <h3 className="text-lg font-semibold text-emerald-900">Payment received</h3>
              <p className="mt-1 text-sm text-emerald-800">
                Order <span className="font-mono">{orderId}</span> is {status}.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Link href="/wallet/transactions" className="inline-flex items-center gap-2 rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:bg-background">
                <Receipt className="h-4 w-4" /> Wallet transactions
              </Link>
              <Link href={`/marketplace/orders/${encodeURIComponent(orderId)}`} className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground">
                <Truck className="h-4 w-4" /> Track order
              </Link>
            </div>
          </div>
        ) : (
          <div className="max-w-xl space-y-4">
            {/* Order summary */}
            <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-foreground">Order summary</h3>
              <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-muted-foreground">Order</dt>
                  <dd className="font-mono text-xs text-foreground break-all">{orderId}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Status</dt>
                  <dd className="font-medium text-foreground">{status ?? "—"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Amount due</dt>
                  <dd className="font-semibold text-foreground">{amount != null ? `${currency} ${amount.toFixed(2)}` : "Not priced yet"}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Method</dt>
                  <dd className="font-medium text-foreground">{method.replace(/_/g, " ")}</dd>
                </div>
              </dl>
              {method === "MUSHE_WALLET" && balance != null ? (
                <p className="mt-3 flex items-center gap-2 rounded-lg bg-primary-soft px-3 py-2 text-xs text-primary">
                  <Wallet className="h-3.5 w-3.5" /> Wallet balance: {currency} {balance.toFixed(2)}
                </p>
              ) : null}
            </div>

            {error ? (
              <div className="rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">{error}</div>
            ) : null}

            {/* Step 1 — create the real MusheX intent */}
            {!intentId ? (
              <button
                type="button"
                disabled={payAction.isPending}
                onClick={runPay}
                className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
              >
                {payAction.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}
                {payAction.isPending ? "Creating payment intent..." : "Start payment"}
              </button>
            ) : (
              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm space-y-3">
                <p className="text-sm text-foreground">
                  Payment intent <span className="font-mono text-xs break-all">{intentId}</span> is ready.
                </p>
                {confirmed ? (
                  <p className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" /> Waiting for payment confirmation — this page refreshes the order automatically.
                  </p>
                ) : (
                  <button
                    type="button"
                    disabled={confirm.isPending}
                    onClick={runConfirm}
                    className="inline-flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
                  >
                    {confirm.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Wallet className="h-4 w-4" />}
                    {confirm.isPending ? "Confirming..." : `Pay ${amount != null ? `${currency} ${amount.toFixed(2)}` : ""} now`}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => void orderQ.refetch()}
                  className="block text-xs font-medium text-primary hover:underline"
                >
                  Refresh order status
                </button>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
