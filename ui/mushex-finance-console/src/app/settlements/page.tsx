"use client";

import { useState, useCallback } from "react";
import { mushexApi, type Settlement, type SettlementStatus } from "@/lib/mushexApi";

const STATUS_BADGE: Record<SettlementStatus, string> = {
  DRAFT: "badge-draft",
  COMPUTING: "badge-computing",
  COMPUTED: "badge-computed",
  RELEASED: "badge-released",
  FAILED: "badge-failed",
};

export default function SettlementsPage() {
  const [periodStart, setPeriodStart] = useState("");
  const [periodEnd, setPeriodEnd] = useState("");
  const [settlementId, setSettlementId] = useState("");
  const [settlement, setSettlement] = useState<Settlement | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const runSettlement = useCallback(async () => {
    if (!periodStart || !periodEnd) {
      setError("Both period start and end dates are required.");
      return;
    }
    setLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const data = await mushexApi.runSettlement(periodStart, periodEnd);
      setSettlement(data);
      setSuccessMessage("Settlement run initiated successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run settlement");
    } finally {
      setLoading(false);
    }
  }, [periodStart, periodEnd]);

  const lookupSettlement = useCallback(async () => {
    if (!settlementId.trim()) {
      setError("Enter a settlement ID to search.");
      return;
    }
    setLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const data = await mushexApi.getSettlement(settlementId.trim());
      setSettlement(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load settlement");
    } finally {
      setLoading(false);
    }
  }, [settlementId]);

  const releasePayouts = useCallback(async () => {
    if (!settlement) return;
    setActionLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await mushexApi.releasePayouts(settlement.settlementId);
      setSettlement(updated);
      setSuccessMessage("Payouts released successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to release payouts");
    } finally {
      setActionLoading(false);
    }
  }, [settlement]);

  const parseTotals = (totals: string | null): Record<string, number> => {
    if (!totals) return {};
    try {
      return JSON.parse(totals);
    } catch {
      return {};
    }
  };

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Settlements</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Run settlement computations for a period, review totals, and release payouts.
        </p>
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

      {/* Run settlement */}
      <div className="card p-6 mb-6">
        <h2 className="text-sm font-semibold text-neutral-700 mb-3">Run New Settlement</h2>
        <div className="flex gap-3 items-end">
          <div>
            <label className="block text-xs font-medium text-neutral-600 mb-1">Period Start</label>
            <input
              type="date"
              value={periodStart}
              onChange={(e) => setPeriodStart(e.target.value)}
              className="input-field"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-neutral-600 mb-1">Period End</label>
            <input
              type="date"
              value={periodEnd}
              onChange={(e) => setPeriodEnd(e.target.value)}
              className="input-field"
            />
          </div>
          <button onClick={runSettlement} disabled={loading} className="btn-primary">
            {loading ? "Running..." : "Run Settlement"}
          </button>
        </div>
      </div>

      {/* Lookup settlement */}
      <div className="card p-6 mb-6">
        <h2 className="text-sm font-semibold text-neutral-700 mb-3">Look Up Settlement</h2>
        <div className="flex gap-3">
          <input
            type="text"
            value={settlementId}
            onChange={(e) => setSettlementId(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && lookupSettlement()}
            placeholder="Enter Settlement ID (ULID)..."
            className="input-field flex-1"
          />
          <button onClick={lookupSettlement} disabled={loading} className="btn-secondary">
            {loading ? "Loading..." : "Search"}
          </button>
        </div>
      </div>

      {/* Settlement detail */}
      {settlement && (
        <div className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-neutral-900 font-mono">
                {settlement.settlementId}
              </h2>
              <span className={STATUS_BADGE[settlement.status]}>
                {settlement.status}
              </span>
            </div>
            {settlement.status === "COMPUTED" && (
              <button
                onClick={releasePayouts}
                disabled={actionLoading}
                className="btn-primary text-xs"
              >
                {actionLoading ? "Releasing..." : "Release Payouts"}
              </button>
            )}
          </div>

          <div className="grid grid-cols-4 gap-4 text-sm">
            <div>
              <span className="text-neutral-500">Period Start</span>
              <p className="font-medium">{settlement.periodStart}</p>
            </div>
            <div>
              <span className="text-neutral-500">Period End</span>
              <p className="font-medium">{settlement.periodEnd}</p>
            </div>
            <div>
              <span className="text-neutral-500">Created</span>
              <p className="text-xs">{new Date(settlement.createdAt).toLocaleString()}</p>
            </div>
            <div>
              <span className="text-neutral-500">Updated</span>
              <p className="text-xs">{new Date(settlement.updatedAt).toLocaleString()}</p>
            </div>
          </div>

          {settlement.totals && (
            <div className="mt-4 pt-4 border-t border-neutral-200">
              <h3 className="text-sm font-semibold text-neutral-700 mb-3">Totals</h3>
              <div className="grid grid-cols-4 gap-3">
                {Object.entries(parseTotals(settlement.totals)).map(([key, value]) => (
                  <div key={key} className="bg-neutral-50 rounded-lg p-3 border border-neutral-200">
                    <span className="text-xs text-neutral-500 uppercase tracking-wider">
                      {key.replace(/_/g, " ")}
                    </span>
                    <p className="text-lg font-semibold text-neutral-900 mt-1 font-mono">
                      {Number(value).toFixed(2)}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {!settlement && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Run a new settlement or search by ID to view settlement details.
        </div>
      )}
    </div>
  );
}
