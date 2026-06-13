"use client";

import { useState } from "react";
import {
  inventoryApi,
  type Requisition,
  type RequisitionStatus,
  type RequisitionPriority,
} from "@/lib/inventoryApi";

const STATUS_BADGE: Record<RequisitionStatus, string> = {
  DRAFT: "badge-status-draft",
  SUBMITTED: "badge-status-submitted",
  APPROVED: "badge-status-approved",
  REJECTED: "badge-status-rejected",
  FULFILLED: "badge-status-fulfilled",
  PARTIALLY_FULFILLED: "badge bg-blue-100 text-blue-800",
  CANCELLED: "badge-status-cancelled",
};

const PRIORITY_BADGE: Record<RequisitionPriority, string> = {
  ROUTINE: "badge bg-neutral-100 text-neutral-700",
  URGENT: "badge bg-amber-100 text-warning-foreground",
  EMERGENCY: "badge bg-red-100 text-red-800",
};

type ViewMode = "LIST" | "CREATE" | "DETAIL";

interface ReqLineInput {
  itemCode: string;
  itemName: string;
  quantityRequested: number;
}

export default function RequisitionsPage() {
  const [requisition, setRequisition] = useState<Requisition | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("LIST");
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Load by ID
  const [reqIdInput, setReqIdInput] = useState("");

  // Create form
  const [createData, setCreateData] = useState({
    requestingFacilityId: "",
    supplyingFacilityId: "",
    priority: "ROUTINE" as RequisitionPriority,
    notes: "",
  });
  const [createLines, setCreateLines] = useState<ReqLineInput[]>([
    { itemCode: "", itemName: "", quantityRequested: 1 },
  ]);

  // Reject dialog
  const [showReject, setShowReject] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const loadRequisition = async () => {
    // Requisitions use the same get pattern but the API is createRequisition-based
    // For now, we'll create and manage requisitions through actions
    setError(
      `Load by ID (${reqIdInput}): use the create/action flow to manage requisitions.`,
    );
  };

  const handleCreate = async () => {
    if (!createData.requestingFacilityId || !createData.supplyingFacilityId) {
      setError("Requesting and supplying facility IDs are required.");
      return;
    }
    const validLines = createLines.filter((l) => l.itemCode && l.itemName && l.quantityRequested > 0);
    if (validLines.length === 0) {
      setError("At least one line item is required.");
      return;
    }

    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.createRequisition({
        requestingFacilityId: createData.requestingFacilityId,
        supplyingFacilityId: createData.supplyingFacilityId,
        priority: createData.priority,
        lines: validLines,
        notes: createData.notes || undefined,
      });
      setRequisition(data);
      setSuccessMessage("Requisition created successfully.");
      setViewMode("DETAIL");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create requisition");
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubmit = async () => {
    if (!requisition) return;
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.submitRequisition(requisition.id);
      setRequisition(data);
      setSuccessMessage("Requisition submitted for approval.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit requisition");
    } finally {
      setActionLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!requisition) return;
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.approveRequisition(requisition.id);
      setRequisition(data);
      setSuccessMessage("Requisition approved.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to approve requisition");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!requisition || !rejectReason.trim()) {
      setError("Rejection reason is required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      const data = await inventoryApi.rejectRequisition(requisition.id, { reason: rejectReason });
      setRequisition(data);
      setSuccessMessage("Requisition rejected.");
      setShowReject(false);
      setRejectReason("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reject requisition");
    } finally {
      setActionLoading(false);
    }
  };

  const addLine = () => {
    setCreateLines([...createLines, { itemCode: "", itemName: "", quantityRequested: 1 }]);
  };

  const removeLine = (index: number) => {
    if (createLines.length <= 1) return;
    setCreateLines(createLines.filter((_, i) => i !== index));
  };

  const updateLine = (index: number, field: keyof ReqLineInput, value: string | number) => {
    const updated = [...createLines];
    updated[index] = { ...updated[index], [field]: value };
    setCreateLines(updated);
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Requisitions</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Create, submit, approve, reject, and fulfill stock requisitions.
          </p>
        </div>
        <div className="flex gap-2">
          {viewMode !== "CREATE" && (
            <button onClick={() => setViewMode("CREATE")} className="btn-primary">
              New Requisition
            </button>
          )}
          {viewMode === "DETAIL" && (
            <button onClick={() => { setViewMode("LIST"); setRequisition(null); }} className="btn-secondary">
              Back
            </button>
          )}
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

      {/* LIST VIEW */}
      {viewMode === "LIST" && (
        <div className="card p-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Load Requisition</h2>
          <p className="text-sm text-neutral-500">
            Enter a requisition ID to view and manage it, or create a new one.
          </p>
          <div className="flex gap-3">
            <input
              value={reqIdInput}
              onChange={(e) => setReqIdInput(e.target.value)}
              placeholder="Requisition UUID"
              className="input-field flex-1"
            />
            <button
              onClick={() => reqIdInput && void loadRequisition()}
              disabled={!reqIdInput}
              className="btn-primary"
            >
              Load
            </button>
          </div>
        </div>
      )}

      {/* CREATE VIEW */}
      {viewMode === "CREATE" && (
        <div className="card p-6 space-y-6">
          <h2 className="text-lg font-semibold text-neutral-900">Create Requisition</h2>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label htmlFor="req-from" className="block text-sm font-medium text-neutral-700 mb-1">
                Requesting Facility ID
              </label>
              <input
                id="req-from"
                value={createData.requestingFacilityId}
                onChange={(e) => setCreateData({ ...createData, requestingFacilityId: e.target.value })}
                placeholder="Facility UUID"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="req-to" className="block text-sm font-medium text-neutral-700 mb-1">
                Supplying Facility ID
              </label>
              <input
                id="req-to"
                value={createData.supplyingFacilityId}
                onChange={(e) => setCreateData({ ...createData, supplyingFacilityId: e.target.value })}
                placeholder="Facility UUID"
                className="input-field"
              />
            </div>
            <div>
              <label htmlFor="req-priority" className="block text-sm font-medium text-neutral-700 mb-1">
                Priority
              </label>
              <select
                id="req-priority"
                value={createData.priority}
                onChange={(e) => setCreateData({ ...createData, priority: e.target.value as RequisitionPriority })}
                className="select-field"
              >
                <option value="ROUTINE">Routine</option>
                <option value="URGENT">Urgent</option>
                <option value="EMERGENCY">Emergency</option>
              </select>
            </div>
          </div>

          {/* Line items */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-semibold text-neutral-700">Line Items</h3>
              <button onClick={addLine} className="text-sm text-amber-600 hover:text-warning-foreground font-medium">
                + Add Line
              </button>
            </div>
            <div className="space-y-3">
              {createLines.map((line, idx) => (
                <div key={idx} className="grid grid-cols-12 gap-3 items-end">
                  <div className="col-span-4">
                    {idx === 0 && (
                      <label className="block text-xs font-medium text-neutral-500 mb-1">Item Code</label>
                    )}
                    <input
                      value={line.itemCode}
                      onChange={(e) => updateLine(idx, "itemCode", e.target.value)}
                      placeholder="e.g., PARA-500MG"
                      className="input-field"
                    />
                  </div>
                  <div className="col-span-4">
                    {idx === 0 && (
                      <label className="block text-xs font-medium text-neutral-500 mb-1">Item Name</label>
                    )}
                    <input
                      value={line.itemName}
                      onChange={(e) => updateLine(idx, "itemName", e.target.value)}
                      placeholder="e.g., Paracetamol 500mg"
                      className="input-field"
                    />
                  </div>
                  <div className="col-span-3">
                    {idx === 0 && (
                      <label className="block text-xs font-medium text-neutral-500 mb-1">Qty Requested</label>
                    )}
                    <input
                      type="number"
                      min={1}
                      value={line.quantityRequested}
                      onChange={(e) => updateLine(idx, "quantityRequested", parseInt(e.target.value) || 1)}
                      className="input-field"
                    />
                  </div>
                  <div className="col-span-1">
                    {createLines.length > 1 && (
                      <button
                        onClick={() => removeLine(idx)}
                        className="text-red-500 hover:text-danger text-sm font-medium"
                        title="Remove line"
                      >
                        X
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div>
            <label htmlFor="req-notes" className="block text-sm font-medium text-neutral-700 mb-1">
              Notes (optional)
            </label>
            <textarea
              id="req-notes"
              value={createData.notes}
              onChange={(e) => setCreateData({ ...createData, notes: e.target.value })}
              placeholder="Additional notes..."
              rows={2}
              className="input-field"
            />
          </div>

          <div className="flex gap-2">
            <button onClick={handleCreate} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Creating..." : "Create Requisition"}
            </button>
            <button onClick={() => setViewMode("LIST")} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* DETAIL VIEW */}
      {viewMode === "DETAIL" && requisition && (
        <div className="space-y-6">
          <div className="card p-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-lg font-semibold text-neutral-900">
                  Requisition {requisition.requisitionNumber}
                </h2>
                <p className="text-xs text-neutral-500 font-mono mt-1">{requisition.id}</p>
              </div>
              <div className="flex gap-2">
                <span className={STATUS_BADGE[requisition.status]}>{requisition.status.replace("_", " ")}</span>
                <span className={PRIORITY_BADGE[requisition.priority]}>{requisition.priority}</span>
              </div>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm mb-4">
              <div>
                <span className="text-neutral-500">Requesting:</span>
                <span className="ml-2 font-medium text-xs font-mono">
                  {requisition.requestingFacilityName || requisition.requestingFacilityId}
                </span>
              </div>
              <div>
                <span className="text-neutral-500">Supplying:</span>
                <span className="ml-2 font-medium text-xs font-mono">
                  {requisition.supplyingFacilityName || requisition.supplyingFacilityId}
                </span>
              </div>
              <div>
                <span className="text-neutral-500">Created:</span>
                <span className="ml-2 text-xs">{new Date(requisition.createdAt).toLocaleString()}</span>
              </div>
              <div>
                <span className="text-neutral-500">Lines:</span>
                <span className="ml-2 font-medium">{requisition.lines.length}</span>
              </div>
            </div>

            {/* Action buttons */}
            <div className="flex gap-2 pt-4 border-t border-neutral-200">
              {requisition.status === "DRAFT" && (
                <button onClick={handleSubmit} disabled={actionLoading} className="btn-primary">
                  {actionLoading ? "Submitting..." : "Submit for Approval"}
                </button>
              )}
              {requisition.status === "SUBMITTED" && (
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
              <h3 className="text-lg font-semibold text-neutral-900">Reject Requisition</h3>
              <div>
                <label htmlFor="rejReason" className="block text-sm font-medium text-neutral-700 mb-1">
                  Reason
                </label>
                <textarea
                  id="rejReason"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Provide a reason..."
                  rows={3}
                  className="input-field"
                />
              </div>
              <div className="flex gap-2">
                <button onClick={handleReject} disabled={actionLoading} className="btn-danger">
                  {actionLoading ? "Rejecting..." : "Reject"}
                </button>
                <button onClick={() => { setShowReject(false); setRejectReason(""); }} className="btn-secondary">
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* Lines table */}
          <div className="card overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-200 bg-neutral-50">
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Item Code</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Name</th>
                  <th className="text-right px-4 py-3 font-medium text-neutral-600">Requested</th>
                  <th className="text-right px-4 py-3 font-medium text-neutral-600">Approved</th>
                  <th className="text-right px-4 py-3 font-medium text-neutral-600">Fulfilled</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Batch</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Expiry</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-neutral-100">
                {requisition.lines.map((line) => (
                  <tr key={line.id} className="hover:bg-neutral-50 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                      {line.itemCode}
                    </td>
                    <td className="px-4 py-3 text-neutral-900">{line.itemName}</td>
                    <td className="px-4 py-3 text-right text-neutral-600">{line.quantityRequested}</td>
                    <td className="px-4 py-3 text-right">
                      <span className={line.quantityApproved !== null ? "text-neutral-900 font-medium" : "text-neutral-400"}>
                        {line.quantityApproved ?? "-"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <span className={line.quantityFulfilled !== null ? "text-primary font-medium" : "text-neutral-400"}>
                        {line.quantityFulfilled ?? "-"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-neutral-600 font-mono text-xs">
                      {line.batchNumber || "-"}
                    </td>
                    <td className="px-4 py-3 text-neutral-500 text-xs">
                      {line.expiryDate || "-"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
