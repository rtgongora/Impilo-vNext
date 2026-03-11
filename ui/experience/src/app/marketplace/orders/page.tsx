"use client";

/**
 * My Orders — Orders table with status, total, ordered by, and date.
 * Route: /marketplace/orders | pageTitle: "My Orders"
 */

import Link from "next/link";
import { ArrowLeft, Loader2, Package, ShoppingBag } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceOrders } from "@/hooks/queries/useMarketplace";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-yellow-100 text-yellow-700",
  APPROVED: "bg-blue-100 text-blue-700",
  SHIPPED: "bg-indigo-100 text-indigo-700",
  DELIVERED: "bg-green-100 text-green-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export default function OrdersPage() {
  const facility = useFacilityStore((s) => s.facility);
  const { data, isLoading } = useMarketplaceOrders(facility?.id ?? "");

  const orders = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="My Orders"
        subtitle="Track and manage purchase orders"
      >
        <div className="mb-4">
          <Link
            href="/marketplace"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to marketplace
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading orders...</span>
          </div>
        ) : orders.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <ShoppingBag className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No orders placed</p>
            <Link
              href="/marketplace/catalog"
              className="mt-3 inline-block text-sm text-blue-600 hover:text-blue-800"
            >
              Browse the catalog
            </Link>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Order #</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Total</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Ordered By</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((order) => {
                  const statusStyle =
                    STATUS_STYLES[order.attributes.status] ?? "bg-gray-100 text-gray-600";
                  return (
                    <tr key={order.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <Link
                          href={`/marketplace/orders/${order.id}`}
                          className="font-medium text-blue-600 hover:text-blue-800 flex items-center gap-1"
                        >
                          <Package className="w-3.5 h-3.5" />
                          {order.id.slice(0, 8).toUpperCase()}
                        </Link>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {order.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-900 font-medium">
                        ${order.attributes.totalAmount.toFixed(2)}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {order.attributes.facilityId}
                      </td>
                      <td className="px-4 py-3 text-gray-500">
                        {new Date(order.attributes.createdAt).toLocaleDateString()}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
