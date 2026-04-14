"use client";

/**
 * Marketplace Cart — absorbs msika-flow-portal:/cart sidecar
 * Shopping cart, validation, payment initiation with full payment method selection.
 * Route: /marketplace/cart | Guard: auth
 */

import { useState } from "react";
import { ShoppingCart, Trash2, CreditCard, Loader2, Wallet } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { PaymentMethodPicker } from "@/components/payment/PaymentMethodPicker";
import { useCommerceCart, useValidateCart, useCheckoutCart } from "@/hooks/queries/useCommerceFlow";

export default function MarketplaceCartPage() {
  const { data: cartData, isLoading } = useCommerceCart();
  const validateCart = useValidateCart();
  const checkout = useCheckoutCart();
  const [validating, setValidating] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState("MUSHE_WALLET");

  const items = cartData?.data ?? [];
  const total = items.reduce(
    (sum: number, item: { quantity: number; unitPrice: number }) => sum + item.quantity * item.unitPrice,
    0,
  );

  return (
    <AppLayout>
      <PageShell title="Shopping Cart" subtitle="Review your order before checkout" icon={<ShoppingCart className="h-6 w-6" />}>
        {isLoading ? (
          <div className="flex items-center justify-center py-12 text-gray-500">
            <Loader2 className="h-5 w-5 animate-spin mr-2" /> Loading cart...
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-8 text-center text-sm text-gray-500">
            <ShoppingCart className="h-8 w-8 text-gray-300 mx-auto mb-2" />
            <p>Your cart is empty. Browse the <a href="/marketplace/catalog" className="text-impilo-500 underline">catalog</a> to add items.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Cart items */}
            <div className="rounded-lg border border-gray-200 bg-white divide-y">
              {items.map((item: { id: string; name: string; quantity: number; unitPrice: number }) => (
                <div key={item.id} className="flex items-center justify-between p-4">
                  <div>
                    <p className="font-medium text-gray-900">{item.name}</p>
                    <p className="text-sm text-gray-500">Qty: {item.quantity} x ${item.unitPrice.toFixed(2)}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-semibold">${(item.quantity * item.unitPrice).toFixed(2)}</span>
                    <button className="text-red-400 hover:text-red-600"><Trash2 className="h-4 w-4" /></button>
                  </div>
                </div>
              ))}
              {/* Total */}
              <div className="flex items-center justify-between p-4 bg-gray-50">
                <span className="text-sm font-semibold text-gray-900">Total</span>
                <span className="text-lg font-bold text-gray-900">${total.toFixed(2)}</span>
              </div>
            </div>

            {/* Payment method selection */}
            <div className="rounded-lg border border-gray-200 bg-white p-4 space-y-3">
              <div className="flex items-center gap-2 mb-1">
                <Wallet className="w-4 h-4 text-impilo-500" />
                <p className="text-sm font-semibold text-gray-900">How would you like to pay?</p>
              </div>
              <PaymentMethodPicker value={paymentMethod} onChange={setPaymentMethod} />
              {paymentMethod === "MUSHE_WALLET" && (
                <p className="text-xs text-impilo-600 bg-impilo-50 rounded-lg px-3 py-2">
                  Payment will be deducted from your Mushe Health Wallet. Ensure you have sufficient balance.
                </p>
              )}
            </div>

            {/* Actions */}
            <div className="flex justify-end gap-3">
              <button
                onClick={() => { setValidating(true); validateCart.mutate(undefined, { onSettled: () => setValidating(false) }); }}
                disabled={validating}
                className="px-4 py-2 text-sm font-medium border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {validating ? "Validating..." : "Validate Order"}
              </button>
              <button
                onClick={() => checkout.mutate()}
                disabled={checkout.isPending}
                className="flex items-center gap-2 px-5 py-2.5 text-sm font-medium text-white bg-impilo-500 rounded-lg hover:bg-impilo-600 disabled:opacity-50 transition-colors"
              >
                <CreditCard className="h-4 w-4" />
                {checkout.isPending
                  ? "Processing..."
                  : `Pay $${total.toFixed(2)} via ${paymentMethod.replace(/_/g, " ")}`}
              </button>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
