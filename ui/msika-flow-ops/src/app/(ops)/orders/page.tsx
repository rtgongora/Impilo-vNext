"use client";

import { useState, useEffect, useCallback } from "react";
import { opsApi, type Order, type PagedResponse } from "@/lib/opsApi";

const STATUS_STYLES: Record<string, string> = {
  CREATED: "bg-neutral-100 text-neutral-700",
  VALIDATED: "bg-blue-100 text-primary-hover",
  PRICED: "bg-indigo-100 text-primary-hover",
  CONFIRMED: "bg-purple-100 text-warning-foreground",
  FULFILLING: "bg-amber-100 text-warning-foreground",
  COMPLETED: "bg-emerald-100 text-primary-hover",
  CANCELLED: "bg-red-100 text-danger",
};

export default function OpsOrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const searchOrders = useCallback(async (p = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data: PagedResponse<Order> = await opsApi.searchOrders({
        query: query || undefined,
        status: statusFilter || undefined,
        page: p,
        size: 20,
      });
      setOrders(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
      setPage(p);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load orders");
    } finally {
      setLoading(false);
    }
  }, [query, statusFilter]);

  useEffect(() => {
    searchOrders(0);
  }, [searchOrders]);

  const viewOrder = async (orderId: string) => {
    setDetailLoading(true);
    try {
      const order = await opsApi.getOrder(orderId);
      setSelectedOrder(order);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load order details");
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">All Orders</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Cross-tenant order search and audit.
            {totalElements > 0 && (
              <span className="ml-2 text-neutral-400">({totalElements} total)</span>
            )}
          </p>
        </div>
        <button onClick={() => searchOrders(page)} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && searchOrders(0)}
          placeholder="Search by order ID, patient CPID, or vendor..."
          className="input-field flex-1 max-w-md"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="input-field w-44"
        >
          <option value="">All Statuses</option>
          <option value="CREATED">Created</option>
          <option value="VALIDATED">Validated</option>
          <option value="PRICED">Priced</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="FULFILLING">Fulfilling</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <button onClick={() => searchOrders(0)} className="btn-primary">
          Search
        </button>
      </div>

      {selectedOrder && (
        <div className="card p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">Order Detail</h2>
            <button onClick={() => setSelectedOrder(null)} className="text-xs text-neutral-500 hover:text-neutral-700">
              Close
            </button>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <span className="text-neutral-500 block text-xs">Order ID</span>
              <span className="font-mono text-xs">{selectedOrder.orderId}</span>
            </div>
            <div>
              <span className="text-neutral-500 block text-xs">Type</span>
              <span>{selectedOrder.orderType}</span>
            </div>
            <div>
              <span className="text-neutral-500 block text-xs">Status</span>
              <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[selectedOrder.status] ?? "bg-neutral-100"}`}>
                {selectedOrder.status}
              </span>
            </div>
            <div>
              <span className="text-neutral-500 block text-xs">Total</span>
              <span className="font-mono">{selectedOrder.currency} {selectedOrder.amountTotal}</span>
            </div>
            <div>
              <span className="text-neutral-500 block text-xs">Tenant</span>
              <span className="font-mono text-xs">{selectedOrder.tenantId}</span>
            </div>
            <div>
              <span className="text-neutral-500 block text-xs">Created</span>
              <span className="text-xs">{new Date(selectedOrder.createdAt).toLocaleString()}</span>
            </div>
          </div>
        </div>
      )}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Order ID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Amount</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Tenant</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Created</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  <div className="animate-pulse">Loading orders...</div>
                </td>
              </tr>
            ) : orders.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  No orders found.
                </td>
              </tr>
            ) : (
              orders.map((order) => (
                <tr key={order.orderId} className="hover:bg-neutral-50">
                  <td className="px-4 py-3 font-mono text-xs">{order.orderId.substring(0, 12)}...</td>
                  <td className="px-4 py-3">{order.orderType}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[order.status] ?? "bg-neutral-100"}`}>
                      {order.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right font-mono">{order.currency} {order.amountTotal}</td>
                  <td className="px-4 py-3 font-mono text-xs">{order.tenantId}</td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {new Date(order.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => viewOrder(order.orderId)}
                      disabled={detailLoading}
                      className="text-xs text-brand-primary hover:underline font-medium"
                    >
                      View
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-4">
          <button
            onClick={() => searchOrders(page - 1)}
            disabled={page === 0 || loading}
            className="btn-secondary text-xs px-3 py-1 disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-neutral-500">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => searchOrders(page + 1)}
            disabled={page >= totalPages - 1 || loading}
            className="btn-secondary text-xs px-3 py-1 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
