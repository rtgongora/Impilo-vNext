"use client";

import { useState, useEffect, useCallback } from "react";
import {
  orosApi,
  type ReconcileItem,
  type OrderType,
} from "@/lib/orosApi";

const CONFIDENCE_COLOR = (score: number): string => {
  if (score >= 0.8) return "text-emerald-700 bg-emerald-50";
  if (score >= 0.5) return "text-yellow-700 bg-yellow-50";
  return "text-red-700 bg-red-50";
};

const STATUS_BADGE: Record<string, string> = {
  PENDING: "badge bg-yellow-100 text-yellow-800",
  MATCHED: "badge bg-emerald-100 text-emerald-800",
  RESOLVED: "badge bg-blue-100 text-blue-800",
  EXPIRED: "badge bg-neutral-100 text-neutral-500",
};

const ORDER_TYPES: OrderType[] = ["LAB", "IMAGING", "PHARMACY", "REFERRAL", "PROCEDURE", "SUPPLY"];

export default function ReconciliationPage() {
  const [items, setItems] = useState<ReconcileItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Filters
  const [filterType, setFilterType] = useState<string>("");

  // Match dialog
  const [matchingId, setMatchingId] = useState<string | null>(null);
  const [matchOrderId, setMatchOrderId] = useState("");
  const [matchNotes, setMatchNotes] = useState("");

  // Resolve dialog
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [resolveType, setResolveType] = useState<"DUPLICATE" | "INVALID" | "ORPHANED" | "OTHER">("DUPLICATE");
  const [resolveNotes, setResolveNotes] = useState("");

  // Expanded candidate view
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const loadPending = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = {};
      if (filterType) params.type = filterType;
      const data = await orosApi.getPendingReconciliations(params);
      setItems(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reconciliation items");
    } finally {
      setLoading(false);
    }
  }, [filterType]);

  useEffect(() => {
    loadPending();
  }, [loadPending]);

  const handleMatch = async () => {
    if (!matchingId || !matchOrderId.trim()) {
      setError("Order ID is required for matching.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await orosApi.matchReconciliation(matchingId, {
        orderId: matchOrderId,
        notes: matchNotes || undefined,
      });
      setSuccessMessage("Reconciliation item matched successfully.");
      setMatchingId(null);
      setMatchOrderId("");
      setMatchNotes("");
      await loadPending();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to match reconciliation item");
    } finally {
      setActionLoading(false);
    }
  };

  const handleResolve = async () => {
    if (!resolvingId) return;

    setActionLoading(true);
    setError(null);

    try {
      await orosApi.resolveReconciliation(resolvingId, {
        resolution: resolveType,
        notes: resolveNotes || undefined,
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

  const handleMatchFromCandidate = (reconcileId: string, candidateOrderId: string) => {
    setMatchingId(reconcileId);
    setMatchOrderId(candidateOrderId);
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
          <h1 className="text-2xl font-semibold text-neutral-900">Reconciliation</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Match unmatched results to orders or resolve orphaned items.
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

      {/* Match dialog */}
      {matchingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Match to Order</h2>
          <div>
            <label htmlFor="matchOrderId" className="block text-sm font-medium text-neutral-700 mb-1">
              Order ID (UUID)
            </label>
            <input
              id="matchOrderId"
              type="text"
              value={matchOrderId}
              onChange={(e) => setMatchOrderId(e.target.value)}
              placeholder="UUID of the order to match against"
              className="input-field"
            />
          </div>
          <div>
            <label htmlFor="matchNotes" className="block text-sm font-medium text-neutral-700 mb-1">
              Notes (optional)
            </label>
            <textarea
              id="matchNotes"
              value={matchNotes}
              onChange={(e) => setMatchNotes(e.target.value)}
              placeholder="Match notes..."
              rows={2}
              className="input-field"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleMatch} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Matching..." : "Confirm Match"}
            </button>
            <button
              onClick={() => {
                setMatchingId(null);
                setMatchOrderId("");
                setMatchNotes("");
              }}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Resolve dialog */}
      {resolvingId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Resolve Without Matching</h2>
          <div>
            <label htmlFor="resolveType" className="block text-sm font-medium text-neutral-700 mb-1">
              Resolution Type
            </label>
            <select
              id="resolveType"
              value={resolveType}
              onChange={(e) => setResolveType(e.target.value as typeof resolveType)}
              className="select-field"
            >
              <option value="DUPLICATE">Duplicate</option>
              <option value="INVALID">Invalid</option>
              <option value="ORPHANED">Orphaned</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
          <div>
            <label htmlFor="resolveNotes" className="block text-sm font-medium text-neutral-700 mb-1">
              Notes (optional)
            </label>
            <textarea
              id="resolveNotes"
              value={resolveNotes}
              onChange={(e) => setResolveNotes(e.target.value)}
              placeholder="Resolution notes..."
              rows={2}
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

      {/* Filters */}
      <div className="flex gap-4 mb-4">
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value)}
          className="select-field w-48"
        >
          <option value="">All Order Types</option>
          {ORDER_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.charAt(0) + t.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
      </div>

      {/* Reconciliation table */}
      <div className="space-y-4">
        {items.map((item) => (
          <div key={item.id} className="card p-5">
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-3">
                  <span className={STATUS_BADGE[item.status] || "badge bg-neutral-100 text-neutral-600"}>
                    {item.status}
                  </span>
                  <span className="badge bg-neutral-100 text-neutral-700">
                    {item.type.replace("_", " ")}
                  </span>
                  <span className="badge bg-purple-100 text-purple-800">
                    {item.orderType}
                  </span>
                  <span className={`badge ${CONFIDENCE_COLOR(item.confidenceScore)}`}>
                    {(item.confidenceScore * 100).toFixed(0)}% confidence
                  </span>
                </div>
                <div className="mt-2 text-sm text-neutral-900">
                  <span className="font-medium">Source:</span>{" "}
                  <span className="font-mono text-xs">{item.sourceIdentifier}</span>
                </div>
                <div className="text-sm text-neutral-600 mt-1">
                  <span className="font-medium">Patient:</span>{" "}
                  <span className="font-mono text-xs">{item.patientCpid}</span>
                  {item.resultSummary && (
                    <>
                      {" "}| <span className="font-medium">Summary:</span> {item.resultSummary}
                    </>
                  )}
                </div>
                <div className="text-xs text-neutral-400 mt-1">
                  Received: {new Date(item.receivedAt).toLocaleString()}
                </div>
              </div>

              <div className="flex gap-2 ml-4">
                {item.status === "PENDING" && (
                  <>
                    <button
                      onClick={() => setMatchingId(item.id)}
                      className="btn-primary text-xs"
                    >
                      Match
                    </button>
                    <button
                      onClick={() => setResolvingId(item.id)}
                      className="btn-secondary text-xs"
                    >
                      Resolve
                    </button>
                  </>
                )}
                {item.candidateOrders.length > 0 && (
                  <button
                    onClick={() => setExpandedId(expandedId === item.id ? null : item.id)}
                    className="text-xs text-indigo-600 hover:text-indigo-800 font-medium"
                  >
                    {expandedId === item.id ? "Hide" : `${item.candidateOrders.length} Candidates`}
                  </button>
                )}
              </div>
            </div>

            {/* Candidate orders */}
            {expandedId === item.id && item.candidateOrders.length > 0 && (
              <div className="mt-4 border-t border-neutral-200 pt-4">
                <h3 className="text-sm font-medium text-neutral-700 mb-2">Candidate Orders</h3>
                <div className="border border-neutral-200 rounded-lg overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-neutral-200 bg-neutral-50">
                        <th className="text-left px-3 py-2 font-medium text-neutral-600">Order #</th>
                        <th className="text-left px-3 py-2 font-medium text-neutral-600">Patient</th>
                        <th className="text-left px-3 py-2 font-medium text-neutral-600">Type</th>
                        <th className="text-left px-3 py-2 font-medium text-neutral-600">Confidence</th>
                        <th className="text-left px-3 py-2 font-medium text-neutral-600">Match Reasons</th>
                        <th className="text-left px-3 py-2 font-medium text-neutral-600">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-neutral-100">
                      {item.candidateOrders.map((candidate) => (
                        <tr key={candidate.orderId} className="hover:bg-neutral-50">
                          <td className="px-3 py-2 font-mono text-xs">
                            {candidate.orderNumber}
                          </td>
                          <td className="px-3 py-2 font-mono text-xs">
                            {candidate.patientCpid}
                          </td>
                          <td className="px-3 py-2">
                            <span className="badge bg-purple-100 text-purple-800">
                              {candidate.orderType}
                            </span>
                          </td>
                          <td className="px-3 py-2">
                            <span className={`badge ${CONFIDENCE_COLOR(candidate.confidence)}`}>
                              {(candidate.confidence * 100).toFixed(0)}%
                            </span>
                          </td>
                          <td className="px-3 py-2 text-xs text-neutral-600">
                            {candidate.matchReasons.join(", ")}
                          </td>
                          <td className="px-3 py-2">
                            {item.status === "PENDING" && (
                              <button
                                onClick={() => handleMatchFromCandidate(item.id, candidate.orderId)}
                                className="text-xs text-emerald-600 hover:text-emerald-800 font-medium"
                              >
                                Select
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        ))}

        {items.length === 0 && (
          <div className="card p-12 text-center text-neutral-500">
            No pending reconciliation items. All results are matched.
          </div>
        )}
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        {items.length} pending item{items.length !== 1 ? "s" : ""}
      </div>
    </div>
  );
}
