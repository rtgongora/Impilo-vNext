"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import {
  pharmacyApi,
  type DispenseOrderSummary,
  type DispensePriority,
  type DispenseStatus,
} from "@/lib/pharmacyApi";

const PRIORITY_BADGE: Record<DispensePriority, string> = {
  ROUTINE: "badge-priority-routine",
  URGENT: "badge-priority-urgent",
  STAT: "badge-priority-stat",
  ASAP: "badge-priority-asap",
};

const STATUS_BADGE: Record<DispenseStatus, string> = {
  PENDING: "badge-status-pending",
  ACCEPTED: "badge-status-accepted",
  PICKING: "badge-status-picking",
  PARTIAL: "badge-status-partial",
  COMPLETED: "badge-status-completed",
  BACKORDERED: "badge-status-backordered",
  REVERSED: "badge-status-reversed",
  CANCELLED: "badge-status-cancelled",
};

const STATUS_TABS: { value: string; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "ACCEPTED", label: "Accepted" },
  { value: "PICKING", label: "Picking" },
  { value: "PARTIAL", label: "Partial" },
  { value: "COMPLETED", label: "Completed" },
  { value: "BACKORDERED", label: "Backordered" },
];

export default function WorklistsPage() {
  const router = useRouter();
  const [items, setItems] = useState<DispenseOrderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [activeTab, setActiveTab] = useState<string>("ALL");
  const [filterPriority, setFilterPriority] = useState<string>("");
  const [filterFacility, setFilterFacility] = useState<string>("");

  const loadWorklists = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = {};
      if (activeTab !== "ALL") params.status = activeTab;
      if (filterPriority) params.priority = filterPriority;
      const data = await pharmacyApi.getWorklist(filterFacility, params);
      setItems(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load worklists");
    } finally {
      setLoading(false);
    }
  }, [activeTab, filterPriority, filterFacility]);

  useEffect(() => {
    loadWorklists();
  }, [loadWorklists]);

  const handleRowClick = (id: string) => {
    router.push(`/dispense?id=${encodeURIComponent(id)}`);
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
          <h1 className="text-2xl font-semibold text-neutral-900">Pharmacy Worklist</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Dispensing worklist for pharmacy operations. Click a row to open the dispense detail.
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

      {/* Status tabs */}
      <div className="flex gap-1 mb-4 border-b border-neutral-200">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setActiveTab(tab.value)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === tab.value
                ? "border-emerald-600 text-emerald-700"
                : "border-transparent text-neutral-500 hover:text-neutral-700 hover:border-neutral-300"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Filters */}
      <div className="flex gap-4 mb-4">
        <input
          type="text"
          value={filterFacility}
          onChange={(e) => setFilterFacility(e.target.value)}
          placeholder="Filter by Facility ID..."
          className="input-field w-64"
        />
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

      {/* Worklist table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Order ID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Patient CPID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Priority</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">OROS Ref</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Items</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Pharmacist</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Created</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {items.map((item) => (
              <tr
                key={item.id}
                onClick={() => handleRowClick(item.id)}
                className="hover:bg-neutral-50 transition-colors cursor-pointer"
              >
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs font-medium">
                  {item.id.substring(0, 8)}...
                </td>
                <td className="px-4 py-3 text-neutral-900 font-mono text-xs">
                  {item.patientCpid}
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
                <td className="px-4 py-3 font-mono text-xs text-neutral-600">
                  {item.orosOrderNumber}
                </td>
                <td className="px-4 py-3 text-neutral-600">
                  {item.dispensedCount}/{item.itemCount}
                </td>
                <td className="px-4 py-3 text-neutral-600 text-xs">
                  {item.pharmacistName || "--"}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(item.createdAt).toLocaleString()}
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                  No dispense orders found for the selected filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {items.length} order{items.length !== 1 ? "s" : ""} total
      </div>
    </div>
  );
}
