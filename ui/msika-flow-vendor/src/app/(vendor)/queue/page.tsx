"use client";

import { useState, useEffect, useCallback } from "react";
import { vendorApi, type Order } from "@/lib/vendorApi";
import { useSessionStore } from "@/stores/sessionStore";

export default function QueuePage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const vendorId = useSessionStore((s) => s.vendorId);

  const loadOrders = useCallback(async () => {
    if (!vendorId) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const data = await vendorApi.getVendorOrders(vendorId);
      setOrders(data.content || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load orders");
    } finally {
      setLoading(false);
    }
  }, [vendorId]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleAccept = async (orderId: string) => {
    if (!vendorId) return;
    setActionLoading(true);
    try {
      await vendorApi.acceptOrder(vendorId, orderId);
      await loadOrders();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to accept");
    } finally {
      setActionLoading(false);
    }
  };

  const handleMarkReady = async (orderId: string) => {
    setActionLoading(true);
    try {
      await vendorApi.markReady(orderId);
      await loadOrders();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to mark ready");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading)
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
        </div>
      </div>
    );

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">
            Order Queue
          </h1>
          <p className="text-sm text-neutral-500 mt-1">
            Incoming orders awaiting acceptance and fulfillment.
          </p>
        </div>
        <button onClick={loadOrders} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">
                Order #
              </th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">
                Type
              </th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">
                Amount
              </th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">
                Status
              </th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">
                Created
              </th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {orders.map((order) => (
              <tr key={order.orderId} className="hover:bg-neutral-50">
                <td className="px-4 py-3 font-mono text-xs">
                  {order.orderId}
                </td>
                <td className="px-4 py-3">{order.orderType}</td>
                <td className="px-4 py-3 font-medium">
                  {order.currency} {order.amountTotal}
                </td>
                <td className="px-4 py-3">
                  <span className={`badge-${order.status.toLowerCase()}`}>
                    {order.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-neutral-500">
                  {new Date(order.createdAt).toLocaleString()}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    {order.status === "ROUTED" && (
                      <button
                        onClick={() => handleAccept(order.orderId)}
                        disabled={actionLoading}
                        className="text-xs text-primary hover:text-primary-hover font-medium"
                      >
                        Accept
                      </button>
                    )}
                    {(order.status === "ACCEPTED" ||
                      order.status === "IN_PROGRESS") && (
                      <button
                        onClick={() => handleMarkReady(order.orderId)}
                        disabled={actionLoading}
                        className="text-xs text-primary hover:text-blue-800 font-medium"
                      >
                        Mark Ready
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {orders.length === 0 && (
              <tr>
                <td
                  colSpan={6}
                  className="px-4 py-12 text-center text-neutral-500"
                >
                  No orders in queue.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
