"use client";

import { useState, useEffect, useCallback } from "react";
import {
  inventoryApi,
  type LedgerEvent,
  type MovementType,
  type PagedResponse,
  type ReceiptRequest,
  type IssueRequest,
  type TransferRequest,
} from "@/lib/inventoryApi";

const MOVEMENT_TYPES: MovementType[] = [
  "RECEIPT", "ISSUE", "TRANSFER", "ADJUSTMENT", "WASTAGE", "RETURN", "CONSUMPTION",
];

const MOVEMENT_BADGE: Record<MovementType, string> = {
  RECEIPT: "badge-movement-receipt",
  ISSUE: "badge-movement-issue",
  TRANSFER: "badge-movement-transfer",
  ADJUSTMENT: "badge-movement-adjustment",
  WASTAGE: "badge-movement-wastage",
  RETURN: "badge-movement-return",
  CONSUMPTION: "badge-movement-consumption",
};

type FormMode = "NONE" | "RECEIPT" | "ISSUE" | "TRANSFER";

export default function MovementsPage() {
  const [events, setEvents] = useState<PagedResponse<LedgerEvent> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Filters
  const [filterType, setFilterType] = useState<string>("");

  // Form state
  const [formMode, setFormMode] = useState<FormMode>("NONE");

  // Receipt form
  const [receiptData, setReceiptData] = useState<ReceiptRequest>({
    storeId: "",
    itemCode: "",
    batchNumber: "",
    expiryDate: "",
    quantity: 1,
  });

  // Issue form
  const [issueData, setIssueData] = useState<IssueRequest>({
    storeId: "",
    itemCode: "",
    batchNumber: "",
    quantity: 1,
  });

  // Transfer form
  const [transferData, setTransferData] = useState<TransferRequest>({
    sourceStoreId: "",
    destinationStoreId: "",
    itemCode: "",
    batchNumber: "",
    quantity: 1,
  });

  const loadEvents = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, string> = { size: "50" };
      if (filterType) params.movementType = filterType;
      const data = await inventoryApi.queryLedger(params);
      setEvents(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load ledger events");
    } finally {
      setLoading(false);
    }
  }, [filterType]);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  const handleReceipt = async () => {
    if (!receiptData.storeId || !receiptData.itemCode || !receiptData.batchNumber || !receiptData.expiryDate) {
      setError("All receipt fields are required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      await inventoryApi.postReceipt(receiptData);
      setSuccessMessage("Receipt recorded successfully.");
      setFormMode("NONE");
      setReceiptData({ storeId: "", itemCode: "", batchNumber: "", expiryDate: "", quantity: 1 });
      await loadEvents();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record receipt");
    } finally {
      setActionLoading(false);
    }
  };

  const handleIssue = async () => {
    if (!issueData.storeId || !issueData.itemCode || !issueData.batchNumber) {
      setError("Store, item code, and batch number are required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      await inventoryApi.postIssue(issueData);
      setSuccessMessage("Issue recorded successfully.");
      setFormMode("NONE");
      setIssueData({ storeId: "", itemCode: "", batchNumber: "", quantity: 1 });
      await loadEvents();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record issue");
    } finally {
      setActionLoading(false);
    }
  };

  const handleTransfer = async () => {
    if (!transferData.sourceStoreId || !transferData.destinationStoreId || !transferData.itemCode || !transferData.batchNumber) {
      setError("Source store, destination store, item code, and batch number are required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      await inventoryApi.postTransfer(transferData);
      setSuccessMessage("Transfer recorded successfully.");
      setFormMode("NONE");
      setTransferData({ sourceStoreId: "", destinationStoreId: "", itemCode: "", batchNumber: "", quantity: 1 });
      await loadEvents();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record transfer");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading && !events) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="h-10 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Stock Movements</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Record receipts, issues, transfers, and view movement history.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setFormMode(formMode === "RECEIPT" ? "NONE" : "RECEIPT")}
            className="btn-primary"
          >
            Receipt
          </button>
          <button
            onClick={() => setFormMode(formMode === "ISSUE" ? "NONE" : "ISSUE")}
            className="btn-warning"
          >
            Issue
          </button>
          <button
            onClick={() => setFormMode(formMode === "TRANSFER" ? "NONE" : "TRANSFER")}
            className="btn-secondary"
          >
            Transfer
          </button>
        </div>
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

      {/* Receipt Form */}
      {formMode === "RECEIPT" && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Record Receipt</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label htmlFor="r-store" className="block text-sm font-medium text-neutral-700 mb-1">Store ID</label>
              <input
                id="r-store"
                value={receiptData.storeId}
                onChange={(e) => setReceiptData({ ...receiptData, storeId: e.target.value })}
                placeholder="Store UUID"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="r-item" className="block text-sm font-medium text-neutral-700 mb-1">Item Code</label>
              <input
                id="r-item"
                value={receiptData.itemCode}
                onChange={(e) => setReceiptData({ ...receiptData, itemCode: e.target.value })}
                placeholder="e.g., PARA-500MG"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="r-batch" className="block text-sm font-medium text-neutral-700 mb-1">Batch Number</label>
              <input
                id="r-batch"
                value={receiptData.batchNumber}
                onChange={(e) => setReceiptData({ ...receiptData, batchNumber: e.target.value })}
                placeholder="e.g., BATCH-001"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="r-expiry" className="block text-sm font-medium text-neutral-700 mb-1">Expiry Date</label>
              <input
                id="r-expiry"
                type="date"
                value={receiptData.expiryDate}
                onChange={(e) => setReceiptData({ ...receiptData, expiryDate: e.target.value })}
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="r-qty" className="block text-sm font-medium text-neutral-700 mb-1">Quantity</label>
              <input
                id="r-qty"
                type="number"
                min={1}
                value={receiptData.quantity}
                onChange={(e) => setReceiptData({ ...receiptData, quantity: parseInt(e.target.value) || 1 })}
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="r-supplier" className="block text-sm font-medium text-neutral-700 mb-1">Supplier (optional)</label>
              <input
                id="r-supplier"
                value={receiptData.supplierName || ""}
                onChange={(e) => setReceiptData({ ...receiptData, supplierName: e.target.value })}
                placeholder="Supplier name"
                className="input-field"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={handleReceipt} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Recording..." : "Record Receipt"}
            </button>
            <button onClick={() => setFormMode("NONE")} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Issue Form */}
      {formMode === "ISSUE" && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Record Issue</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label htmlFor="i-store" className="block text-sm font-medium text-neutral-700 mb-1">Store ID</label>
              <input
                id="i-store"
                value={issueData.storeId}
                onChange={(e) => setIssueData({ ...issueData, storeId: e.target.value })}
                placeholder="Store UUID"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="i-item" className="block text-sm font-medium text-neutral-700 mb-1">Item Code</label>
              <input
                id="i-item"
                value={issueData.itemCode}
                onChange={(e) => setIssueData({ ...issueData, itemCode: e.target.value })}
                placeholder="e.g., PARA-500MG"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="i-batch" className="block text-sm font-medium text-neutral-700 mb-1">Batch Number</label>
              <input
                id="i-batch"
                value={issueData.batchNumber}
                onChange={(e) => setIssueData({ ...issueData, batchNumber: e.target.value })}
                placeholder="e.g., BATCH-001"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="i-qty" className="block text-sm font-medium text-neutral-700 mb-1">Quantity</label>
              <input
                id="i-qty"
                type="number"
                min={1}
                value={issueData.quantity}
                onChange={(e) => setIssueData({ ...issueData, quantity: parseInt(e.target.value) || 1 })}
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="i-to" className="block text-sm font-medium text-neutral-700 mb-1">Issued To (optional)</label>
              <input
                id="i-to"
                value={issueData.issuedTo || ""}
                onChange={(e) => setIssueData({ ...issueData, issuedTo: e.target.value })}
                placeholder="Department or person"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="i-reason" className="block text-sm font-medium text-neutral-700 mb-1">Reason (optional)</label>
              <input
                id="i-reason"
                value={issueData.reason || ""}
                onChange={(e) => setIssueData({ ...issueData, reason: e.target.value })}
                placeholder="Reason for issue"
                className="input-field"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={handleIssue} disabled={actionLoading} className="btn-warning">
              {actionLoading ? "Recording..." : "Record Issue"}
            </button>
            <button onClick={() => setFormMode("NONE")} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Transfer Form */}
      {formMode === "TRANSFER" && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Record Transfer</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label htmlFor="t-source" className="block text-sm font-medium text-neutral-700 mb-1">Source Store ID</label>
              <input
                id="t-source"
                value={transferData.sourceStoreId}
                onChange={(e) => setTransferData({ ...transferData, sourceStoreId: e.target.value })}
                placeholder="Source store UUID"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="t-dest" className="block text-sm font-medium text-neutral-700 mb-1">Destination Store ID</label>
              <input
                id="t-dest"
                value={transferData.destinationStoreId}
                onChange={(e) => setTransferData({ ...transferData, destinationStoreId: e.target.value })}
                placeholder="Destination store UUID"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="t-item" className="block text-sm font-medium text-neutral-700 mb-1">Item Code</label>
              <input
                id="t-item"
                value={transferData.itemCode}
                onChange={(e) => setTransferData({ ...transferData, itemCode: e.target.value })}
                placeholder="e.g., PARA-500MG"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="t-batch" className="block text-sm font-medium text-neutral-700 mb-1">Batch Number</label>
              <input
                id="t-batch"
                value={transferData.batchNumber}
                onChange={(e) => setTransferData({ ...transferData, batchNumber: e.target.value })}
                placeholder="e.g., BATCH-001"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="t-qty" className="block text-sm font-medium text-neutral-700 mb-1">Quantity</label>
              <input
                id="t-qty"
                type="number"
                min={1}
                value={transferData.quantity}
                onChange={(e) => setTransferData({ ...transferData, quantity: parseInt(e.target.value) || 1 })}
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="t-reason" className="block text-sm font-medium text-neutral-700 mb-1">Reason (optional)</label>
              <input
                id="t-reason"
                value={transferData.reason || ""}
                onChange={(e) => setTransferData({ ...transferData, reason: e.target.value })}
                placeholder="Transfer reason"
                className="input-field"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={handleTransfer} disabled={actionLoading} className="btn-secondary">
              {actionLoading ? "Recording..." : "Record Transfer"}
            </button>
            <button onClick={() => setFormMode("NONE")} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Filter */}
      <div className="flex gap-4 mb-4">
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value)}
          className="select-field w-48"
        >
          <option value="">All Movement Types</option>
          {MOVEMENT_TYPES.map((t) => (
            <option key={t} value={t}>
              {t.charAt(0) + t.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
        <button onClick={loadEvents} className="btn-secondary">
          Refresh
        </button>
      </div>

      {/* Movement history table */}
      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Timestamp</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Type</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Item</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Batch</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Qty</th>
              <th className="text-right px-4 py-3 font-medium text-neutral-600">Balance</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Reason</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {events?.content.map((evt) => (
              <tr key={evt.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3 text-neutral-500 text-xs">
                  {new Date(evt.timestamp).toLocaleString()}
                </td>
                <td className="px-4 py-3">
                  <span className={MOVEMENT_BADGE[evt.movementType]}>
                    {evt.movementType}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="text-neutral-900 font-medium">{evt.itemName}</div>
                  <div className="text-neutral-500 text-xs font-mono">{evt.itemCode}</div>
                </td>
                <td className="px-4 py-3 text-neutral-600 font-mono text-xs">
                  {evt.batchNumber || "-"}
                </td>
                <td className={`px-4 py-3 text-right font-medium ${
                  evt.quantity > 0 ? "text-primary" : "text-red-600"
                }`}>
                  {evt.quantity > 0 ? `+${evt.quantity}` : evt.quantity}
                </td>
                <td className="px-4 py-3 text-right text-neutral-600">
                  {evt.balanceAfter}
                </td>
                <td className="px-4 py-3 text-neutral-500 text-xs max-w-[200px] truncate">
                  {evt.reason || evt.notes || "-"}
                </td>
              </tr>
            ))}
            {(!events || events.content.length === 0) && (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                  No movement events found for the selected filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-3 text-xs text-neutral-400">
        Showing {events?.content.length ?? 0} of {events?.totalElements ?? 0} event{(events?.totalElements ?? 0) !== 1 ? "s" : ""}
      </div>
    </div>
  );
}
