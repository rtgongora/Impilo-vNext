"use client";

/**
 * Order Detail — View order information, items, and status timeline.
 * Route: /marketplace/orders/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  AlertTriangle,
  ArrowLeft,
  Package,
  CheckCircle2,
  Clock,
  Truck,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceOrder } from "@/hooks/queries/useMarketplace";

const STATUS_TIMELINE = ["PLACED", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED"];

const STATUS_ICON: Record<string, typeof Clock> = {
  PLACED: Clock,
  CONFIRMED: CheckCircle2,
  PROCESSING: Package,
  SHIPPED: Truck,
  DELIVERED: CheckCircle2,
};

export default function OrderDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useMarketplaceOrder(id);
  const order = data?.data;

  function getTimelineIndex(status: string): number {
    return STATUS_TIMELINE.indexOf(status);
  }

  return (
    <AppLayout>
      <PageShell title="Order Details" subtitle="View order information and tracking">
        <div className="mb-4">
          <Link
            href="/marketplace/orders"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Orders
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading order...</span>
          </div>
        ) : error || !order ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load order details</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Order Summary */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h3 className="font-medium text-gray-900">Order #{order.id.slice(0, 8).toUpperCase()}</h3>
                  <p className="text-xs text-gray-500 mt-0.5">
                    Placed: {new Date(order.attributes.createdAt).toLocaleString()}
                  </p>
                </div>
                <span
                  className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                    order.attributes.status === "DELIVERED"
                      ? "bg-green-100 text-green-700"
                      : order.attributes.status === "CANCELLED"
                      ? "bg-red-100 text-red-700"
                      : "bg-blue-100 text-blue-700"
                  }`}
                >
                  {order.attributes.status}
                </span>
              </div>
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-gray-500">Facility</dt>
                  <dd className="font-medium text-gray-900">{order.attributes.facilityId}</dd>
                </div>
                <div>
                  <dt className="text-gray-500">Total Amount</dt>
                  <dd className="font-medium text-gray-900">
                    ${order.attributes.totalAmount.toFixed(2)}
                  </dd>
                </div>
              </dl>
            </div>

            {/* Items */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Order Items</h3>
              <div className="space-y-3">
                {order.attributes.items.map((item, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
                  >
                    <div className="flex items-center gap-3">
                      <Package className="w-5 h-5 text-gray-400" />
                      <div>
                        <p className="text-sm font-medium text-gray-900">
                          Product: {item.productId}
                        </p>
                        <p className="text-xs text-gray-500">Qty: {item.quantity}</p>
                      </div>
                    </div>
                    <span className="text-sm font-medium text-gray-900">
                      ${(item.unitPrice * item.quantity).toFixed(2)}
                    </span>
                  </div>
                ))}
              </div>
              <div className="mt-4 pt-3 border-t flex justify-between text-sm">
                <span className="font-medium text-gray-700">Total</span>
                <span className="font-bold text-gray-900">
                  ${order.attributes.totalAmount.toFixed(2)}
                </span>
              </div>
            </div>

            {/* Status Timeline */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-4">Status Timeline</h3>
              <div className="space-y-3">
                {STATUS_TIMELINE.map((step, i) => {
                  const currentIndex = getTimelineIndex(order.attributes.status);
                  const isCompleted = i <= currentIndex;
                  const isCurrent = i === currentIndex;
                  const Icon = STATUS_ICON[step] || Clock;
                  return (
                    <div key={step} className="flex items-center gap-3">
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${
                          isCompleted
                            ? "bg-green-100 text-green-600"
                            : "bg-gray-100 text-gray-400"
                        } ${isCurrent ? "ring-2 ring-green-400" : ""}`}
                      >
                        <Icon className="w-4 h-4" />
                      </div>
                      <span
                        className={`text-sm ${
                          isCompleted ? "text-gray-900 font-medium" : "text-gray-400"
                        }`}
                      >
                        {step}
                      </span>
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
