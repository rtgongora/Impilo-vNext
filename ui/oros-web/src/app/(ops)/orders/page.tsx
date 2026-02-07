"use client";

import { useState, useEffect, useCallback } from "react";
import {
  orosApi,
  type Order,
  type OrderType,
  type OrderStatus,
  type OrderPriority,
  type PlaceOrderRequest,
  type OrderItemRequest,
} from "@/lib/orosApi";

const ORDER_TYPES: OrderType[] = ["LAB", "IMAGING", "PHARMACY", "REFERRAL", "PROCEDURE", "SUPPLY"];

const STATUS_BADGE: Record<OrderStatus, string> = {
  PLACED: "badge-status-placed",
  ACCEPTED: "badge-status-accepted",
  IN_PROGRESS: "badge-status-in-progress",
  COMPLETED: "badge-status-completed",
  CANCELLED: "badge-status-cancelled",
  REJECTED: "badge-status-rejected",
  EXPIRED: "badge-status-expired",
};

const PRIORITY_BADGE: Record<OrderPriority, string> = {
  ROUTINE: "badge-priority-routine",
  URGENT: "badge-priority-urgent",
  STAT: "badge-priority-stat",
  ASAP: "badge-priority-asap",
};

const TYPE_BADGE: Record<OrderType, string> = {
  LAB: "badge-type-lab",
  IMAGING: "badge-type-imaging",
  PHARMACY: "badge-type-pharmacy",
  REFERRAL: "badge-type-referral",
  PROCEDURE: "badge bg-amber-100 text-amber-800",
  SUPPLY: "badge bg-neutral-100 text-neutral-700",
};

