"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { AlertTriangle, ArrowLeft, CheckCircle2, Clock, Loader2, Package, Truck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceOrder } from "@/hooks/queries/useMarketplace";

const STATUS_TIMELINE = ["PENDING", "APPROVED", "SHIPPED", "DELIVERED"];
const STATUS_ICON: Record<string, typeof Clock> = {
  PENDING: Clock,
  APPROVED: CheckCircle2,
  SHIPPED: Truck,
  DELIVERED: CheckCircle2,
};

export default function OrderDetailPage() {
  const params = useParams();
  const id = params.id as string;
  const orderQuery = useMarketplaceOrder(id);
  const order = orderQuery.data;

  const currentIndex = order ? STATUS_TIMELINE.indexOf(order.status) : -1;

  return (
    <AppLayout>
      <PageShell title="Order Details" subtitle="View order information, line items, and fulfilment status.">
        <div className="mb-4">
          <Link href="/marketplace/orders" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Back to orders
          </Link>
        </div>

        {orderQuery.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-gray-500">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading order...
          </div>
        ) : !order ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center">
            <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
            <p className="text-sm text-red-600">Failed to load order details.</p>
          </div>
        ) : (
          <div className="max-w-3xl space-y-6">
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <h3 className="text-lg font-semibold text-slate-900">{order.orderNumber}</h3>
                  <p className="mt-1 text-sm text-slate-500">Placed {new Date(order.createdAt).toLocaleString()} • Ordered by {order.orderedBy}</p>
                </div>
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${order.status === "DELIVERED" ? "bg-emerald-100 text-emerald-800" : order.status === "SHIPPED" ? "bg-indigo-100 text-indigo-800" : "bg-amber-100 text-amber-800"}`}>
                  {order.status}
                </span>
              </div>
              <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-slate-500">Facility</dt>
                  <dd className="font-medium text-slate-900">{order.facilityId}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Total amount</dt>
                  <dd className="font-medium text-slate-900">{order.currency} {order.totalAmount.toFixed(2)}</dd>
                </div>
              </dl>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-slate-900">Order items</h3>
              <div className="mt-4 space-y-3">
                {order.items.map((item, index) => (
                  <div key={`${item.productId}-${index}`} className="flex items-center justify-between rounded-xl bg-slate-50 p-3">
                    <div className="flex items-center gap-3">
                      <Package className="h-5 w-5 text-slate-400" />
                      <div>
                        <p className="text-sm font-medium text-slate-900">{item.description || item.productId}</p>
                        <p className="text-xs text-slate-500">{item.productId} • Qty {item.quantity}</p>
                      </div>
                    </div>
                    <span className="text-sm font-medium text-slate-900">{order.currency} {(item.unitPrice * item.quantity).toFixed(2)}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="text-lg font-semibold text-slate-900">Fulfilment timeline</h3>
              <div className="mt-4 space-y-3">
                {STATUS_TIMELINE.map((step, index) => {
                  const Icon = STATUS_ICON[step] ?? Clock;
                  const completed = index <= currentIndex;
                  const current = index === currentIndex;
                  return (
                    <div key={step} className="flex items-center gap-3">
                      <div className={`flex h-8 w-8 items-center justify-center rounded-full ${completed ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-400"} ${current ? "ring-2 ring-emerald-300" : ""}`}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <span className={`text-sm ${completed ? "font-medium text-slate-900" : "text-slate-400"}`}>{step}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
