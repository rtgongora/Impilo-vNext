"use client";

import { useState, useCallback } from "react";
import { mushexApi, type Claim, type ClaimDetail, type ClaimStatus } from "@/lib/mushexApi";

const STATUS_BADGE: Record<ClaimStatus, string> = {
  DRAFT: "badge-draft",
  SUBMITTED: "badge-submitted",
  RECEIVED: "badge-received",
  ADJUDICATED: "badge-adjudicated",
  PAID: "badge-paid",
  PARTIAL: "badge-partial",
  REJECTED: "badge-rejected",
  RESUBMIT_PENDING: "badge-resubmit-pending",
};

export default function ClaimsPage() {
  const [claimId, setClaimId] = useState("");
  const [claimDetail, setClaimDetail] = useState<ClaimDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [disputeReason, setDisputeReason] = useState("");
  const [showDispute, setShowDispute] = useState(false);

  const lookupClaim = useCallback(async () => {
    if (!claimId.trim()) {
      setError("Enter a claim ID to search.");
      return;
    }
    setLoading(true);
    setError(null);
    setSuccessMessage(null);
    setClaimDetail(null);
    setShowDispute(false);
    try {
      const data = await mushexApi.getClaim(claimId.trim());
      setClaimDetail(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load claim");
    } finally {
      setLoading(false);
    }
  }, [claimId]);

  const submitClaim = useCallback(async () => {
    if (!claimDetail) return;
    setActionLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await mushexApi.submitClaim(claimDetail.claim.claimId);
      setClaimDetail({ ...claimDetail, claim: updated });
      setSuccessMessage("Claim submitted successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to submit claim");
    } finally {
      setActionLoading(false);
    }
  }, [claimDetail]);

  const disputeClaimAction = useCallback(async () => {
    if (!claimDetail || !disputeReason.trim()) {
      setError("A dispute reason is required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    setSuccessMessage(null);
    try {
      const updated = await mushexApi.disputeClaim(
        claimDetail.claim.claimId,
        disputeReason.trim()
      );
      setClaimDetail({ ...claimDetail, claim: updated });
      setSuccessMessage("Claim disputed successfully.");
      setShowDispute(false);
      setDisputeReason("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to dispute claim");
    } finally {
      setActionLoading(false);
    }
  }, [claimDetail, disputeReason]);

  const parseTotals = (totals: string | null): Record<string, number> => {
    if (!totals) return {};
    try {
      return JSON.parse(totals);
    } catch {
      return {};
    }
  };

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Claims Management</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Look up insurance claims by ID, submit, or dispute them.
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
          value={claimId}
          onChange={(e) => setClaimId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && lookupClaim()}
          placeholder="Enter Claim ID (ULID)..."
          className="input-field flex-1"
        />
        <button onClick={lookupClaim} disabled={loading} className="btn-primary">
          {loading ? "Loading..." : "Search"}
        </button>
      </div>

      {/* Claim detail */}
      {claimDetail && (
        <>
          <div className="card p-6 mb-4">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <h2 className="text-lg font-semibold text-neutral-900 font-mono">
                  {claimDetail.claim.claimId}
                </h2>
                <span className={STATUS_BADGE[claimDetail.claim.status]}>
                  {claimDetail.claim.status.replace(/_/g, " ")}
                </span>
              </div>
              <div className="flex gap-2">
                {claimDetail.claim.status === "DRAFT" && (
                  <button
                    onClick={submitClaim}
                    disabled={actionLoading}
                    className="btn-primary text-xs"
                  >
                    Submit Claim
                  </button>
                )}
                {["ADJUDICATED", "PARTIAL", "REJECTED"].includes(claimDetail.claim.status) && (
                  <button
                    onClick={() => setShowDispute(!showDispute)}
                    className="btn-danger text-xs"
                  >
                    Dispute
                  </button>
                )}
              </div>
            </div>

            <div className="grid grid-cols-4 gap-4 text-sm">
              <div>
                <span className="text-neutral-500">Bill ID</span>
                <p className="font-mono text-xs font-medium">{claimDetail.claim.billId}</p>
              </div>
              <div>
                <span className="text-neutral-500">Insurer ID</span>
                <p className="font-mono text-xs">{claimDetail.claim.insurerId}</p>
              </div>
              <div>
                <span className="text-neutral-500">Facility</span>
                <p className="font-mono text-xs">{claimDetail.claim.facilityId}</p>
              </div>
              <div>
                <span className="text-neutral-500">External Ref</span>
                <p className="font-mono text-xs">{claimDetail.claim.externalRef || "---"}</p>
              </div>
            </div>

            {claimDetail.claim.totals && (
              <div className="mt-4 pt-4 border-t border-neutral-200">
                <h3 className="text-sm font-semibold text-neutral-700 mb-2">Totals</h3>
                <div className="grid grid-cols-4 gap-3">
                  {Object.entries(parseTotals(claimDetail.claim.totals)).map(([key, value]) => (
                    <div key={key} className="bg-neutral-50 rounded-lg p-3 border border-neutral-200">
                      <span className="text-xs text-neutral-500 uppercase tracking-wider">
                        {key.replace(/_/g, " ")}
                      </span>
                      <p className="text-lg font-semibold text-neutral-900 mt-1 font-mono">
                        {Number(value).toFixed(2)}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="grid grid-cols-3 gap-4 mt-4 pt-4 border-t border-neutral-200 text-xs text-neutral-500">
              <div>
                Submitted: {claimDetail.claim.submittedAt
                  ? new Date(claimDetail.claim.submittedAt).toLocaleString()
                  : "Not submitted"}
              </div>
              <div>
                Adjudicated: {claimDetail.claim.adjudicatedAt
                  ? new Date(claimDetail.claim.adjudicatedAt).toLocaleString()
                  : "Not adjudicated"}
              </div>
              <div>
                Created: {new Date(claimDetail.claim.createdAt).toLocaleString()}
              </div>
            </div>
          </div>

          {/* Dispute form */}
          {showDispute && (
            <div className="card p-6 mb-4 border-red-300">
              <h3 className="text-sm font-semibold text-red-700 mb-3">Dispute Claim</h3>
              <div className="flex gap-3">
                <input
                  type="text"
                  value={disputeReason}
                  onChange={(e) => setDisputeReason(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && disputeClaimAction()}
                  placeholder="Enter dispute reason..."
                  className="input-field flex-1"
                />
                <button onClick={disputeClaimAction} disabled={actionLoading} className="btn-danger">
                  {actionLoading ? "Submitting..." : "Submit Dispute"}
                </button>
                <button
                  onClick={() => { setShowDispute(false); setDisputeReason(""); }}
                  className="btn-secondary"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          {/* Claim events */}
          {claimDetail.events.length > 0 && (
            <div className="card overflow-hidden mb-4">
              <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
                <h3 className="text-sm font-semibold text-neutral-700">
                  Events ({claimDetail.events.length})
                </h3>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-neutral-200">
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Event ID</th>
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Type</th>
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Payload</th>
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {claimDetail.events.map((event) => (
                    <tr key={event.id} className="hover:bg-neutral-50 transition-colors">
                      <td className="px-4 py-2 font-mono text-xs">{event.id}</td>
                      <td className="px-4 py-2">
                        <span className="badge bg-indigo-100 text-indigo-800">{event.eventType}</span>
                      </td>
                      <td className="px-4 py-2 text-xs text-neutral-500 max-w-xs truncate">
                        {event.payload || "---"}
                      </td>
                      <td className="px-4 py-2 text-xs text-neutral-500">
                        {new Date(event.createdAt).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Attachments */}
          {claimDetail.attachments.length > 0 && (
            <div className="card overflow-hidden">
              <div className="px-4 py-3 bg-neutral-50 border-b border-neutral-200">
                <h3 className="text-sm font-semibold text-neutral-700">
                  Attachments ({claimDetail.attachments.length})
                </h3>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-neutral-200">
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">ID</th>
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Document ID</th>
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Type</th>
                    <th className="text-left px-4 py-2 font-medium text-neutral-600 text-xs">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {claimDetail.attachments.map((att) => (
                    <tr key={att.id} className="hover:bg-neutral-50 transition-colors">
                      <td className="px-4 py-2 font-mono text-xs">{att.id}</td>
                      <td className="px-4 py-2 font-mono text-xs">{att.landelaDocId}</td>
                      <td className="px-4 py-2 text-xs">{att.docType}</td>
                      <td className="px-4 py-2 text-xs text-neutral-500">
                        {new Date(att.createdAt).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {!claimDetail && !loading && !error && (
        <div className="card p-12 text-center text-neutral-500 text-sm">
          Enter a claim ID above to view claim details, submit, or dispute.
        </div>
      )}
    </div>
  );
}
