"use client";

import { useState, useCallback } from "react";
import {
  orosApi,
  type Result,
  type Order,
  type Interpretation,
} from "@/lib/orosApi";

const INTERPRETATION_COLORS: Record<Interpretation, string> = {
  NORMAL: "text-primary-hover bg-success-soft",
  ABNORMAL: "text-yellow-700 bg-yellow-50",
  HIGH: "text-orange-700 bg-orange-50",
  LOW: "text-primary-hover bg-info-soft",
  CRITICAL_HIGH: "text-danger bg-danger-soft font-bold",
  CRITICAL_LOW: "text-danger bg-danger-soft font-bold",
};

export default function ResultsPage() {
  const [orderId, setOrderId] = useState("");
  const [order, setOrder] = useState<Order | null>(null);
  const [results, setResults] = useState<Result[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const loadResults = useCallback(async () => {
    if (!orderId.trim()) {
      setError("Please enter an Order ID.");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const [orderData, resultsData] = await Promise.all([
        orosApi.getOrder(orderId),
        orosApi.getResults(orderId),
      ]);
      setOrder(orderData);
      setResults(resultsData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load results");
      setOrder(null);
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  const handleAcknowledge = async () => {
    if (!order || results.length === 0) return;

    const unacknowledged = results.filter((r) => !r.acknowledged);
    if (unacknowledged.length === 0) {
      setError("All results are already acknowledged.");
      return;
    }

    setActionLoading(true);
    setError(null);

    try {
      await orosApi.acknowledgeResults(order.id, {
        resultIds: unacknowledged.map((r) => r.id),
      });
      setSuccessMessage(`${unacknowledged.length} result(s) acknowledged successfully.`);
      await loadResults();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to acknowledge results");
    } finally {
      setActionLoading(false);
    }
  };

  const handleMarkCritical = async (resultId: string) => {
    if (!order) return;

    setActionLoading(true);
    setError(null);

    try {
      await orosApi.markResultCritical(order.id, resultId, { reason: "Flagged by operator" });
      setSuccessMessage("Result flagged as critical.");
      await loadResults();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to flag result as critical");
    } finally {
      setActionLoading(false);
    }
  };

  const unacknowledgedCount = results.filter((r) => !r.acknowledged).length;
  const criticalCount = results.filter((r) => r.isCritical).length;

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Results</h1>
        <p className="text-sm text-neutral-500 mt-1">
          View, acknowledge, and flag critical results for clinical orders.
        </p>
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

      {/* Search */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={orderId}
          onChange={(e) => setOrderId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && loadResults()}
          placeholder="Enter Order ID (UUID) to view results..."
          className="input-field flex-1"
        />
        <button onClick={loadResults} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Load Results"}
        </button>
      </div>

      {/* Order summary */}
      {order && (
        <div className="card p-6 mb-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-lg font-semibold text-neutral-900">
                Order {order.orderNumber}
              </h2>
              <div className="flex gap-3 mt-2 text-sm text-neutral-600">
                <span>Patient: <span className="font-mono text-xs">{order.patientCpid}</span></span>
                <span>Type: <span className="font-medium">{order.type}</span></span>
                <span>Status: <span className="font-medium">{order.status}</span></span>
                <span>Priority: <span className="font-medium">{order.priority}</span></span>
              </div>
            </div>
            <div className="flex gap-2 items-center">
              {criticalCount > 0 && (
                <span className="badge-critical">
                  {criticalCount} CRITICAL
                </span>
              )}
              {unacknowledgedCount > 0 && (
                <button
                  onClick={handleAcknowledge}
                  disabled={actionLoading}
                  className="btn-primary"
                >
                  {actionLoading ? "Acknowledging..." : `Acknowledge (${unacknowledgedCount})`}
                </button>
              )}
              {unacknowledgedCount === 0 && results.length > 0 && (
                <span className="badge bg-emerald-100 text-primary-hover">
                  All Acknowledged
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Results table */}
      {results.length > 0 && (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200 bg-neutral-50">
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Test</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Value</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Unit</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Ref. Range</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Interpretation</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Critical</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">ACK</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Posted</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {results.map((result) => (
                <tr
                  key={result.id}
                  className={`hover:bg-neutral-50 transition-colors ${
                    result.isCritical ? "bg-danger-soft/50" : ""
                  }`}
                >
                  <td className="px-4 py-3">
                    <div className="text-neutral-900 font-medium">
                      {result.displayName || result.code || "N/A"}
                    </div>
                    {result.code && (
                      <div className="text-neutral-400 font-mono text-xs mt-0.5">
                        {result.code}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-neutral-900 font-mono font-medium">
                    {result.value}
                  </td>
                  <td className="px-4 py-3 text-neutral-600 text-xs">
                    {result.unit || "--"}
                  </td>
                  <td className="px-4 py-3 text-neutral-500 text-xs">
                    {result.referenceRange || "--"}
                  </td>
                  <td className="px-4 py-3">
                    {result.interpretation ? (
                      <span
                        className={`badge ${
                          INTERPRETATION_COLORS[result.interpretation] || "bg-neutral-100 text-neutral-600"
                        }`}
                      >
                        {result.interpretation.replace("_", " ")}
                      </span>
                    ) : (
                      <span className="text-neutral-400 text-xs">--</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {result.isCritical ? (
                      <span className="badge-critical">CRITICAL</span>
                    ) : (
                      <span className="text-neutral-400 text-xs">No</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {result.acknowledged ? (
                      <span className="badge bg-emerald-100 text-primary-hover">ACK</span>
                    ) : (
                      <span className="badge bg-yellow-100 text-yellow-800">Pending</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-neutral-500 text-xs">
                    {new Date(result.postedAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      {!result.isCritical && (
                        <button
                          onClick={() => handleMarkCritical(result.id)}
                          disabled={actionLoading}
                          className="text-xs text-red-600 hover:text-red-800 font-medium"
                        >
                          Flag Critical
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {order && results.length === 0 && !loading && (
        <div className="card p-12 text-center text-neutral-500">
          No results posted for this order yet.
        </div>
      )}

      {results.length > 0 && (
        <div className="mt-3 text-xs text-neutral-400">
          {results.length} result{results.length !== 1 ? "s" : ""} &middot;{" "}
          {criticalCount} critical &middot; {unacknowledgedCount} pending acknowledgement
        </div>
      )}
    </div>
  );
}
