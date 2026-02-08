"use client";

import { useState, useEffect, useCallback } from "react";
import { msikaFlowApi, type Order } from "@/lib/msikaFlowApi";

const STATUS_BADGE: Record<string, string> = {
  CREATED: "badge-created",
  VALIDATED: "badge-validated",
  PRICED: "badge-priced",
  PAYMENT_PENDING: "badge bg-yellow-100 text-yellow-800",
  PAID: "badge-paid",
  ROUTED: "badge bg-blue-100 text-blue-800",
  ACCEPTED: "badge bg-indigo-100 text-indigo-800",
  IN_PROGRESS: "badge bg-yellow-100 text-yellow-800",
  READY_FOR_PICKUP: "badge-ready",
  COLLECTED: "badge-collected",
  COMPLETED: "badge-completed",
  CANCELLED: "badge-cancelled",
  FAILED: "badge-failed",
};

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);

  const loadOrders = useCallback(async () => {
    // In production, use a list endpoint; here we show empty state
    setLoading(false);
  }, []);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const viewOrder = async (orderId: string) => {
    try {
      const order = await msikaFlowApi.getOrder(orderId);
      setSelectedOrder(order);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load order");
    }
  };

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-1">My Orders</h1>
      <p className="text-sm text-neutral-500 mb-6">Track your health marketplace orders and pickups.</p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">{error}</div>
      )}

      {selectedOrder ? (
        <div className="card p-6">
          <div className="flex justify-between items-start mb-4">
            <div>
              <h2 className="text-lg font-semibold">Order {selectedOrder.orderId}</h2>
              <p className="text-xs text-neutral-500 mt-1">
                {selectedOrder.orderType} — Created {new Date(selectedOrder.createdAt).toLocaleString()}
              </p>
            </div>
            <span className={STATUS_BADGE[selectedOrder.status] || "badge bg-neutral-100 text-neutral-600"}>
              {selectedOrder.status}
            </span>
          </div>

          {selectedOrder.lines && selectedOrder.lines.length > 0 && (
            <div className="mb-4">
              <h3 className="text-sm font-medium text-neutral-700 mb-2">Items</h3>
              <div className="space-y-1">
                {selectedOrder.lines.map((line) => (
                  <div key={line.lineId} className="flex justify-between text-sm py-1 border-b border-neutral-50">
                    <span>{line.msikaCoreCode} x{line.qty}</span>
                    <span className="font-medium">ZWG {line.lineTotal}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="flex justify-between items-center pt-3 border-t border-neutral-200">
            <span className="font-semibold">Total: ZWG {selectedOrder.amountTotal}</span>
            <button onClick={() => setSelectedOrder(null)} className="btn-secondary">Back to List</button>
          </div>
        </div>
      ) : (
        <div className="card p-12 text-center">
          <p className="text-neutral-500">No orders yet. Browse the marketplace to place your first order.</p>
        </div>
      )}
    </div>
  );
}
