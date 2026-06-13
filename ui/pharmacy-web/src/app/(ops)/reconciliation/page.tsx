"use client";

import { useState, useEffect, useCallback } from "react";
import {
  pharmacyApi,
  type ReconcileSummary,
  type ReconcileStockStatus,
} from "@/lib/pharmacyApi";

const STATUS_BADGE: Record<ReconcileStockStatus, string> = {
  PENDING: "badge bg-yellow-100 text-yellow-800",
  RESOLVED: "badge bg-emerald-100 text-primary-hover",
  EXPIRED: "badge bg-neutral-100 text-neutral-500",
};

const CONFIDENCE_COLOR = (score: number): string => {
  if (score >= 0.8) return "text-primary-hover bg-success-soft";
  if (score >= 0.5) return "text-yellow-700 bg-yellow-50";
  return "text-danger bg-danger-soft";
};

const VARIANCE_COLOR = (variance: number): string => {
  if (variance === 0) return "text-primary-hover";
  if (Math.abs(variance) <= 5) return "text-yellow-700";
  return "text-danger";
};

export default function ReconciliationPage() {
  const [items, setItems] = useState<ReconcileSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Pagination
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 20;

  // Resolve dialog
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [resolveNotes, setResolveNotes] = useState("");
  const [resolveAction, setResolveAction] = useState<"ACCEPT_INTERNAL" | "ACCEPT_EXTERNAL" | "MANUAL_ADJUST">("ACCEPT_INTERNAL");
  const [adjustedQty, setAdjustedQty] = useState<number>(0);

  const loadPending = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await pharmacyApi.getReconcilePending(page, pageSize);
      setItems(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reconciliation items");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    loadPending();
  }, [loadPending]);

  const handleResolve = async () => {
    if (!resolvingId || !resolveNotes.trim()) {
      setError("Resolution notes are required.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await pharmacyApi.resolveReconcile(resolvingId, {
        notes: resolveNotes,
        adjustmentAction: resolveAction,
        adjustedQuantity: resolveAction === "MANUAL_ADJUST" ? adjustedQty : undefined,
      });
      setSuccessMessage("Reconciliation item resolved.");
      setResolvingId(null);
      setResolveNotes("");
      await loadPending();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resolve reconciliation item");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3].map((i) => (
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
          <h1 className="text-2xl font-semibold text-neutral-900">Stock Reconciliation</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Reconcile stock variances between internal inventory and external eLMIS data.
          </p>
        </div>
        <button onClick={loadPending} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-success-soft border border-success/25 text-sm text-primary-hover">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-primary hover:text-primary-hover font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Resolve dialog */}
      {resolvingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Resolve Variance</h2>
          <div>
            <label htmlFor="resolveAction" className="block text-sm font-medium text-neutral-700 mb-1">
              Adjustment Action
            </label>
            <select
              id="resolveAction"
              value={resolveAction}
              onChange={(e) => setResolveAction(e.target.value as typeof resolveAction)}
              className="select-field w-64"
            >
              <option value="ACCEPT_INTERNAL">Accept Internal Count</option>
              <option value="ACCEPT_EXTERNAL">Accept External (eLMIS) Count</option>
              <option value="MANUAL_ADJUST">Manual Adjustment</option>
            </select>
          </div>
          {resolveAction === "MANUAL_ADJUST" && (
            <div>
              <label htmlFor="adjustedQty" className="block text-sm font-medium text-neutral-700 mb-1">
                Adjusted Quantity
              </label>
              <input
                id="adjustedQty"
                type="number"
                min={0}
                value={adjustedQty}
                onChange={(e) => setAdjustedQty(parseInt(e.target.value) || 0)}
                className="input-field w-32"
              />
            </div>
          )}
          <div>
            <label htmlFor="resolveNotes" className="block text-sm font-medium text-neutral-700 mb-1">
              Resolution Notes
            </label>
            <textarea
              id="resolveNotes"
              value={resolveNotes}
              onChange={(e) => setResolveNotes(e.target.value)}
              placeholder="Explain the variance and resolution..."
              rows={3}
              className="input-field"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleResolve} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Resolving..." : "Resolve"}
            </button>
            <button
              onClick={() => {
                setResolvingId(null);
                setResolveNotes("");
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Reconciliation table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Item Code</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Item Name</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Internal Qty</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">External Qty</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Variance</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Confidence</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Source</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Created</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {items.map((item) => (
              <tr key={item.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                  {item.itemCode}
                </td>
                <td className="px-4 py-3 text-neutral-900">
                  {item.itemName}
                </td>
                <td className="px-4 py-3 text-right text-neutral-900 font-medium">
                  {item.internalQuantity}
                </td>
                <td className="px-4 py-3 text-right text-neutral-900 font-medium">
                  {item.externalQuantity}
                </td>
                <td className={`px-4 py-3 text-right font-medium ${VARIANCE_COLOR(item.variance)}`}>
                  {item.variance > 0 ? "+" : ""}
                  {item.variance}
                </td>
                <td className="px-4 py-3">
                  <span className={`badge ${CONFIDENCE_COLOR(item.confidenceScore)}`}>
                    {(item.confidenceScore * 100).toFixed(0)}%
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-neutral-600">
                  {item.source}
                </td>
                <td className="px-4 py-3">
                  <span className={STATUS_BADGE[item.status] || "badge bg-neutral-100 text-neutral-600"}>
                    {item.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(item.createdAt).toLocaleString()}
                </td>
                <td className="px-4 py-3">
                  {item.status === "PENDING" && (
                    <button
                      onClick={() => setResolvingId(item.id)}
                      className="text-xs text-primary hover:text-primary-hover font-medium"
                    >
                      Resolve
                    </button>
                  )}
                  {item.status === "RESOLVED" && item.resolutionNotes && (
                    <span className="text-xs text-neutral-500" title={item.resolutionNotes}>
                      {item.resolutionNotes.substring(0, 30)}
                      {item.resolutionNotes.length > 30 ? "..." : ""}
                    </span>
                  )}
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={10} className="px-4 py-12 text-center text-neutral-500">
                  No pending stock reconciliation items.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="mt-4 flex items-center justify-between">
        <div className="text-xs text-neutral-400">
          {totalElements} item{totalElements !== 1 ? "s" : ""} total
          {totalPages > 1 && ` | Page ${page + 1} of ${totalPages}`}
        </div>
        {totalPages > 1 && (
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="btn-secondary text-xs"
            >
              Previous
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="btn-secondary text-xs"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
