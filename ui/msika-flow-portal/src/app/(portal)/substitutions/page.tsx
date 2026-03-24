"use client";

import { useState, useEffect, useCallback } from "react";
import { msikaFlowApi, type SubstitutionRequest } from "@/lib/msikaFlowApi";

const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-amber-100 text-amber-700",
  APPROVED: "bg-emerald-100 text-emerald-700",
  REJECTED: "bg-red-100 text-red-700",
};

export default function SubstitutionsPage() {
  const [substitutions, setSubstitutions] = useState<SubstitutionRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState("PENDING");
  const [actionLoading, setActionLoading] = useState(false);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const loadSubstitutions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await msikaFlowApi.listSubstitutions(statusFilter || undefined);
      setSubstitutions(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load substitution requests");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadSubstitutions();
  }, [loadSubstitutions]);

  const handleApprove = async (sub: SubstitutionRequest) => {
    setActionLoading(true);
    setError(null);
    try {
      await msikaFlowApi.approveSubstitution(sub.orderId, sub.originalLineId);
      await loadSubstitutions();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve substitution");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    const sub = substitutions.find((s) => s.id === rejectingId);
    if (!sub) return;
    setActionLoading(true);
    setError(null);
    try {
      await msikaFlowApi.rejectSubstitution(sub.orderId, sub.originalLineId, rejectReason || undefined);
      setRejectingId(null);
      setRejectReason("");
      await loadSubstitutions();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject substitution");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold mb-1">Substitution Approvals</h1>
          <p className="text-sm text-neutral-500">
            Review and approve medication substitutions proposed by your pharmacy.
          </p>
        </div>
        <button onClick={loadSubstitutions} className="btn-secondary">
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
          <option value="PENDING">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="">All</option>
        </select>
      </div>

      {rejectingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold">Reject Substitution</h2>
          <textarea
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="Reason for rejection (optional)..."
            rows={3}
            className="input-field"
          />
          <div className="flex gap-2">
            <button onClick={handleReject} disabled={actionLoading} className="btn-danger">
              {actionLoading ? "Rejecting..." : "Confirm Reject"}
            </button>
            <button
              onClick={() => { setRejectingId(null); setRejectReason(""); }}
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
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Original</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Proposed</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Reason</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Proposed By</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Date</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  <div className="animate-pulse">Loading substitution requests...</div>
                </td>
              </tr>
            ) : substitutions.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  No {statusFilter ? statusFilter.toLowerCase() : ""} substitution requests.
                </td>
              </tr>
            ) : (
              substitutions.map((sub) => (
                <tr key={sub.id} className="hover:bg-neutral-50">
                  <td className="px-4 py-3">
                    <div className="text-sm font-medium">{sub.originalName}</div>
                    <div className="text-xs text-neutral-400 font-mono">{sub.originalCode}</div>
                  </td>
                  <td className="px-4 py-3">
                    <div className="text-sm font-medium">{sub.proposedName}</div>
                    <div className="text-xs text-neutral-400 font-mono">{sub.proposedCode}</div>
                  </td>
                  <td className="px-4 py-3 text-sm text-neutral-600 max-w-xs truncate">
                    {sub.reason}
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500">{sub.proposedBy}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[sub.status] ?? "bg-neutral-100"}`}>
                      {sub.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {new Date(sub.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3">
                    {sub.status === "PENDING" && (
                      <div className="flex gap-2">
                        <button
                          onClick={() => handleApprove(sub)}
                          disabled={actionLoading}
                          className="text-xs text-emerald-600 hover:text-emerald-800 font-medium"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => setRejectingId(sub.id)}
                          disabled={actionLoading}
                          className="text-xs text-red-600 hover:text-red-800 font-medium"
                        >
                          Reject
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
