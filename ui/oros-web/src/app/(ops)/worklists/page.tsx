"use client";

import { useState, useEffect, useCallback } from "react";
import {
  orosApi,
  type WorklistItem,
  type OrderType,
  type OrderPriority,
} from "@/lib/orosApi";

const ORDER_TYPES: OrderType[] = ["LAB", "IMAGING", "PHARMACY", "REFERRAL", "PROCEDURE", "SUPPLY"];

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

const STATUS_BADGE: Record<string, string> = {
  PENDING: "badge-status-placed",
  ACCEPTED: "badge-status-accepted",
  IN_PROGRESS: "badge-status-in-progress",
  COMPLETED: "badge-status-completed",
  REJECTED: "badge-status-rejected",
};

export default function WorklistsPage() {
  const [items, setItems] = useState<WorklistItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Filters
  const [activeTab, setActiveTab] = useState<string>("ALL");
  const [filterPriority, setFilterPriority] = useState<string>("");
  const [filterStatus, setFilterStatus] = useState<string>("");

  // Reject dialog
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const loadWorklists = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = {};
      if (activeTab !== "ALL") params.type = activeTab;
      if (filterPriority) params.priority = filterPriority;
      if (filterStatus) params.status = filterStatus;
      const data = await orosApi.getWorklists(params);
      setItems(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load worklists");
    } finally {
      setLoading(false);
    }
  }, [activeTab, filterPriority, filterStatus]);

  useEffect(() => {
    loadWorklists();
  }, [loadWorklists]);

  const handleAccept = async (id: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await orosApi.acceptWorklistItem(id);
      setSuccessMessage("Worklist item accepted.");
      await loadWorklists();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to accept item");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!rejectingId || !rejectReason.trim()) {
      setError("Rejection reason is required.");
      return;
    }

    setActionLoading(true);
    setError(null);
    try {
      await orosApi.rejectWorklistItem(rejectingId, { reason: rejectReason });
      setSuccessMessage("Worklist item rejected.");
      setRejectingId(null);
      setRejectReason("");
      await loadWorklists();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject item");
    } finally {
      setActionLoading(false);
    }
  };

  const tabs = ["ALL", ...ORDER_TYPES];

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
          <h1 className="text-2xl font-semibold text-neutral-900">Worklists</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Department worklists for order triage, acceptance, and processing.
          </p>
        </div>
        <button onClick={loadWorklists} className="btn-secondary">
          Refresh
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

      {/* Tabs */}
      <div className="flex gap-1 mb-4 border-b border-neutral-200">
        {tabs.map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab
                ? "border-indigo-600 text-indigo-700"
                : "border-transparent text-neutral-500 hover:text-neutral-700 hover:border-neutral-300"
            }`}
          >
            {tab === "ALL" ? "All" : tab.charAt(0) + tab.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {/* Filters */}
      <div className="flex gap-4 mb-4">
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
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="select-field w-40"
        >
          <option value="">All Statuses</option>
          <option value="PENDING">Pending</option>
          <option value="ACCEPTED">Accepted</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="COMPLETED">Completed</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>

      {/* Reject dialog */}
      {rejectingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Reject Worklist Item</h2>
          <div>
            <label htmlFor="rejectReason" className="block text-sm font-medium text-neutral-700 mb-1">
              Reason for rejection
            </label>
            <textarea
              id="rejectReason"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Provide a reason for rejection..."
              rows={3}
              className="input-field"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleReject} disabled={actionLoading} className="btn-danger">
              {actionLoading ? "Rejecting..." : "Reject"}
            </button>
            <button
              onClick={() => {
                setRejectingId(null);
                setRejectReason("");
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Worklist table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Order #</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Patient</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Priority</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Placed</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Items</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {items.map((item) => (
              <tr key={item.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs font-medium">
                  {item.orderNumber}
                </td>
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs">
                  {item.patientCpid}
                </td>
                <td className="px-4 py-3">
                  <span className={TYPE_BADGE[item.orderType]}>
                    {item.orderType}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={PRIORITY_BADGE[item.priority]}>
                    {item.priority}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <span className={STATUS_BADGE[item.status] || "badge bg-neutral-100 text-neutral-600"}>
                    {item.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(item.placedAt).toLocaleString()}
                </td>
                <td className="px-4 py-3 text-neutral-600">
                  {item.itemCount}
                </td>
                <td className="px-4 py-3">
                  <div className="flex gap-2">
                    {item.status === "PENDING" && (
                      <>
                        <button
                          onClick={() => handleAccept(item.id)}
                          disabled={actionLoading}
                          className="text-xs text-emerald-600 hover:text-emerald-800 font-medium"
                        >
                          Accept
                        </button>
                        <button
                          onClick={() => setRejectingId(item.id)}
                          disabled={actionLoading}
                          className="text-xs text-red-600 hover:text-red-800 font-medium"
                        >
                          Reject
                        </button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                  No worklist items found for the selected filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {items.length} item{items.length !== 1 ? "s" : ""} total
      </div>
    </div>
  );
}
