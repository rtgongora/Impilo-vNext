"use client";

import React, { useCallback, useEffect, useState } from "react";
import { vitoApi, type IssuanceRequest } from "@/lib/vitoApi";

type IssuanceTab = "SUBMITTED" | "PROOFING" | "APPROVED" | "ISSUED";

/**
 * Issuance Queue — operators process ID issuance requests through
 * the lifecycle: SUBMITTED → PROOFING → APPROVED → ISSUED.
 * Each state transition is audited and persisted via the outbox pattern.
 */
export default function IssuanceQueuePage() {
  const [activeTab, setActiveTab] = useState<IssuanceTab>("SUBMITTED");
  const [requests, setRequests] = useState<IssuanceRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");

  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const data = await vitoApi.getIssuanceQueue(activeTab, 0, 50);
      setRequests(data.items ?? []);
    } catch {
      setRequests([]);
    } finally {
      setLoading(false);
    }
  }, [activeTab]);

  useEffect(() => {
    loadQueue();
  }, [loadQueue]);

  const handleStartProofing = async (requestId: number) => {
    try {
      await vitoApi.startProofing(requestId);
      await loadQueue();
    } catch {
      // Error handling via toast in production
    }
  };

  const handleApprove = async (requestId: number) => {
    try {
      await vitoApi.approveIssuance(requestId);
      await loadQueue();
    } catch {
      // Error handling via toast in production
    }
  };

  const handleIssue = async (requestId: number) => {
    try {
      await vitoApi.issueId(requestId);
      await loadQueue();
    } catch {
      // Error handling via toast in production
    }
  };

  const handleReject = async (requestId: number) => {
    if (!rejectionReason.trim()) return;
    try {
      await vitoApi.rejectIssuance(requestId, rejectionReason.trim());
      setRejectingId(null);
      setRejectionReason("");
      await loadQueue();
    } catch {
      // Error handling via toast in production
    }
  };

  const stateBadge = (state: string) => {
    const styles: Record<string, string> = {
      SUBMITTED: "bg-info/10 text-info",
      PROOFING: "bg-warning/10 text-warning",
      APPROVED: "bg-success/10 text-success",
      ISSUED: "bg-success/10 text-success",
      DELIVERED: "bg-brand-primary/10 text-brand-primary",
      REJECTED: "bg-danger/10 text-danger",
      EXPIRED: "bg-neutral-100 text-neutral-500",
    };
    return styles[state] ?? "bg-neutral-100 text-neutral-900";
  };

  const typeBadge = (type: string) => {
    const styles: Record<string, string> = {
      NEW: "bg-brand-primary/10 text-brand-primary",
      REPLACEMENT: "bg-warning/10 text-warning",
      RECOVERY: "bg-info/10 text-info",
    };
    return styles[type] ?? "bg-neutral-100 text-neutral-900";
  };

  const tabs: { key: IssuanceTab; label: string }[] = [
    { key: "SUBMITTED", label: "Submitted" },
    { key: "PROOFING", label: "Proofing" },
    { key: "APPROVED", label: "Approved" },
    { key: "ISSUED", label: "Issued" },
  ];

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Issuance Queue
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Process ID issuance requests through proofing, approval, and issuance stages
        </p>
      </div>

      {/* Tab bar */}
      <div className="flex gap-1 mb-4 bg-neutral-100 p-1 rounded-[12px] w-fit">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 text-sm font-medium rounded-[8px] transition-colors ${
              activeTab === tab.key
                ? "bg-white text-neutral-900 shadow-subtle"
                : "text-neutral-500 hover:text-neutral-900"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Rejection reason dialog */}
      {rejectingId !== null && (
        <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 mb-4">
          <h2 className="text-base font-semibold text-neutral-900 mb-3">
            Reject Issuance Request #{rejectingId}
          </h2>
          <p className="text-sm text-neutral-500 mb-3">
            Provide a reason for rejecting this issuance request.
          </p>
          <textarea
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            placeholder="Enter rejection reason..."
            rows={3}
            className="w-full px-4 py-2.5 text-sm border border-neutral-200 rounded-[8px] bg-white focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary placeholder:text-neutral-400 resize-none"
          />
          <div className="flex gap-2 mt-3">
            <button
              onClick={() => handleReject(rejectingId)}
              disabled={!rejectionReason.trim()}
              className="px-4 py-2 text-sm font-medium text-white bg-danger rounded-[8px] hover:bg-danger/90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Confirm Rejection
            </button>
            <button
              onClick={() => { setRejectingId(null); setRejectionReason(""); }}
              className="px-4 py-2 text-sm font-medium text-neutral-500 bg-neutral-100 rounded-[8px] hover:bg-neutral-200"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Queue table */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-100 bg-neutral-50">
              <th className="px-4 py-3 text-left font-medium text-neutral-500">ID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Health ID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Type</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Channel</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">State</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Assigned To</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Submitted At</th>
              <th className="px-4 py-3 text-right font-medium text-neutral-500">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-neutral-500">Loading...</td>
              </tr>
            ) : requests.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-neutral-500">
                  No requests in {activeTab.toLowerCase()} state
                </td>
              </tr>
            ) : (
              requests.map((req) => (
                <tr key={req.id} className="border-b border-neutral-50 hover:bg-neutral-50">
                  <td className="px-4 py-3 font-mono text-xs">{req.id}</td>
                  <td className="px-4 py-3 font-mono text-xs">{req.healthId.substring(0, 8)}...</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${typeBadge(req.type)}`}>
                      {req.type}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-neutral-500">{req.channel}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${stateBadge(req.state)}`}>
                      {req.state}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-neutral-500">{req.assignedTo ?? "—"}</td>
                  <td className="px-4 py-3 text-neutral-500">
                    {new Date(req.submittedAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {activeTab === "SUBMITTED" && (
                      <button
                        onClick={() => handleStartProofing(req.id)}
                        className="px-3 py-1 text-xs font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90"
                      >
                        Start Proofing
                      </button>
                    )}
                    {activeTab === "PROOFING" && (
                      <div className="flex gap-2 justify-end">
                        <button
                          onClick={() => handleApprove(req.id)}
                          className="px-3 py-1 text-xs font-medium text-white bg-success rounded-[8px] hover:bg-success/90"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => { setRejectingId(req.id); setRejectionReason(""); }}
                          className="px-3 py-1 text-xs font-medium text-danger bg-danger/10 rounded-[8px] hover:bg-danger/20"
                        >
                          Reject
                        </button>
                      </div>
                    )}
                    {activeTab === "APPROVED" && (
                      <button
                        onClick={() => handleIssue(req.id)}
                        className="px-3 py-1 text-xs font-medium text-white bg-success rounded-[8px] hover:bg-success/90"
                      >
                        Issue
                      </button>
                    )}
                    {activeTab === "ISSUED" && (
                      <span className="text-xs text-neutral-400">Completed</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
