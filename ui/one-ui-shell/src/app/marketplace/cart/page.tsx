"use client";

/**
 * Marketplace Cart — absorbs msika-flow-portal:/cart sidecar
 * Shopping cart, validation, payment initiation with full payment method selection.
 * Route: /marketplace/cart | Guard: auth
 */

import { useState } from "react";
import { ShoppingCart, Trash2, CreditCard, Loader2, Wallet } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ContextualLearningPanel } from "@/components/learning/ContextualLearningPanel";
import { PageShell } from "@/components/PageShell";
import { useCommerceCart, useValidateCart, useCheckoutCart } from "@/hooks/queries/useCommerceFlow";

export default function MarketplaceCartPage() {
  const { data: cartData, isLoading } = useCommerceCart();
  const validateCart = useValidateCart();
  const checkout = useCheckoutCart();
  const [validating, setValidating] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState("MUSHE_WALLET");

  function asRecord(value: unknown): Record<string, unknown> | null {
    return value != null && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
  }

  const root = asRecord(cartData);
  const data = asRecord(root?.data);
  const cart = asRecord(data?.data);
  const cartId = typeof cart?.cartId === "string" ? cart.cartId : undefined;
  const items = Array.isArray(cart?.items) ? (cart?.items as Array<Record<string, unknown>>) : [];
  const total = 0;

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
            <p>Your cart is empty. Browse the <a href="/marketplace/catalog" className="text-primary underline">catalog</a> to add items.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Cart items */}
            <div className="rounded-lg border border-border bg-card divide-y">
              {items.map((raw) => {
                const item = raw as Record<string, unknown>;
                const lineId = String(item.id ?? item.msikaCoreCode ?? "item");
                const code = String(item.msikaCoreCode ?? item.name ?? "Unknown item");
                const qty = Number(item.qty ?? item.quantity ?? 1);
                return (
                <div key={lineId} className="flex items-center justify-between p-4">
                  <div>
                    <p className="font-medium text-foreground">{code}</p>
                    <p className="text-sm text-muted-foreground">Qty: {qty}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-semibold">$0.00</span>
                    <button className="text-red-400 hover:text-red-600"><Trash2 className="h-4 w-4" /></button>
                  </div>
                </div>
              );
              })}
              {/* Total */}
              <div className="flex items-center justify-between p-4 bg-background">
                <span className="text-sm font-semibold text-foreground">Total</span>
                <span className="text-lg font-bold text-foreground">${total.toFixed(2)}</span>
              </div>
            </div>

            {/* Payment method selection (routing to MusheX handled post-checkout) */}
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

            {/* Actions */}
            <div className="flex justify-end gap-3">
              <button
                onClick={() => {
                  setValidating(true);
                  validateCart.mutate(
                    {
                      items: items.map((i) => ({ msikaCoreCode: String(i.msikaCoreCode ?? ""), qty: Number(i.qty ?? 1) })),
                      channel: "WEB",
                      // if this is a facility-scoped cart (e.g., kiosk), callers should also provide facilityRef/providerRef
                    },
                    { onSettled: () => setValidating(false) },
                  );
                }}
                disabled={validating}
                className="px-4 py-2 text-sm font-medium border border-border rounded-lg hover:bg-background"
              >
                {validating ? "Validating..." : "Validate Order"}
              </button>
              <button
                onClick={() => cartId && checkout.mutate({ cartId, orderType: "OTC_PRODUCT_ORDER" })}
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
