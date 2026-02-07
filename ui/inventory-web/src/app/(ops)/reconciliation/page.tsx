"use client";

import { useState, useEffect, useCallback } from "react";
import {
  inventoryApi,
  type ReconcileItem,
  type ReconcileStatus,
  type AdjustmentAction,
  type PagedResponse,
} from "@/lib/inventoryApi";

const STATUS_BADGE: Record<ReconcileStatus, string> = {
  PENDING: "badge-status-pending",
  RESOLVED: "badge-status-resolved",
  EXPIRED: "badge-status-cancelled",
};

const ACTION_LABELS: Record<AdjustmentAction, string> = {
  ACCEPT_INTERNAL: "Accept Internal Count",
  ACCEPT_EXTERNAL: "Accept External (eLMIS) Count",
  MANUAL_ADJUST: "Manual Adjustment",
};

function confidenceBadge(score: number): string {
  if (score >= 0.9) return "badge bg-emerald-100 text-emerald-800";
  if (score >= 0.7) return "badge bg-amber-100 text-amber-800";
  return "badge bg-red-100 text-red-800";
}

export default function ReconciliationPage() {
  const [data, setData] = useState<PagedResponse<ReconcileItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Resolve dialog
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [resolveAction, setResolveAction] = useState<AdjustmentAction>("ACCEPT_INTERNAL");
  const [resolveNotes, setResolveNotes] = useState("");
  const [resolveAdjustedQty, setResolveAdjustedQty] = useState<number>(0);

  const loadPending = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const result = await inventoryApi.getReconcilePending({ size: "50" });
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reconciliation queue");
    } finally {
      setLoading(false);
    }
  }, []);

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
      await inventoryApi.resolveReconcile(resolvingId, {
        action: resolveAction,
        adjustedQuantity: resolveAction === "MANUAL_ADJUST" ? resolveAdjustedQty : undefined,
        notes: resolveNotes,
      });
      setSuccessMessage("Reconciliation entry resolved.");
      setResolvingId(null);
      setResolveNotes("");
      setResolveAction("ACCEPT_INTERNAL");
      setResolveAdjustedQty(0);
      await loadPending();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resolve entry");
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
          <h1 className="text-2xl font-semibold text-neutral-900">Reconciliation</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Stock variances between internal inventory and external eLMIS systems.
          </p>
        </div>
        <button onClick={loadPending} className="btn-secondary">
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

      {/* Resolve dialog */}
      {resolvingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Resolve Variance</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label htmlFor="res-action" className="block text-sm font-medium text-neutral-700 mb-1">
                Resolution Action
              </label>
              <select
                id="res-action"
                value={resolveAction}
                onChange={(e) => setResolveAction(e.target.value as AdjustmentAction)}
                className="select-field"
              >
                {(Object.keys(ACTION_LABELS) as AdjustmentAction[]).map((action) => (
                  <option key={action} value={action}>
                    {ACTION_LABELS[action]}
                  </option>
                ))}
              </select>
            </div>
            {resolveAction === "MANUAL_ADJUST" && (
              <div>
                <label htmlFor="res-qty" className="block text-sm font-medium text-neutral-700 mb-1">
                  Adjusted Quantity
                </label>
                <input
                  id="res-qty"
                  type="number"
                  min={0}
                  value={resolveAdjustedQty}
                  onChange={(e) => setResolveAdjustedQty(parseInt(e.target.value) || 0)}
                  className="input-field"
                />
              </div>
            )}
          </div>
          <div>
            <label htmlFor="res-notes" className="block text-sm font-medium text-neutral-700 mb-1">
              Resolution Notes
            </label>
            <textarea
              id="res-notes"
              value={resolveNotes}
              onChange={(e) => setResolveNotes(e.target.value)}
              placeholder="Explain the resolution..."
              rows={3}
              className="input-field"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleResolve} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Resolving..." : "Resolve"}
            </button>
            <button
              onClick={() => { setResolvingId(null); setResolveNotes(""); }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Reconciliation queue table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Item Code</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Batch</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Internal</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">External</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Variance</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Confidence</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Source</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {data?.content.map((item) => (
              <tr key={item.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                  {item.itemCode}
                </td>
                <td className="px-4 py-3 text-neutral-900">{item.itemName}</td>
                <td className="px-4 py-3 text-neutral-600 font-mono text-xs">
                  {item.batchNumber || "-"}
                </td>
                <td className="px-4 py-3 text-right text-neutral-600">
                  {item.internalQuantity}
                </td>
                <td className="px-4 py-3 text-right text-neutral-600">
                  {item.externalQuantity}
                </td>
                <td className={`px-4 py-3 text-right font-medium ${
                  item.variance === 0 ? "text-emerald-600" : "text-red-600"
                }`}>
                  {item.variance > 0 ? `+${item.variance}` : item.variance}
                </td>
                <td className="px-4 py-3">
                  <span className={confidenceBadge(item.confidenceScore)}>
                    {(item.confidenceScore * 100).toFixed(0)}%
                  </span>
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {item.source}
                </td>
                <td className="px-4 py-3">
                  <span className={STATUS_BADGE[item.status]}>
                    {item.status}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {item.status === "PENDING" && (
                    <button
                      onClick={() => setResolvingId(item.id)}
                      disabled={actionLoading}
                      className="text-xs text-amber-600 hover:text-amber-800 font-medium"
                    >
                      Resolve
                    </button>
                  )}
                  {item.status === "RESOLVED" && (
                    <span className="text-xs text-neutral-400">
                      {item.resolutionNotes ? item.resolutionNotes.substring(0, 30) + "..." : "Resolved"}
                    </span>
                  )}
                </td>
              </tr>
            ))}
            {(!data || data.content.length === 0) && (
              <tr>
                <td colSpan={10} className="px-4 py-12 text-center text-neutral-500">
                  No reconciliation entries found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        Showing {data?.content.length ?? 0} of {data?.totalElements ?? 0} entr{(data?.totalElements ?? 0) !== 1 ? "ies" : "y"}
      </div>
    </div>
  );
}
