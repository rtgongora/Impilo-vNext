"use client";

/**
 * Marketplace Cart — absorbs msika-flow-portal:/cart sidecar
 * Shopping cart, validation, payment initiation.
 * Route: /marketplace/cart | Guard: auth
 */

import { useState } from "react";
import { ShoppingCart, Trash2, CreditCard, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCommerceCart, useValidateCart, useCheckoutCart } from "@/hooks/queries/useCommerceFlow";

export default function MarketplaceCartPage() {
  const { data: cartData, isLoading } = useCommerceCart();
  const validateCart = useValidateCart();
  const checkout = useCheckoutCart();
  const [validating, setValidating] = useState(false);

  const items = cartData?.data ?? [];

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
            <p>Your cart is empty. Browse the <a href="/marketplace/catalog" className="text-blue-600 underline">catalog</a> to add items.</p>
          </div>
        ) : (
          <div className="space-y-4">
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
            </div>
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
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700"
              >
                <CreditCard className="h-4 w-4" /> {checkout.isPending ? "Processing..." : "Checkout"}
              </button>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
