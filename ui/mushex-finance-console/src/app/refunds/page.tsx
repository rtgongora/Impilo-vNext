"use client";

import { useState, useCallback } from "react";
import { mushexApi, type Refund, type RefundStatus, type PaymentIntent } from "@/lib/mushexApi";

const STATUS_BADGE: Record<RefundStatus, string> = {
  PENDING: "badge-pending",
  PROCESSING: "badge-processing",
  COMPLETED: "badge-completed",
  FAILED: "badge-failed",
};

export default function RefundsPage() {
  const [intentId, setIntentId] = useState("");
  const [intent, setIntent] = useState<PaymentIntent | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Refund form
  const [refundAmount, setRefundAmount] = useState("");
  const [refundReason, setRefundReason] = useState("");
  const [refundResult, setRefundResult] = useState<Refund | null>(null);
  const [refundLoading, setRefundLoading] = useState(false);

  const lookupIntent = useCallback(async () => {
    if (!intentId.trim()) {
      setError("Enter a payment intent ID.");
      return;
    }
    setLoading(true);
    setError(null);
    setSuccessMessage(null);
    setIntent(null);
    setRefundResult(null);
    try {
      const data = await mushexApi.getIntent(intentId.trim());
      setIntent(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load payment intent");
    } finally {
      setLoading(false);
    }
  }, [intentId]);

  const submitRefund = useCallback(async () => {
    if (!intent) return;
    if (!refundAmount || parseFloat(refundAmount) <= 0) {
      setError("A valid refund amount is required.");
      return;
    }
    if (!refundReason.trim()) {
      setError("A refund reason is required.");
      return;
    }
    setRefundLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const result = await mushexApi.createRefund(
        intent.intentId,
        parseFloat(refundAmount),
        refundReason.trim()
      );
      setRefundResult(result);
      setSuccessMessage(`Refund ${result.refundId} created successfully.`);
      setRefundAmount("");
      setRefundReason("");
      // Refresh intent status
      const updated = await mushexApi.getIntent(intent.intentId);
      setIntent(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create refund");
    } finally {
      setRefundLoading(false);
    }
  }, [intent, refundAmount, refundReason]);

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Refund Management</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Look up a payment intent and issue refunds with approval tracking.
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

      {/* Lookup */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={intentId}
          onChange={(e) => setIntentId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && lookupIntent()}
          placeholder="Enter Payment Intent ID..."
          className="input-field flex-1"
        />
        <button onClick={lookupIntent} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Look Up"}
        </button>
      </div>

      {/* Intent summary */}
      {intent && (
        <div className="card p-6 mb-6">
          <div className="flex items-center gap-3 mb-3">
            <h2 className="text-lg font-semibold text-neutral-900 font-mono">
              {intent.intentId}
            </h2>
            <span className={`badge ${
              intent.status === "PAID" ? "bg-emerald-100 text-emerald-800" :
              intent.status === "REFUNDED" ? "bg-purple-100 text-purple-800" :
              intent.status === "REFUND_PENDING" ? "bg-amber-100 text-amber-800" :
              "bg-neutral-100 text-neutral-600"
            }`}>
              {intent.status.replace(/_/g, " ")}
            </span>
          </div>
          <div className="grid grid-cols-4 gap-4 text-sm">
            <div>
              <span className="text-neutral-500">Total</span>
              <p className="font-semibold font-mono">
                {intent.currency} {Number(intent.amountTotal).toFixed(2)}
              </p>
            </div>
            <div>
              <span className="text-neutral-500">Paid</span>
              <p className="font-semibold font-mono text-emerald-700">
                {intent.currency} {Number(intent.amountPaid).toFixed(2)}
              </p>
            </div>
            <div>
              <span className="text-neutral-500">Source</span>
              <p className="font-medium">{intent.sourceType}</p>
            </div>
            <div>
              <span className="text-neutral-500">Created</span>
              <p className="text-xs">{new Date(intent.createdAt).toLocaleString()}</p>
            </div>
          </div>
        </div>
      )}

      {/* Refund form */}
      {intent && ["PAID", "REFUND_PENDING"].includes(intent.status) && (
        <div className="card p-6 mb-6">
          <h2 className="text-sm font-semibold text-neutral-700 mb-3">Issue Refund</h2>
          <div className="grid grid-cols-2 gap-4 mb-4">
            <div>
              <label className="block text-xs font-medium text-neutral-600 mb-1">
                Refund Amount ({intent.currency})
              </label>
              <input
                type="number"
                step="0.01"
                max={intent.amountPaid}
                value={refundAmount}
                onChange={(e) => setRefundAmount(e.target.value)}
                placeholder="0.00"
                className="input-field"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-neutral-600 mb-1">Reason</label>
              <input
                type="text"
                value={refundReason}
                onChange={(e) => setRefundReason(e.target.value)}
                placeholder="Enter refund reason..."
                className="input-field"
              />
            </div>
          </div>
          <button onClick={submitRefund} disabled={refundLoading} className="btn-danger">
            {refundLoading ? "Processing..." : "Submit Refund"}
          </button>
        </div>
      )}

      {/* Refund result */}
      {refundResult && (
        <div className="card p-6">
          <h3 className="text-sm font-semibold text-neutral-700 mb-3">Refund Created</h3>
          <div className="grid grid-cols-4 gap-4 text-sm">
            <div>
              <span className="text-neutral-500">Refund ID</span>
              <p className="font-mono text-xs font-medium">{refundResult.refundId}</p>
            </div>
            <div>
              <span className="text-neutral-500">Amount</span>
              <p className="font-semibold font-mono">{Number(refundResult.amount).toFixed(2)}</p>
            </div>
            <div>
              <span className="text-neutral-500">Status</span>
              <span className={STATUS_BADGE[refundResult.status]}>{refundResult.status}</span>
            </div>
            <div>
              <span className="text-neutral-500">Reason</span>
              <p className="text-xs">{refundResult.reason || "---"}</p>
            </div>
          </div>
        </div>
      )}

      {!intent && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Enter a payment intent ID above to look up the intent and issue refunds.
        </div>
      )}
    </div>
  );
}
