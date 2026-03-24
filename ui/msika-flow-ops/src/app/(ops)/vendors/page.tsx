"use client";

import { useState, useEffect, useCallback } from "react";
import { opsApi, type Vendor, type PagedResponse } from "@/lib/opsApi";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-700",
  PENDING: "bg-amber-100 text-amber-700",
  SUSPENDED: "bg-red-100 text-red-700",
  INACTIVE: "bg-neutral-100 text-neutral-600",
};

export default function VendorsPage() {
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [actionLoading, setActionLoading] = useState(false);
  const [suspendingId, setSuspendingId] = useState<string | null>(null);
  const [suspendReason, setSuspendReason] = useState("");

  const loadVendors = useCallback(async (p = 0) => {
    setLoading(true);
    setError(null);
    try {
      const data: PagedResponse<Vendor> = await opsApi.listVendors({
        status: statusFilter || undefined,
        page: p,
        size: 20,
      });
      setVendors(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
      setPage(p);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load vendors");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadVendors(0);
  }, [loadVendors]);

  const handleSuspend = async () => {
    if (!suspendingId || !suspendReason.trim()) return;
    setActionLoading(true);
    setError(null);
    try {
      await opsApi.suspendVendor(suspendingId, suspendReason.trim());
      setSuspendingId(null);
      setSuspendReason("");
      await loadVendors(page);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to suspend vendor");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReinstate = async (vendorId: string) => {
    setActionLoading(true);
    setError(null);
    try {
      await opsApi.reinstateVendor(vendorId);
      await loadVendors(page);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reinstate vendor");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Vendor Management</h1>
          <p className="text-sm text-neutral-500 mt-1">
            View, approve, suspend, and audit vendor profiles.
            {totalElements > 0 && (
              <span className="ml-2 text-neutral-400">({totalElements} total)</span>
            )}
          </p>
        </div>
        <button onClick={() => loadVendors(page)} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="flex gap-3 mb-6">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="input-field w-44"
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="PENDING">Pending</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {suspendingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold">Suspend Vendor</h2>
          <textarea
            value={suspendReason}
            onChange={(e) => setSuspendReason(e.target.value)}
            placeholder="Reason for suspension (required)..."
            rows={3}
            className="input-field"
          />
          <div className="flex gap-2">
            <button
              onClick={handleSuspend}
              disabled={actionLoading || !suspendReason.trim()}
              className="btn-danger"
            >
              {actionLoading ? "Suspending..." : "Confirm Suspend"}
            </button>
            <button
              onClick={() => { setSuspendingId(null); setSuspendReason(""); }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Vendor ID</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Tenant</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Registered</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  <div className="animate-pulse">Loading vendors...</div>
                </td>
              </tr>
            ) : vendors.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  No vendors found.
                </td>
              </tr>
            ) : (
              vendors.map((vendor) => (
                <tr key={vendor.vendorId} className="hover:bg-neutral-50">
                  <td className="px-4 py-3 font-mono text-xs">{vendor.vendorId.substring(0, 12)}...</td>
                  <td className="px-4 py-3 font-medium">{vendor.name}</td>
                  <td className="px-4 py-3">{vendor.type}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[vendor.status] ?? "bg-neutral-100"}`}>
                      {vendor.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs">{vendor.tenantId}</td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {new Date(vendor.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      {vendor.status === "ACTIVE" && (
                        <button
                          onClick={() => setSuspendingId(vendor.vendorId)}
                          disabled={actionLoading}
                          className="text-xs text-red-600 hover:text-red-800 font-medium"
                        >
                          Suspend
                        </button>
                      )}
                      {vendor.status === "SUSPENDED" && (
                        <button
                          onClick={() => handleReinstate(vendor.vendorId)}
                          disabled={actionLoading}
                          className="text-xs text-emerald-600 hover:text-emerald-800 font-medium"
                        >
                          Reinstate
                        </button>
                      )}
                    </div>
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
            onClick={() => loadVendors(page - 1)}
            disabled={page === 0 || loading}
            className="btn-secondary text-xs px-3 py-1 disabled:opacity-50"
          >
            Previous
          </button>
          <span className="text-sm text-neutral-500">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => loadVendors(page + 1)}
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
