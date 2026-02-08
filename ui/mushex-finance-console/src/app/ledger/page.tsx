"use client";

import { useState, useCallback } from "react";
import { mushexApi, type PaymentIntent } from "@/lib/mushexApi";
import { apiClient } from "@/lib/apiClient";

interface LedgerEntry {
  entryId: string;
  tenantId: string;
  intentId: string | null;
  referenceType: string;
  referenceId: string;
  debitAccount: string;
  creditAccount: string;
  amount: number;
  currency: string;
  description: string | null;
  createdAt: string;
}

export default function LedgerPage() {
  const [intentId, setIntentId] = useState("");
  const [intent, setIntent] = useState<PaymentIntent | null>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const lookupByIntent = useCallback(async () => {
    if (!intentId.trim()) {
      setError("Enter a payment intent ID to look up related ledger entries.");
      return;
    }
    setLoading(true);
    setError(null);
    setIntent(null);
    setEntries([]);
    try {
      const intentData = await mushexApi.getIntent(intentId.trim());
      setIntent(intentData);

      // Fetch ledger entries for this intent via a generic endpoint
      const ledgerData = await apiClient.get<LedgerEntry[]>(
        `/mushex/v1/ledger?intentId=${encodeURIComponent(intentId.trim())}`
      );
      setEntries(ledgerData);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load ledger entries");
    } finally {
      setLoading(false);
    }
  }, [intentId]);

  const totalDebits = entries.reduce((sum, e) => sum + Number(e.amount), 0);

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Ledger Entries</h1>
        <p className="text-sm text-neutral-500 mt-1">
          View double-entry ledger records associated with payment intents.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {/* Search */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={intentId}
          onChange={(e) => setIntentId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && lookupByIntent()}
          placeholder="Enter Payment Intent ID..."
          className="input-field flex-1"
        />
        <button onClick={lookupByIntent} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Search Ledger"}
        </button>
      </div>

      {/* Intent summary */}
      {intent && (
        <div className="card p-4 mb-6">
          <div className="flex items-center gap-3 text-sm">
            <span className="font-mono text-xs font-medium">{intent.intentId}</span>
            <span className={`badge ${
              intent.status === "PAID" ? "bg-emerald-100 text-emerald-800" :
              "bg-neutral-100 text-neutral-600"
            }`}>
              {intent.status}
            </span>
            <span className="text-neutral-500">
              {intent.currency} {Number(intent.amountTotal).toFixed(2)}
            </span>
          </div>
        </div>
      )}

      {/* Ledger table */}
      {entries.length > 0 && (
        <>
          <div className="card overflow-hidden">
            <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
              <h3 className="text-sm font-semibold text-neutral-700">
                Ledger Entries ({entries.length})
              </h3>
            </div>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200">
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Entry ID</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Ref Type</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Debit Account</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Credit Account</th>
                  <th className="text-right px-4 py-2 font-medium text-neutral-600 text-xs">Amount</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Description</th>
                  <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {entries.map((entry) => (
                  <tr key={entry.entryId} className="hover:bg-neutral-50 transition-colors">
                    <td className="px-4 py-2 font-mono text-xs">{entry.entryId}</td>
                    <td className="px-4 py-2 text-xs">
                      <span className="badge bg-blue-100 text-blue-800">{entry.referenceType}</span>
                    </td>
                    <td className="px-4 py-2 font-mono text-xs text-red-700">{entry.debitAccount}</td>
                    <td className="px-4 py-2 font-mono text-xs text-emerald-700">{entry.creditAccount}</td>
                    <td className="px-4 py-2 text-right font-mono text-xs font-medium">
                      {entry.currency} {Number(entry.amount).toFixed(2)}
                    </td>
                    <td className="px-4 py-2 text-xs text-neutral-500 max-w-xs truncate">
                      {entry.description || "---"}
                    </td>
                    <td className="px-4 py-2 text-xs text-neutral-500">
                      {new Date(entry.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t border-neutral-300 bg-neutral-50">
                  <td colSpan={4} className="px-4 py-2 text-right text-xs font-semibold text-neutral-700">
                    Total
                  </td>
                  <td className="px-4 py-2 text-right font-mono text-xs font-semibold text-neutral-900">
                    {totalDebits.toFixed(2)}
                  </td>
                  <td colSpan={2} />
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      )}

      {!intent && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Enter a payment intent ID above to view associated ledger entries.
        </div>
      )}

      {intent && entries.length === 0 && !loading && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          No ledger entries found for intent <span className="font-mono">{intentId}</span>.
        </div>
      )}
    </div>
  );
}
