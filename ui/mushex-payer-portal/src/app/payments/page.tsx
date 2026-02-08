"use client";

import { useState, useCallback } from "react";
import { mushexApi, type PaymentIntent, type IntentStatus } from "@/lib/mushexApi";

const STATUS_BADGE: Record<IntentStatus, string> = {
  CREATED: "badge-created",
  PENDING: "badge-pending",
  AUTHORIZED: "badge-authorized",
  PAID: "badge-paid",
  FAILED: "badge-failed",
  CANCELLED: "badge-cancelled",
  REFUND_PENDING: "badge-refund-pending",
  REFUNDED: "badge-refunded",
};

export default function PaymentsPage() {
  const [intentId, setIntentId] = useState("");
  const [intent, setIntent] = useState<PaymentIntent | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const lookupIntent = useCallback(async () => {
    if (!intentId.trim()) {
      setError("Enter a payment intent ID to search.");
      return;
    }
    setLoading(true);
    setError(null);
    setIntent(null);
    setSuccessMessage(null);
    try {
      const data = await mushexApi.getIntent(intentId.trim());
      setIntent(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load payment intent");
    } finally {
      setLoading(false);
    }
  }, [intentId]);

  const cancelIntent = useCallback(async () => {
    if (!intent) return;
    setActionLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await mushexApi.cancelIntent(intent.intentId);
      setIntent(updated);
      setSuccessMessage("Payment intent cancelled successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to cancel payment intent");
    } finally {
      setActionLoading(false);
    }
  }, [intent]);

  const issueRemittance = useCallback(async () => {
    if (!intent) return;
    setActionLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const token = await mushexApi.issueRemittanceSlip(intent.intentId);
      setSuccessMessage(
        `Remittance slip issued. Token: ${token.token}. Share this with the recipient.`
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to issue remittance slip");
    } finally {
      setActionLoading(false);
    }
  }, [intent]);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Payment Status</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Search payment intents by ID to view status, amounts, and perform actions.
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

      {/* Search */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={intentId}
          onChange={(e) => setIntentId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && lookupIntent()}
          placeholder="Enter Payment Intent ID (ULID)..."
          className="input-field flex-1"
        />
        <button onClick={lookupIntent} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Search"}
        </button>
      </div>

      {/* Intent detail */}
      {intent && (
        <div className="card p-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-neutral-900 font-mono">
                {intent.intentId}
              </h2>
              <span className={STATUS_BADGE[intent.status]}>
                {intent.status.replace(/_/g, " ")}
              </span>
            </div>
            <div className="flex gap-2">
              {["CREATED", "PENDING", "AUTHORIZED"].includes(intent.status) && (
                <button
                  onClick={cancelIntent}
                  disabled={actionLoading}
                  className="btn-secondary text-xs"
                >
                  Cancel
                </button>
              )}
              {intent.status === "PAID" && (
                <button
                  onClick={issueRemittance}
                  disabled={actionLoading}
                  className="btn-primary text-xs"
                >
                  Issue Remittance Slip
                </button>
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <span className="text-neutral-500">Source Type</span>
              <p className="font-medium">{intent.sourceType}</p>
            </div>
            <div>
              <span className="text-neutral-500">Source ID</span>
              <p className="font-mono text-xs">{intent.sourceId}</p>
            </div>
            <div>
              <span className="text-neutral-500">Currency</span>
              <p className="font-medium">{intent.currency}</p>
            </div>
            <div>
              <span className="text-neutral-500">Expires</span>
              <p className="text-xs">
                {intent.expiresAt
                  ? new Date(intent.expiresAt).toLocaleString()
                  : "No expiry"}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 mt-4 pt-4 border-t border-neutral-200">
            <div>
              <span className="text-neutral-500 text-sm">Total Amount</span>
              <p className="text-xl font-semibold text-neutral-900 font-mono">
                {intent.currency} {Number(intent.amountTotal).toFixed(2)}
              </p>
            </div>
            <div>
              <span className="text-neutral-500 text-sm">Amount Paid</span>
              <p className="text-xl font-semibold text-emerald-700 font-mono">
                {intent.currency} {Number(intent.amountPaid).toFixed(2)}
              </p>
            </div>
            <div>
              <span className="text-neutral-500 text-sm">Outstanding</span>
              <p className="text-xl font-semibold text-amber-700 font-mono">
                {intent.currency}{" "}
                {(Number(intent.amountTotal) - Number(intent.amountPaid)).toFixed(2)}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mt-4 pt-4 border-t border-neutral-200 text-xs text-neutral-500">
            <div>
              <span>Created:</span> {new Date(intent.createdAt).toLocaleString()}
            </div>
            <div>
              <span>Updated:</span> {new Date(intent.updatedAt).toLocaleString()}
            </div>
          </div>
        </div>
      )}

      {!intent && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Enter a payment intent ID above to view its status and details.
        </div>
      )}
    </div>
  );
}
