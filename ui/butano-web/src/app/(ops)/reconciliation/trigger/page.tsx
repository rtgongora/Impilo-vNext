"use client";

import React, { useState } from "react";
import { butanoApi, type ReconciliationStatus } from "@/lib/butanoApi";

/**
 * Trigger Reconciliation — form to submit a CPID reconciliation request.
 * Stores the result in sessionStorage so the queue page can pick it up.
 */
export default function TriggerReconciliationPage() {
  const [oldCpid, setOldCpid] = useState("");
  const [newCpid, setNewCpid] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<ReconciliationStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!oldCpid.trim() || !newCpid.trim() || !reason.trim()) return;

    setSubmitting(true);
    setError(null);
    setResult(null);

    try {
      const status = await butanoApi.triggerReconciliation({
        oldCpid: oldCpid.trim(),
        newCpid: newCpid.trim(),
        reason: reason.trim(),
      });
      setResult(status);

      // Persist to sessionStorage for the queue page
      try {
        const stored = sessionStorage.getItem("butano_reconciliation_jobs");
        const jobs: ReconciliationStatus[] = stored ? JSON.parse(stored) : [];
        jobs.unshift(status);
        sessionStorage.setItem("butano_reconciliation_jobs", JSON.stringify(jobs));
      } catch {
        // Ignore storage errors
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to trigger reconciliation");
    } finally {
      setSubmitting(false);
    }
  };

  const statusColor: Record<string, string> = {
    PENDING: "bg-info/10 text-info border-info/20",
    IN_PROGRESS: "bg-warning/10 text-warning border-warning/20",
    COMPLETED: "bg-success/10 text-success border-success/20",
    FAILED: "bg-danger/10 text-danger border-danger/20",
  };

  return (
    <div className="max-w-2xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Trigger Reconciliation
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Submit a CPID reconciliation request to re-key all FHIR resources from the old CPID to the new one
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6 space-y-4">
        <div>
          <label htmlFor="oldCpid" className="block text-sm font-medium text-neutral-700 mb-1">
            Old CPID (O-CPID)
          </label>
          <input
            id="oldCpid"
            type="text"
            value={oldCpid}
            onChange={(e) => setOldCpid(e.target.value)}
            placeholder="CPID-OLD-00001"
            className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
            required
          />
          <p className="text-xs text-neutral-400 mt-1">
            The deprecated CPID that will be replaced across all FHIR resources
          </p>
        </div>

        <div>
          <label htmlFor="newCpid" className="block text-sm font-medium text-neutral-700 mb-1">
            New CPID
          </label>
          <input
            id="newCpid"
            type="text"
            value={newCpid}
            onChange={(e) => setNewCpid(e.target.value)}
            placeholder="CPID-NEW-00002"
            className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary"
            required
          />
          <p className="text-xs text-neutral-400 mt-1">
            The surviving CPID that replaces the old one
          </p>
        </div>

        <div>
          <label htmlFor="reason" className="block text-sm font-medium text-neutral-700 mb-1">
            Reason
          </label>
          <textarea
            id="reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="VITO identity merge — duplicate detected"
            rows={3}
            className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary resize-none"
            required
          />
        </div>

        <button
          type="submit"
          disabled={submitting || !oldCpid.trim() || !newCpid.trim() || !reason.trim()}
          className="w-full px-4 py-2.5 text-sm font-medium text-white bg-brand-primary rounded-lg hover:bg-brand-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          {submitting ? "Submitting..." : "Submit Reconciliation Request"}
        </button>
      </form>

      {/* Error */}
      {error && (
        <div className="mt-6 bg-danger/5 border border-danger/20 text-danger text-sm rounded-lg px-4 py-3">
          {error}
        </div>
      )}

      {/* Result */}
      {result && (
        <div className={`mt-6 rounded-[12px] border p-5 ${statusColor[result.status] ?? "bg-neutral-50 border-neutral-200"}`}>
          <h2 className="text-sm font-semibold mb-3">Reconciliation Accepted</h2>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <dt className="font-medium opacity-70">Job ID</dt>
            <dd className="font-mono text-xs">{result.id}</dd>
            <dt className="font-medium opacity-70">Status</dt>
            <dd className="font-semibold">{result.status}</dd>
            <dt className="font-medium opacity-70">Old CPID</dt>
            <dd className="font-mono text-xs">{result.oldCpid}</dd>
            <dt className="font-medium opacity-70">New CPID</dt>
            <dd className="font-mono text-xs">{result.newCpid}</dd>
            <dt className="font-medium opacity-70">Resources Updated</dt>
            <dd>{result.resourcesUpdated}</dd>
            <dt className="font-medium opacity-70">Requested At</dt>
            <dd className="text-xs">{new Date(result.requestedAt).toLocaleString()}</dd>
          </dl>
        </div>
      )}
    </div>
  );
}
