"use client";

/**
 * Marketplace Cart — real persisted cart over msika-flow via the Experience BFF.
 * Real line prices from the payload, remove-line, payment-method selection carried
 * into checkout, and post-checkout redirect to the pay page.
 * Route: /marketplace/cart | Guard: auth
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ShoppingCart, Trash2, CreditCard, Loader2, Wallet } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ContextualLearningPanel } from "@/components/learning/ContextualLearningPanel";
import { PageShell } from "@/components/PageShell";
import {
  extractOrderId,
  unwrapCommerceData,
  useCommerceCart,
  useCheckoutCart,
  useRemoveCartItem,
  useValidateCart,
} from "@/hooks/queries/useCommerceFlow";

type CartLine = {
  lineId: string;
  code: string;
  label: string;
  qty: number;
  unitPrice: number | null;
  currency: string | null;
};

function asNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value))) return Number(value);
  return null;
}

function toLine(raw: Record<string, unknown>, index: number): CartLine {
  const code = String(raw.msikaCoreCode ?? raw.listingId ?? raw.itemCode ?? "");
  const unitPrice = asNumber(raw.unitPrice ?? raw.unit_price ?? raw.priceAmount ?? raw.price);
  const currency =
    typeof raw.currency === "string"
      ? raw.currency
      : typeof raw.priceCurrency === "string"
        ? raw.priceCurrency
        : null;
  return {
    lineId: String(raw.id ?? raw.lineId ?? code ?? `line-${index}`),
    code,
    label: String(raw.name ?? raw.title ?? code ?? "Unknown item"),
    qty: Number(raw.qty ?? raw.quantity ?? 1),
    unitPrice,
    currency,
  };
}

/** Friction / validation errors arrive as 422 bodies — surface them inline, never swallow. */
function frictionMessages(error: unknown): string[] {
  const err = error as {
    status?: number;
    error?: { message?: string; details?: unknown };
    message?: string;
    errors?: unknown;
    details?: unknown;
  } | null;
  const out: string[] = [];
  const push = (v: unknown) => {
    if (typeof v === "string" && v.trim()) out.push(v);
    else if (v && typeof v === "object") {
      const rec = v as Record<string, unknown>;
      const msg = rec.message ?? rec.reason ?? rec.detail;
      if (typeof msg === "string" && msg.trim()) out.push(msg);
    }
  };
  push(err?.error?.message ?? err?.message);
  for (const bag of [err?.error?.details, err?.errors, err?.details]) {
    if (Array.isArray(bag)) bag.forEach(push);
  }
  return out.length > 0 ? [...new Set(out)] : ["The order could not be processed."];
}

