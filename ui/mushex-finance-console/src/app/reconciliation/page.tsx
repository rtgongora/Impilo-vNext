"use client";

import { useState, useCallback, useEffect } from "react";
import {
  mushexApi,
  type ReconEntry,
  type ReconStatus,
  type PagedResponse,
  type ReconImportLine,
} from "@/lib/mushexApi";

const STATUS_BADGE: Record<ReconStatus, string> = {
  UNMATCHED: "badge-unmatched",
  MATCHED: "badge-matched",
  DISPUTED: "badge-disputed",
};

export default function ReconciliationPage() {
  const [entries, setEntries] = useState<ReconEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Import form
  const [showImport, setShowImport] = useState(false);
  const [importRef, setImportRef] = useState("");
  const [importDate, setImportDate] = useState("");
  const [importAmount, setImportAmount] = useState("");
  const [importCurrency, setImportCurrency] = useState("USD");
  const [importCounterparty, setImportCounterparty] = useState("");
  const [importLoading, setImportLoading] = useState(false);

  // Match form
  const [matchReconId, setMatchReconId] = useState<string | null>(null);
  const [matchIntentId, setMatchIntentId] = useState("");
  const [matchLoading, setMatchLoading] = useState(false);

  const loadUnmatched = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data: PagedResponse<ReconEntry> = await mushexApi.getUnmatched({
        page,
        size: 20,
      });
      setEntries(data.items);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load reconciliation entries");
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    loadUnmatched();
  }, [loadUnmatched]);

  const handleImport = useCallback(async () => {
    if (!importRef || !importDate || !importAmount) {
      setError("Statement ref, date, and amount are required.");
      return;
    }
    setImportLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const line: ReconImportLine = {
        statementRef: importRef,
        statementDate: importDate,
        amount: parseFloat(importAmount),
        currency: importCurrency,
        counterparty: importCounterparty || undefined,
      };
      await mushexApi.importStatement([line]);
      setSuccessMessage("Statement line imported successfully.");
      setImportRef("");
      setImportDate("");
      setImportAmount("");
      setImportCounterparty("");
      setShowImport(false);
      await loadUnmatched();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to import statement");
    } finally {
      setImportLoading(false);
    }
  }, [importRef, importDate, importAmount, importCurrency, importCounterparty, loadUnmatched]);

  const handleMatch = useCallback(async () => {
    if (!matchReconId || !matchIntentId.trim()) {
      setError("Intent ID is required for matching.");
      return;
    }
    setMatchLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      await mushexApi.matchEntry(matchReconId, matchIntentId.trim());
      setSuccessMessage("Entry matched successfully.");
      setMatchReconId(null);
      setMatchIntentId("");
      await loadUnmatched();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to match entry");
    } finally {
      setMatchLoading(false);
    }
  }, [matchReconId, matchIntentId, loadUnmatched]);

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Bank Reconciliation</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Import bank statements and match entries to payment intents.
          </p>
        </div>
        <button onClick={() => setShowImport(!showImport)} className="btn-primary">
          Import Statement Line
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

      {/* Import form */}
      {showImport && (
        <div className="card p-6 mb-6">
          <h2 className="text-sm font-semibold text-neutral-700 mb-3">Import Statement Line</h2>
          <div className="grid grid-cols-2 gap-4 mb-4">
            <div>
              <label className="block text-xs font-medium text-neutral-600 mb-1">Statement Ref</label>
              <input
                type="text"
                value={importRef}
                onChange={(e) => setImportRef(e.target.value)}
                placeholder="e.g., TXN-20240101-001"
                className="input-field"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-neutral-600 mb-1">Statement Date</label>
              <input
                type="date"
                value={importDate}
                onChange={(e) => setImportDate(e.target.value)}
                className="input-field"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-neutral-600 mb-1">Amount</label>
              <input
                type="number"
                step="0.01"
                value={importAmount}
                onChange={(e) => setImportAmount(e.target.value)}
                placeholder="0.00"
                className="input-field"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-neutral-600 mb-1">Currency</label>
              <input
                type="text"
                value={importCurrency}
                onChange={(e) => setImportCurrency(e.target.value)}
                className="input-field"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-neutral-600 mb-1">Counterparty</label>
              <input
                type="text"
                value={importCounterparty}
                onChange={(e) => setImportCounterparty(e.target.value)}
                placeholder="Optional"
                className="input-field"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={handleImport} disabled={importLoading} className="btn-primary">
              {importLoading ? "Importing..." : "Import"}
            </button>
            <button onClick={() => setShowImport(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Match dialog */}
      {matchReconId && (
        <div className="card p-6 mb-6 border-emerald-300">
          <h2 className="text-sm font-semibold text-neutral-700 mb-3">
            Match Entry: <span className="font-mono">{matchReconId}</span>
          </h2>
          <div className="flex gap-3">
            <input
              type="text"
              value={matchIntentId}
              onChange={(e) => setMatchIntentId(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && handleMatch()}
              placeholder="Enter Payment Intent ID to match..."
              className="input-field flex-1"
            />
            <button onClick={handleMatch} disabled={matchLoading} className="btn-primary">
              {matchLoading ? "Matching..." : "Match"}
            </button>
            <button onClick={() => { setMatchReconId(null); setMatchIntentId(""); }} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Unmatched entries table */}
      {loading ? (
        <div className="p-8 text-center text-neutral-500 text-sm">Loading...</div>
      ) : (
        <div className="card overflow-hidden">
          <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
            <h3 className="text-sm font-semibold text-neutral-700">
              Unmatched Entries ({totalElements})
            </h3>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200">
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">ID</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Statement Ref</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Date</th>
                <th className="text-right px-4 py-2 font-medium text-neutral-600 text-xs">Amount</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Counterparty</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Status</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Matched To</th>
                <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {entries.map((entry) => (
                <tr key={entry.id} className="hover:bg-neutral-50 transition-colors">
                  <td className="px-4 py-2 font-mono text-xs">{entry.id}</td>
                  <td className="px-4 py-2 font-mono text-xs font-medium">{entry.statementRef}</td>
                  <td className="px-4 py-2 text-xs text-neutral-500">{entry.statementDate}</td>
                  <td className="px-4 py-2 text-right font-mono text-xs font-medium">
                    {entry.currency} {Number(entry.amount).toFixed(2)}
                  </td>
                  <td className="px-4 py-2 text-xs text-neutral-500">{entry.counterparty || "---"}</td>
                  <td className="px-4 py-2">
                    <span className={STATUS_BADGE[entry.status]}>{entry.status}</span>
                  </td>
                  <td className="px-4 py-2 font-mono text-xs text-neutral-500">
                    {entry.matchedIntentId || "---"}
                  </td>
                  <td className="px-4 py-2">
                    {entry.status === "UNMATCHED" && (
                      <button
                        onClick={() => setMatchReconId(entry.id)}
                        className="text-xs text-emerald-700 hover:text-emerald-800 font-medium"
                      >
                        Match
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {entries.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                    No unmatched entries found. Import a bank statement to begin reconciliation.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-xs text-neutral-500">
            {totalElements} entr{totalElements !== 1 ? "ies" : "y"} total
          </span>
          <div className="flex gap-2">
            <button
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
              className="btn-secondary text-xs"
            >
              Previous
            </button>
            <span className="text-xs text-neutral-600 self-center">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
              disabled={page >= totalPages - 1}
              className="btn-secondary text-xs"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
