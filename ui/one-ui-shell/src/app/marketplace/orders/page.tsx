"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Package, ShoppingBag } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateOrder, useMarketplaceOrders } from "@/hooks/queries/useMarketplace";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useAuthStore } from "@/hooks/useAuthStore";

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-amber-100 text-warning-foreground",
  APPROVED: "bg-primary-soft text-primary-hover",
  SHIPPED: "bg-indigo-100 text-primary-hover",
  DELIVERED: "bg-emerald-100 text-primary-hover",
  CANCELLED: "bg-rose-100 text-rose-800",
};

function formatMoney(amount: number, currency: string) {
  return `${currency} ${amount.toFixed(2)}`;
}

export default function OrdersPage() {
  const facility = useFacilityStore((state) => state.facility);
  const user = useAuthStore((state) => state.user);
  const ordersQuery = useMarketplaceOrders(facility?.id ?? "");
  const createOrder = useCreateOrder();
  const [productId, setProductId] = useState("");
  const [description, setDescription] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [unitPrice, setUnitPrice] = useState("0");
  const orders = ordersQuery.data ?? [];

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!facility || !user) return;

    const qty = Number(quantity);
    const price = Number(unitPrice);
    await createOrder.mutateAsync({
      facilityId: facility.id,
      orderNumber: `PO-${new Date().toISOString().slice(0, 10).replace(/-/g, "")}-${Math.random().toString(16).slice(2, 8).toUpperCase()}`,
      orderedBy: user.displayName,
      items: [{ productId, description, quantity: qty, unitPrice: price }],
      totalAmount: qty * price,
    });

    setProductId("");
    setDescription("");
    setQuantity("1");
    setUnitPrice("0");
  }

  return (
    <AppLayout>
      <PageShell title="Marketplace Orders" subtitle="Place and track facility procurement orders from the same experience layer.">
        <div className="mb-4">
          <Link href="/marketplace" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors">
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        {!facility ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Select a facility before placing procurement orders.
            <div className="mt-3"><Link href="/workspace" className="font-medium underline">Choose facility work</Link></div>
          </div>
        ) : (
          <div className="space-y-6">
            <section className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
              <form onSubmit={handleSubmit} className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Create order</div>
                <h2 className="mt-1 text-xl font-semibold text-foreground">{facility.name}</h2>
                <p className="mt-2 text-sm text-muted-foreground">Capture the order where the inventory gap is already known, then follow shipping and delivery without switching products.</p>
                <div className="mt-5 space-y-4">
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Catalog or service ID</span>
                    <input value={productId} onChange={(event) => setProductId(event.target.value)} required className="w-full rounded-lg border border-border px-3 py-2" placeholder="95000000-..." />
                  </label>
                  <label className="block text-sm">
                    <span className="mb-1 block font-medium text-foreground">Description</span>
                    <input value={description} onChange={(event) => setDescription(event.target.value)} required className="w-full rounded-lg border border-border px-3 py-2" placeholder="Courier run, equipment servicing, or supply bundle" />
                  </label>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="block text-sm">
                      <span className="mb-1 block font-medium text-foreground">Quantity</span>
                      <input value={quantity} onChange={(event) => setQuantity(event.target.value)} type="number" min={1} required className="w-full rounded-lg border border-border px-3 py-2" />
                    </label>
                    <label className="block text-sm">
                      <span className="mb-1 block font-medium text-foreground">Unit price (USD)</span>
                      <input value={unitPrice} onChange={(event) => setUnitPrice(event.target.value)} type="number" min={0} step="0.01" required className="w-full rounded-lg border border-border px-3 py-2" />
                    </label>
                  </div>
                </div>
                <div className="mt-5 flex items-center justify-between gap-3">
                  <Link href="/marketplace/catalog" className="text-sm font-medium text-muted-foreground underline">Browse live catalog</Link>
                  <button disabled={createOrder.isPending} type="submit" className="rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-60">
                    {createOrder.isPending ? "Creating..." : "Create order"}
                  </button>
                </div>
              </form>

              <div className="rounded-2xl border border-border bg-card shadow-sm">
                <div className="flex items-center justify-between border-b border-border px-5 py-4">
                  <div>
                    <h3 className="text-lg font-semibold text-foreground">Facility orders</h3>
                    <p className="text-sm text-muted-foreground">Track order status from request to delivery.</p>
                  </div>
                  {ordersQuery.isLoading ? <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" /> : null}
                </div>
                {orders.length === 0 ? (
                  <div className="p-10 text-center text-sm text-muted-foreground">
                    <ShoppingBag className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                    No facility orders placed yet.
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead className="bg-background text-left text-muted-foreground">
                        <tr>
                          <th className="px-4 py-3 font-medium">Order #</th>
                          <th className="px-4 py-3 font-medium">Status</th>
                          <th className="px-4 py-3 font-medium">Total</th>
                          <th className="px-4 py-3 font-medium">Ordered by</th>
                          <th className="px-4 py-3 font-medium">Date</th>
                        </tr>
                      </thead>
                      <tbody>
                        {orders.map((order) => (
                          <tr key={order.id} className="border-t border-border hover:bg-background">
                            <td className="px-4 py-3">
                              <Link href={`/marketplace/orders/${order.id}`} className="inline-flex items-center gap-1 font-medium text-primary hover:text-primary-hover">
                                <Package className="h-3.5 w-3.5" />
                                {order.orderNumber}
                              </Link>
                            </td>
                            <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[order.status] ?? "bg-neutral-100 text-foreground"}`}>{order.status}</span></td>
                            <td className="px-4 py-3 font-medium text-foreground">{formatMoney(order.totalAmount, order.currency)}</td>
                            <td className="px-4 py-3 text-muted-foreground">{order.orderedBy}</td>
                            <td className="px-4 py-3 text-muted-foreground">{new Date(order.createdAt).toLocaleDateString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
