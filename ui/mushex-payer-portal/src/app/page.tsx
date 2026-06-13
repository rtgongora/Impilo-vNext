"use client";

import { useState, useCallback } from "react";
import Link from "next/link";
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

export default function DashboardPage() {
  const [intentId, setIntentId] = useState("");
  const [intent, setIntent] = useState<PaymentIntent | null>(null);
  const [recentIntents, setRecentIntents] = useState<PaymentIntent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const lookupIntent = useCallback(async () => {
    if (!intentId.trim()) {
      setError("Enter a payment intent ID to search.");
      return;
    }
    setLoading(true);
    setError(null);
    setIntent(null);
    try {
      const data = await mushexApi.getIntent(intentId.trim());
      setIntent(data);
      setRecentIntents((prev) => {
        const filtered = prev.filter((i) => i.intentId !== data.intentId);
        return [data, ...filtered].slice(0, 10);
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load payment intent");
    } finally {
      setLoading(false);
    }
  }, [intentId]);

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">Payment Dashboard</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Look up payment intents, view status, and access receipts.
        </p>
      </div>

      {/* Quick links */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        <Link href="/payments" className="card p-5 hover:border-emerald-300 transition-colors">
          <h3 className="text-sm font-semibold text-neutral-700">Payments</h3>
          <p className="text-xs text-neutral-500 mt-1">View payment status and history</p>
        </Link>
        <Link href="/receipts" className="card p-5 hover:border-emerald-300 transition-colors">
          <h3 className="text-sm font-semibold text-neutral-700">Receipts</h3>
          <p className="text-xs text-neutral-500 mt-1">Download issued receipts</p>
        </Link>
        <Link href="/remittance" className="card p-5 hover:border-emerald-300 transition-colors">
          <h3 className="text-sm font-semibold text-neutral-700">Remittance</h3>
          <p className="text-xs text-neutral-500 mt-1">Claim a remittance slip with token and OTP</p>
        </Link>
      </div>

      {/* Intent lookup */}
      <div className="card p-6 mb-6">
        <h2 className="text-lg font-semibold text-neutral-900 mb-4">Look Up Payment Intent</h2>
        <div className="flex gap-3 mb-4">
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

        {error && (
          <div className="p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
            {error}
          </div>
        )}

        {intent && (
          <div className="mt-4 border-t border-neutral-200 pt-4">
            <div className="flex items-center gap-3 mb-3">
              <h3 className="text-sm font-semibold text-neutral-900 font-mono">
                {intent.intentId}
              </h3>
              <span className={STATUS_BADGE[intent.status]}>
                {intent.status.replace(/_/g, " ")}
              </span>
            </div>
            <div className="grid grid-cols-4 gap-4 text-sm">
              <div>
                <span className="text-neutral-500">Amount</span>
                <p className="font-semibold font-mono">
                  {intent.currency} {Number(intent.amountTotal).toFixed(2)}
                </p>
              </div>
              <div>
                <span className="text-neutral-500">Paid</span>
                <p className="font-semibold font-mono text-primary-hover">
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
      </div>

      {/* Recent lookups */}
      {recentIntents.length > 0 && (
        <div className="card overflow-hidden">
          <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
            <h3 className="text-sm font-semibold text-neutral-700">
              Recent Lookups ({recentIntents.length})
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200">
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Intent ID</th>
                <th className="text-right px-4 py-2 font-medium text-neutral-600 text-xs">Amount</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Status</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Source</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {recentIntents.map((ri) => (
                <tr key={ri.intentId} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-2 font-mono text-xs font-medium text-neutral-900">
                    {ri.intentId}
                  </td>
                  <td className="px-4 py-2 text-right font-mono text-xs">
                    {ri.currency} {Number(ri.amountTotal).toFixed(2)}
                  </td>
                  <td className="px-4 py-2">
                    <span className={STATUS_BADGE[ri.status]}>
                      {ri.status.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-neutral-500 text-xs">{ri.sourceType}</td>
                  <td className="px-4 py-2 text-neutral-500 text-xs">
                    {new Date(ri.createdAt).toLocaleDateString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