export default function MarketplaceCartPage() {
  const router = useRouter();
  const { data: cartData, isLoading } = useCommerceCart();
  const validateCart = useValidateCart();
  const checkout = useCheckoutCart();
  const removeItem = useRemoveCartItem();
  const [paymentMethod, setPaymentMethod] = useState("MUSHE_WALLET");
  const [inlineErrors, setInlineErrors] = useState<string[] | null>(null);
  const [validationOk, setValidationOk] = useState(false);

  const cart = unwrapCommerceData(cartData);
  const cartId = typeof cart?.cartId === "string" ? cart.cartId : undefined;
  const rawItems = Array.isArray(cart?.items) ? (cart?.items as Array<Record<string, unknown>>) : [];
  const items = rawItems.map(toLine);

  const currency = items.find((line) => line.currency)?.currency ?? "USD";
  const pricedTotal = items.reduce((sum, line) => sum + (line.unitPrice ?? 0) * line.qty, 0);
  const unpricedCount = items.filter((line) => line.unitPrice == null).length;

  return (
    <AppLayout>
      <PageShell title="Shopping Cart" subtitle="Review your order before checkout" icon={<ShoppingCart className="h-6 w-6" />}>
        <div className="mb-4">
          <ContextualLearningPanel
            appCode="marketplace"
            routeRef="/marketplace/cart"
            workflowCode="order_exception"
            title="Commerce & exceptions"
          />
        </div>
        {isLoading ? (
          <div className="flex items-center justify-center py-12 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin mr-2" /> Loading cart...
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <ShoppingCart className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
            <p>
              Your cart is empty. Browse the <a href="/marketplace/store/search" className="text-primary underline">store</a> or the{" "}
              <a href="/marketplace/catalog" className="text-primary underline">catalog</a> to add items.
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Cart items */}
            <div className="rounded-lg border border-border bg-card divide-y">
              {items.map((line) => (
                <div key={line.lineId} className="flex items-center justify-between p-4">
                  <div>
                    <p className="font-medium text-foreground">{line.label}</p>
                    <p className="text-sm text-muted-foreground">Qty: {line.qty}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-semibold">
                      {line.unitPrice != null ? `${line.currency ?? currency} ${(line.unitPrice * line.qty).toFixed(2)}` : "Unpriced"}
                    </span>
                    <button
                      type="button"
                      aria-label={`Remove ${line.label}`}
                      disabled={removeItem.isPending || !cartId || !line.code}
                      className="text-red-400 hover:text-red-600 disabled:opacity-40"
                      onClick={() => cartId && line.code && removeItem.mutate({ cartId, msikaCoreCode: line.code })}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              ))}
              {/* Total */}
              <div className="flex items-center justify-between p-4 bg-background">
                <span className="text-sm font-semibold text-foreground">
                  Total{unpricedCount > 0 ? ` (${unpricedCount} unpriced line${unpricedCount === 1 ? "" : "s"} excluded)` : ""}
                </span>
                <span className="text-lg font-bold text-foreground">{currency} {pricedTotal.toFixed(2)}</span>
              </div>
            </div>

            {unpricedCount > 0 ? (
              <p className="rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-xs text-warning-foreground">
                Some lines have no vendor price yet — checkout will reject them (422) until the listing carries a price.
              </p>
            ) : null}

            {/* Payment method selection — carried into checkout and the pay page */}
            <div className="rounded-lg border border-border bg-card p-4 space-y-3">
              <div className="flex items-center gap-2 mb-1">
                <Wallet className="w-4 h-4 text-primary" />
                <p className="text-sm font-semibold text-foreground">How would you like to pay?</p>
              </div>
              <select
                value={paymentMethod}
                onChange={(event) => setPaymentMethod(event.target.value)}
                className="w-full rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground"
                aria-label="Payment method"
              >
                <option value="MUSHE_WALLET">Mushe Wallet</option>
                <option value="CARD">Card</option>
                <option value="BANK_TRANSFER">Bank transfer</option>
              </select>
              {paymentMethod === "MUSHE_WALLET" && (
                <p className="text-xs text-primary bg-primary-soft rounded-lg px-3 py-2">
                  Payment will be deducted from your Mushe Health Wallet. Ensure you have sufficient balance.
                </p>
              )}
            </div>

            {inlineErrors ? (
              <div className="rounded-lg border border-danger/28 bg-danger-soft p-3 text-sm text-red-800">
                <p className="font-medium">The order was not accepted:</p>
                <ul className="mt-1 list-disc pl-5">
                  {inlineErrors.map((message) => (
                    <li key={message}>{message}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            {validationOk ? (
              <p className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                Cart validated — no restrictions blocked these lines.
              </p>
            ) : null}

            {/* Actions */}
            <div className="flex justify-end gap-3">
              <button
                onClick={() => {
                  setInlineErrors(null);
                  setValidationOk(false);
                  validateCart.mutate(
                    {
                      items: items
                        .filter((line) => line.code)
                        .map((line) => ({ msikaCoreCode: line.code, qty: line.qty })),
                      channel: "WEB",
                    },
                    {
                      onSuccess: () => setValidationOk(true),
                      onError: (err) => setInlineErrors(frictionMessages(err)),
                    },
                  );
                }}
                disabled={validateCart.isPending}
                className="px-4 py-2 text-sm font-medium border border-border rounded-lg hover:bg-background"
              >
                {validateCart.isPending ? "Validating..." : "Validate Order"}
              </button>
              <button
                onClick={() => {
                  if (!cartId) return;
                  setInlineErrors(null);
                  checkout.mutate(
                    { cartId, orderType: "OTC_PRODUCT_ORDER", paymentMethod },
                    {
                      onSuccess: (res) => {
                        const orderId = extractOrderId(res);
                        if (orderId) {
                          router.push(`/marketplace/orders/${encodeURIComponent(orderId)}/pay?method=${encodeURIComponent(paymentMethod)}`);
                        } else {
                          setInlineErrors(["Checkout succeeded but no order id was returned — check My Orders."]);
                        }
                      },
                      onError: (err) => setInlineErrors(frictionMessages(err)),
                    },
                  );
                }}
                disabled={checkout.isPending || !cartId}
                className="flex items-center gap-2 px-5 py-2.5 text-sm font-medium text-white bg-primary rounded-lg hover:bg-primary-hover disabled:opacity-50 transition-colors"
              >
                <CreditCard className="h-4 w-4" />
                {checkout.isPending
                  ? "Processing..."
                  : `Checkout via ${paymentMethod.replace(/_/g, " ")}`}
              </button>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
