"use client";

import { useState, useCallback } from "react";
import { mushexApi, type Receipt } from "@/lib/mushexApi";

export default function ReceiptsPage() {
  const [intentId, setIntentId] = useState("");
  const [receipts, setReceipts] = useState<Receipt[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);

  const loadReceipts = useCallback(async () => {
    if (!intentId.trim()) {
      setError("Enter a payment intent ID to fetch receipts.");
      return;
    }
    setLoading(true);
    setError(null);
    setReceipts([]);
    setSearched(true);
    try {
      const data = await mushexApi.getReceipts(intentId.trim());
      setReceipts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load receipts");
    } finally {
      setLoading(false);
    }
  }, [intentId]);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Receipts</h1>
        <p className="text-sm text-neutral-500 mt-1">
          View receipts issued for a payment intent. Enter the intent ID to retrieve associated receipts.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      {/* Search */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={intentId}
          onChange={(e) => setIntentId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && loadReceipts()}
          placeholder="Enter Payment Intent ID..."
          className="input-field flex-1"
        />
        <button onClick={loadReceipts} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Fetch Receipts"}
        </button>
      </div>

      {/* Results */}
      {receipts.length > 0 && (
        <div className="card overflow-hidden">
          <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
            <h3 className="text-sm font-semibold text-neutral-700">
              Receipts ({receipts.length})
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200">
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Receipt ID</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Intent ID</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Document ID</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Issued At</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Summary</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {receipts.map((r) => (
                <tr key={r.receiptId} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                    {r.receiptId}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-neutral-600">
                    {r.intentId}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-neutral-600">
                    {r.landelaDocId || "---"}
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500">
                    {new Date(r.issuedAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-xs text-neutral-500 max-w-xs truncate">
                    {r.summary || "---"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {searched && receipts.length === 0 && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          No receipts found for intent <span className="font-mono">{intentId}</span>.
        </div>
      )}

      {!searched && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Enter a payment intent ID above to retrieve associated receipts.
        </div>
      )}
    </div>
  );
}
