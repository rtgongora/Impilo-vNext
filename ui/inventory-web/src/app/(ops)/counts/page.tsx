"use client";

import { useState, useCallback } from "react";
import {
  inventoryApi,
  type StockCount,
  type CountStatus,
  type CountLine,
} from "@/lib/inventoryApi";

const STATUS_BADGE: Record<CountStatus, string> = {
  DRAFT: "badge-status-draft",
  IN_PROGRESS: "badge-status-in-progress",
  SUBMITTED: "badge-status-submitted",
  APPROVED: "badge-status-approved",
  REJECTED: "badge-status-rejected",
};

type ViewMode = "LIST" | "CREATE" | "DETAIL";

export default function CountsPage() {
  const [currentCount, setCurrentCount] = useState<StockCount | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("LIST");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Create form
  const [createStoreId, setCreateStoreId] = useState("");
  const [createCountType, setCreateCountType] = useState<"FULL" | "CYCLE" | "SPOT">("FULL");
  const [createNotes, setCreateNotes] = useState("");

  // Count line entry
  const [editingLineId, setEditingLineId] = useState<string | null>(null);
  const [countedQty, setCountedQty] = useState<number>(0);
  const [barcodeScan, setBarcodeScan] = useState("");

  // Reject dialog
  const [showReject, setShowReject] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  // Load count by ID
  const [countIdInput, setCountIdInput] = useState("");

  const loadCount = useCallback(async (id: string) => {
    try {
      setLoading(true);
      setError(null);
      const data = await inventoryApi.getCount(id);
      setCurrentCount(data);
      setViewMode("DETAIL");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load count");
    } finally {
      setLoading(false);
    }
  }, []);

  const handleCreate = async () => {
    if (!createStoreId) {
      setError("Store ID is required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.createCount({
        storeId: createStoreId,
        countType: createCountType,
        notes: createNotes || undefined,
      });
      setCurrentCount(data);
      setSuccessMessage("Stock count session created.");
      setViewMode("DETAIL");
      setCreateStoreId("");
      setCreateNotes("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create count");
    } finally {
      setActionLoading(false);
    }
  };

  const handleStart = async () => {
    if (!currentCount) return;
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.startCount(currentCount.id);
      setCurrentCount(data);
      setSuccessMessage("Count session started. System quantities have been snapshotted.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start count");
    } finally {
      setActionLoading(false);
    }
  };

  const handleRecordLine = async (lineId: string) => {
    if (!currentCount) return;
    setActionLoading(true);
    setError(null);
    try {
      await inventoryApi.recordCountLine(currentCount.id, lineId, {
        countedQty,
        barcodeScan: barcodeScan || undefined,
      });
      setSuccessMessage("Count line recorded.");
      setEditingLineId(null);
      setCountedQty(0);
      setBarcodeScan("");
      // Reload count
      await loadCount(currentCount.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record count line");
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubmit = async () => {
    if (!currentCount) return;
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.submitCount(currentCount.id);
      setCurrentCount(data);
      setSuccessMessage("Count submitted for approval.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit count");
    } finally {
      setActionLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!currentCount) return;
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.approveCount(currentCount.id);
      setCurrentCount(data);
      setSuccessMessage("Count approved. Adjustments will be generated for variances.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve count");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!currentCount || !rejectReason.trim()) {
      setError("Rejection reason is required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.rejectCount(currentCount.id, { reason: rejectReason });
      setCurrentCount(data);
      setSuccessMessage("Count rejected. Re-count required.");
      setShowReject(false);
      setRejectReason("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject count");
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Stock Counts</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Physical stock count sessions with variance review and approval workflow.
          </p>
        </div>
        <div className="flex gap-2">
          {viewMode !== "CREATE" && (
            <button onClick={() => setViewMode("CREATE")} className="btn-primary">
              New Count
            </button>
          )}
          {viewMode === "DETAIL" && (
            <button onClick={() => setViewMode("LIST")} className="btn-secondary">
              Back
            </button>
          )}
        </div>
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

      {/* LIST VIEW — load by ID */}
      {viewMode === "LIST" && (
        <div className="card p-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Load Stock Count</h2>
          <p className="text-sm text-neutral-500">
            Enter a stock count session ID to view its details and perform actions.
          </p>
          <div className="flex gap-3">
            <input
              value={countIdInput}
              onChange={(e) => setCountIdInput(e.target.value)}
              placeholder="Stock count session UUID"
              className="input-field flex-1"
            />
            <button
              onClick={() => countIdInput && loadCount(countIdInput)}
              disabled={loading || !countIdInput}
              className="btn-primary"
            >
              {loading ? "Loading..." : "Load"}
            </button>
          </div>
        </div>
      )}

      {/* CREATE VIEW */}
      {viewMode === "CREATE" && (
        <div className="card p-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Create Stock Count Session</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label htmlFor="c-store" className="block text-sm font-medium text-neutral-700 mb-1">Store ID</label>
              <input
                id="c-store"
                value={createStoreId}
                onChange={(e) => setCreateStoreId(e.target.value)}
                placeholder="Store UUID"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="c-type" className="block text-sm font-medium text-neutral-700 mb-1">Count Type</label>
              <select
                id="c-type"
                value={createCountType}
                onChange={(e) => setCreateCountType(e.target.value as "FULL" | "CYCLE" | "SPOT")}
                className="select-field"
              >
                <option value="FULL">Full Physical Count</option>
                <option value="CYCLE">Cycle Count</option>
                <option value="SPOT">Spot Check</option>
              </select>
            </div>
            <div>
              <label htmlFor="c-notes" className="block text-sm font-medium text-neutral-700 mb-1">Notes (optional)</label>
              <input
                id="c-notes"
                value={createNotes}
                onChange={(e) => setCreateNotes(e.target.value)}
                placeholder="Count session notes"
                className="input-field"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={handleCreate} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Creating..." : "Create Count"}
            </button>
            <button onClick={() => setViewMode("LIST")} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* DETAIL VIEW */}
      {viewMode === "DETAIL" && currentCount && (
        <div className="space-y-6">
          {/* Count header */}
          <div className="card p-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-lg font-semibold text-neutral-900">
                  Count Session
                </h2>
                <p className="text-xs text-neutral-500 font-mono mt-1">{currentCount.id}</p>
              </div>
              <span className={STATUS_BADGE[currentCount.status]}>
                {currentCount.status.replace("_", " ")}
              </span>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <div>
                <span className="text-neutral-500">Store:</span>
                <span className="ml-2 font-medium">{currentCount.storeName || currentCount.storeId}</span>
              </div>
              <div>
                <span className="text-neutral-500">Type:</span>
                <span className="ml-2 font-medium">{currentCount.countType}</span>
              </div>
              <div>
                <span className="text-neutral-500">Lines:</span>
                <span className="ml-2 font-medium">{currentCount.countedLines}/{currentCount.totalLines}</span>
              </div>
              <div>
                <span className="text-neutral-500">Total Variance:</span>
                <span className={`ml-2 font-medium ${
                  currentCount.totalVariance === 0 ? "text-emerald-600" : "text-red-600"
                }`}>
                  {currentCount.totalVariance}
                </span>
              </div>
            </div>

            {/* Action buttons */}
            <div className="flex gap-2 mt-4 pt-4 border-t border-neutral-200">
              {currentCount.status === "DRAFT" && (
                <button onClick={handleStart} disabled={actionLoading} className="btn-primary">
                  {actionLoading ? "Starting..." : "Start Count"}
                </button>
              )}
              {currentCount.status === "IN_PROGRESS" && (
                <button onClick={handleSubmit} disabled={actionLoading} className="btn-primary">
                  {actionLoading ? "Submitting..." : "Submit for Approval"}
                </button>
              )}
              {currentCount.status === "SUBMITTED" && (
                <>
                  <button onClick={handleApprove} disabled={actionLoading} className="btn-primary">
                    {actionLoading ? "Approving..." : "Approve"}
                  </button>
                  <button onClick={() => setShowReject(true)} disabled={actionLoading} className="btn-danger">
                    Reject
                  </button>
                </>
              )}
            </div>
          </div>

          {/* Reject dialog */}
          {showReject && (
            <div className="card p-6 space-y-4">
              <h3 className="text-lg font-semibold text-neutral-900">Reject Count</h3>
              <div>
                <label htmlFor="rejectReason" className="block text-sm font-medium text-neutral-700 mb-1">
                  Reason for rejection
                </label>
                <textarea
                  id="rejectReason"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Provide a reason for rejection..."
                  rows={3}
                  className="input-field"
                />
              </div>
              <div className="flex gap-2">
                <button onClick={handleReject} disabled={actionLoading} className="btn-danger">
                  {actionLoading ? "Rejecting..." : "Reject Count"}
                </button>
                <button onClick={() => { setShowReject(false); setRejectReason(""); }} className="btn-secondary">
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* Count lines table */}
          <div className="card overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 bg-neutral-50">
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Item Code</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Batch</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Bin</th>
                  <th className="text-right px-4 py-3 font-medium text-neutral-600">Expected</th>
                  <th className="text-right px-4 py-3 font-medium text-neutral-600">Counted</th>
                  <th className="text-right px-4 py-3 font-medium text-neutral-600">Variance</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {currentCount.lines.map((line: CountLine) => (
                  <tr key={line.id} className="hover:bg-neutral-50 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                      {line.itemCode}
                    </td>
                    <td className="px-4 py-3 text-neutral-900">{line.itemName}</td>
                    <td className="px-4 py-3 text-neutral-600 font-mono text-xs">{line.batchNumber}</td>
                    <td className="px-4 py-3 text-neutral-500 text-xs">{line.binCode || "-"}</td>
                    <td className="px-4 py-3 text-right text-neutral-600">{line.expectedQty}</td>
                    <td className="px-4 py-3 text-right">
                      {editingLineId === line.id ? (
                        <input
                          type="number"
                          min={0}
                          value={countedQty}
                          onChange={(e) => setCountedQty(parseInt(e.target.value) || 0)}
                          className="input-field w-20 text-right"
                          autoFocus
                        />
                      ) : (
                        <span className={line.countedQty !== null ? "text-neutral-900 font-medium" : "text-neutral-400"}>
                          {line.countedQty !== null ? line.countedQty : "-"}
                        </span>
                      )}
                    </td>
                    <td className={`px-4 py-3 text-right font-medium ${
                      line.variance === null
                        ? "text-neutral-400"
                        : line.variance === 0
                          ? "text-emerald-600"
                          : "text-red-600"
                    }`}>
                      {line.variance !== null ? (line.variance > 0 ? `+${line.variance}` : line.variance) : "-"}
                    </td>
                    <td className="px-4 py-3">
                      {currentCount.status === "IN_PROGRESS" && (
                        <>
                          {editingLineId === line.id ? (
                            <div className="flex gap-1">
                              <button
                                onClick={() => handleRecordLine(line.id)}
                                disabled={actionLoading}
                                className="text-xs text-emerald-600 hover:text-emerald-800 font-medium"
                              >
                                Save
                              </button>
                              <button
                                onClick={() => { setEditingLineId(null); setCountedQty(0); }}
                                className="text-xs text-neutral-500 hover:text-neutral-700 font-medium"
                              >
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => {
                                setEditingLineId(line.id);
                                setCountedQty(line.countedQty ?? line.expectedQty);
                              }}
                              className="text-xs text-amber-600 hover:text-amber-800 font-medium"
                            >
                              {line.countedQty !== null ? "Re-count" : "Count"}
                            </button>
                          )}
                        </>
                      )}
                    </td>
                  </tr>
                ))}
                {currentCount.lines.length === 0 && (
                  <tr>
                    <td colSpan={8} className="px-4 py-12 text-center text-neutral-500">
                      No count lines. Start the count to populate lines from current stock.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="text-xs text-neutral-400">
            {currentCount.countedLines} of {currentCount.totalLines} lines counted
          </div>
        </div>
      )}
    </div>
  );
}
