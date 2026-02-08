"use client";

import { useState } from "react";
import { vendorApi } from "@/lib/vendorApi";

export default function SubstitutionsPage() {
  const [orderId, setOrderId] = useState("");
  const [lineId, setLineId] = useState("");
  const [substituteCode, setSubstituteCode] = useState("");
  const [substitutePrice, setSubstitutePrice] = useState("");
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handlePropose = async () => {
    if (!orderId || !lineId || !substituteCode) {
      setError("Order ID, Line ID, and substitute code are required");
      return;
    }
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      await vendorApi.proposeSubstitution(
        orderId,
        lineId,
        substituteCode,
        parseFloat(substitutePrice) || 0,
        reason
      );
      setResult("Substitution proposed successfully!");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to propose substitution"
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-8">
      <h1 className="text-2xl font-semibold mb-1">Propose Substitution</h1>
      <p className="text-sm text-neutral-500 mb-6">
        Propose medication substitutions for Rx fulfillment orders.
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}
      {result && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {result}
        </div>
      )}

      <div className="card p-6 max-w-lg space-y-4">
        <div>
          <label className="block text-sm font-medium text-neutral-700 mb-1">
            Order ID
          </label>
          <input
            value={orderId}
            onChange={(e) => setOrderId(e.target.value)}
            className="input-field"
            placeholder="Order ULID"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-neutral-700 mb-1">
            Line ID
          </label>
          <input
            value={lineId}
            onChange={(e) => setLineId(e.target.value)}
            className="input-field"
            placeholder="Line ULID"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-neutral-700 mb-1">
            Substitute Code
          </label>
          <input
            value={substituteCode}
            onChange={(e) => setSubstituteCode(e.target.value)}
            className="input-field"
            placeholder="MSIKA Core code"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-neutral-700 mb-1">
            Substitute Price (ZWG)
          </label>
          <input
            value={substitutePrice}
            onChange={(e) => setSubstitutePrice(e.target.value)}
            className="input-field"
            placeholder="0.00"
            type="number"
            step="0.01"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-neutral-700 mb-1">
            Reason
          </label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            className="input-field"
            rows={3}
            placeholder="Clinical reason for substitution..."
          />
        </div>

        <button
          onClick={handlePropose}
          disabled={loading}
          className="btn-primary w-full"
        >
          {loading ? "Proposing..." : "Propose Substitution"}
        </button>
      </div>
    </div>
  );
}