const EMPTY_ITEM: OrderItemRequest = {
  catalogCode: "",
  name: "",
  quantity: 1,
};

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Filters
  const [filterType, setFilterType] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [filterPriority, setFilterPriority] = useState<string>("");
  const [searchQuery, setSearchQuery] = useState<string>("");

  // Create order form
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createData, setCreateData] = useState<PlaceOrderRequest>({
    type: "LAB",
    priority: "ROUTINE",
    patientCpid: "",
    facilityId: "",
    items: [{ ...EMPTY_ITEM }],
  });

  // Cancel dialog
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState("");

  const loadOrders = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = {};
      if (filterType) params.type = filterType;
      if (filterStatus) params.status = filterStatus;
      if (filterPriority) params.priority = filterPriority;
      if (searchQuery) params.q = searchQuery;
      const data = await orosApi.listOrders(params);
      setOrders(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load orders");
    } finally {
      setLoading(false);
    }
  }, [filterType, filterStatus, filterPriority, searchQuery]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleCreate = async () => {
    if (!createData.patientCpid.trim()) {
      setError("Patient CPID is required.");
      return;
    }
    if (!createData.facilityId.trim()) {
      setError("Facility ID is required.");
      return;
    }
    if (createData.items.length === 0 || !createData.items[0].catalogCode.trim()) {
      setError("At least one order item with a catalog code is required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await orosApi.placeOrder(createData);
      setSuccessMessage("Order placed successfully.");
      setShowCreateForm(false);
      setCreateData({
        type: "LAB",
        priority: "ROUTINE",
        patientCpid: "",
        facilityId: "",
        items: [{ ...EMPTY_ITEM }],
      });
      await loadOrders();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to place order");
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancel = async () => {
    if (!cancellingId || !cancelReason.trim()) {
      setError("Cancellation reason is required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await orosApi.cancelOrder(cancellingId, { reason: cancelReason });
      setSuccessMessage("Order cancelled.");
      setCancellingId(null);
      setCancelReason("");
      await loadOrders();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel order");
    } finally {
      setActionLoading(false);
    }
  };

  const addItem = () => {
    setCreateData({
      ...createData,
      items: [...createData.items, { ...EMPTY_ITEM }],
    });
  };

  const removeItem = (index: number) => {
    setCreateData({
      ...createData,
      items: createData.items.filter((_, i) => i !== index),
    });
  };

  const updateItem = (index: number, field: keyof OrderItemRequest, value: string | number) => {
    const updated = [...createData.items];
    updated[index] = { ...updated[index], [field]: value };
    setCreateData({ ...createData, items: updated });
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="h-10 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Orders</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Search, create, and manage clinical orders across all departments.
          </p>
        </div>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="btn-primary"
        >
          Place Order
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-emerald-600 hover:text-emerald-800 font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Create order form */}
      {showCreateForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Place New Order</h2>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="orderType" className="block text-sm font-medium text-neutral-700 mb-1">
                Order Type
              </label>
              <select
                id="orderType"
                value={createData.type}
                onChange={(e) => setCreateData({ ...createData, type: e.target.value as OrderType })}
                className="select-field"
              >
                {ORDER_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.charAt(0) + t.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="orderPriority" className="block text-sm font-medium text-neutral-700 mb-1">
                Priority
              </label>
              <select
                id="orderPriority"
                value={createData.priority}
                onChange={(e) => setCreateData({ ...createData, priority: e.target.value as OrderPriority })}
                className="select-field"
              >
                <option value="ROUTINE">Routine</option>
                <option value="URGENT">Urgent</option>
                <option value="STAT">STAT</option>
                <option value="ASAP">ASAP</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="patientCpid" className="block text-sm font-medium text-neutral-700 mb-1">
                Patient CPID
              </label>
              <input
                id="patientCpid"
                type="text"
                value={createData.patientCpid}
                onChange={(e) => setCreateData({ ...createData, patientCpid: e.target.value })}
                placeholder="CPID-2024-00012345"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="facilityId" className="block text-sm font-medium text-neutral-700 mb-1">
                Facility ID
              </label>
              <input
                id="facilityId"
                type="text"
                value={createData.facilityId}
                onChange={(e) => setCreateData({ ...createData, facilityId: e.target.value })}
                placeholder="UUID of originating facility"
                className="input-field"
              />
            </div>
          </div>

          <div>
            <label htmlFor="clinicalNotes" className="block text-sm font-medium text-neutral-700 mb-1">
              Clinical Notes
            </label>
            <textarea
              id="clinicalNotes"
              value={createData.clinicalNotes || ""}
              onChange={(e) => setCreateData({ ...createData, clinicalNotes: e.target.value })}
              placeholder="Clinical indications and notes..."
              rows={2}
              className="input-field"
            />
          </div>

          {/* Order items */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="block text-sm font-medium text-neutral-700">Order Items</label>
              <button onClick={addItem} className="text-xs text-indigo-600 hover:text-indigo-800 font-medium">
                + Add Item
              </button>
            </div>
            {createData.items.map((item, index) => (
              <div key={index} className="grid grid-cols-4 gap-3 mb-2">
                <input
                  type="text"
                  value={item.catalogCode}
                  onChange={(e) => updateItem(index, "catalogCode", e.target.value)}
                  placeholder="Catalog code (e.g., 2160-0)"
                  className="input-field"
                />
                <input
                  type="text"
                  value={item.name}
                  onChange={(e) => updateItem(index, "name", e.target.value)}
                  placeholder="Item name"
                  className="input-field"
                />
                <input
                  type="number"
                  value={item.quantity || 1}
                  onChange={(e) => updateItem(index, "quantity", parseInt(e.target.value) || 1)}
                  min={1}
                  className="input-field"
                />
                {createData.items.length > 1 && (
                  <button
                    onClick={() => removeItem(index)}
                    className="text-xs text-red-600 hover:text-red-800 font-medium self-center"
                  >
                    Remove
                  </button>
                )}
              </div>
            ))}
          </div>

          <div className="flex gap-2">
            <button onClick={handleCreate} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Placing..." : "Place Order"}
            </button>
            <button onClick={() => setShowCreateForm(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Cancel dialog */}
      {cancellingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Cancel Order</h2>
          <div>
            <label htmlFor="cancelReason" className="block text-sm font-medium text-neutral-700 mb-1">
              Cancellation Reason
            </label>
            <textarea
              id="cancelReason"
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="Provide a reason for cancellation..."
              rows={3}
              className="input-field"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleCancel} disabled={actionLoading} className="btn-danger">
              {actionLoading ? "Cancelling..." : "Cancel Order"}
            </button>
            <button
              onClick={() => {
                setCancellingId(null);
                setCancelReason("");
              }}
              className="btn-secondary"
            >
              Go Back
            </button>
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="flex gap-4 mb-4">
        <div className="flex-1">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by order number, patient CPID, or notes..."
            className="input-field"
          />
        </div>
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value)}
          className="select-field w-40"
        >
          <option value="">All Types</option>
          {ORDER_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.charAt(0) + t.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="select-field w-40"
        >
          <option value="">All Statuses</option>
          <option value="PLACED">Placed</option>
          <option value="ACCEPTED">Accepted</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
          <option value="REJECTED">Rejected</option>
        </select>
        <select
          value={filterPriority}
          onChange={(e) => setFilterPriority(e.target.value)}
          className="select-field w-40"
        >
          <option value="">All Priorities</option>
          <option value="ROUTINE">Routine</option>
          <option value="URGENT">Urgent</option>
          <option value="STAT">STAT</option>
          <option value="ASAP">ASAP</option>
        </select>
      </div>

      {/* Orders table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Order #</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Patient</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Priority</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Results</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Placed</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {orders.map((order) => (
              <tr key={order.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs font-medium">
                  {order.orderNumber}
                  {order.hasCriticalResult && (
                    <span className="ml-2 badge-critical">CRITICAL</span>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs">
                  {order.patientCpid}
                </td>
                <td className="px-4 py-3">
                  <span className={TYPE_BADGE[order.type]}>
                    {order.type}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={PRIORITY_BADGE[order.priority]}>
                    {order.priority}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={STATUS_BADGE[order.status]}>
                    {order.status.replace("_", " ")}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-600">
                  {order.resultCount}
                  {order.acknowledged && (
                    <span className="ml-1 text-emerald-600 text-xs" title="Acknowledged">
                      ACK
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(order.placedAt).toLocaleString()}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    {(order.status === "PLACED" ||
                      order.status === "ACCEPTED" ||
                      order.status === "IN_PROGRESS") && (
                      <button
                        onClick={() => setCancellingId(order.id)}
                        disabled={actionLoading}
                        className="text-xs text-red-600 hover:text-red-800 font-medium"
                      >
                        Cancel
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {orders.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                  No orders found. Place a new order or adjust filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {orders.length} order{orders.length !== 1 ? "s" : ""} total
      </div>
    </div>
  );
}
