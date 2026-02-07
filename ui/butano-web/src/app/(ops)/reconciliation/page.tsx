"use client";

import React, { useCallback, useEffect, useState } from "react";
import { butanoApi, type ReconciliationStatus } from "@/lib/butanoApi";

/**
 * Status badge styling for reconciliation jobs.
 */
const STATUS_STYLES: Record<string, string> = {
  PENDING: "bg-info/10 text-info",
  IN_PROGRESS: "bg-warning/10 text-warning",
  COMPLETED: "bg-success/10 text-success",
  FAILED: "bg-danger/10 text-danger",
};

/**
 * Reconciliation Queue — shows all reconciliation jobs with auto-refresh
 * for in-progress items.
 *
 * NOTE: In production, this would paginate through a list endpoint.
 * For now, the page polls individual known job IDs from local state.
 * The initial list comes from a hypothetical list endpoint; here we
 * use a mock array that gets populated via the trigger page.
 */
export default function ReconciliationQueuePage() {
  const [jobs, setJobs] = useState<ReconciliationStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadJobs = useCallback(async () => {
    try {
      // If we have known job IDs, refresh their statuses
      if (jobs.length > 0) {
        const updated = await Promise.all(
          jobs.map(async (job) => {
            try {
              return await butanoApi.getReconciliationStatus(job.id);
            } catch {
              return job; // Keep stale data on error
            }
          }),
        );
        setJobs(updated);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refresh jobs");
    } finally {
      setLoading(false);
    }
  }, [jobs]);

  // Load from sessionStorage on mount (jobs added by trigger page)
  useEffect(() => {
    try {
      const stored = sessionStorage.getItem("butano_reconciliation_jobs");
      if (stored) {
        setJobs(JSON.parse(stored));
      }
    } catch {
      // Ignore parse errors
    }
    setLoading(false);
  }, []);

  // Persist jobs to sessionStorage whenever they change
  useEffect(() => {
    if (jobs.length > 0) {
      sessionStorage.setItem("butano_reconciliation_jobs", JSON.stringify(jobs));
    }
  }, [jobs]);

  // Auto-refresh every 10 seconds if any jobs are in progress
  useEffect(() => {
    const hasInProgress = jobs.some(
      (j) => j.status === "PENDING" || j.status === "IN_PROGRESS",
    );
    if (!hasInProgress) return;

    const interval = setInterval(loadJobs, 10_000);
    return () => clearInterval(interval);
  }, [jobs, loadJobs]);

  return (
    <div className="max-w-6xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Reconciliation Queue
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Monitor CPID reconciliation jobs. In-progress items auto-refresh every 10 seconds.
        </p>
      </div>

      {error && (
        <div className="bg-danger/5 border border-danger/20 text-danger text-sm rounded-lg px-4 py-3 mb-6">
          {error}
        </div>
      )}

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-100 bg-neutral-50">
              <th className="px-4 py-3 text-left font-medium text-neutral-500">ID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Old CPID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">New CPID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Status</th>
              <th className="px-4 py-3 text-right font-medium text-neutral-500">Resources Updated</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Requested At</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Completed At</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-neutral-500">
                  Loading...
                </td>
              </tr>
            ) : jobs.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-neutral-500">
                  No reconciliation jobs found. Use &quot;Trigger Reconciliation&quot; to start one.
                </td>
              </tr>
            ) : (
              jobs.map((job) => (
                <tr key={job.id} className="border-b border-neutral-50 hover:bg-neutral-50">
                  <td className="px-4 py-3 font-mono text-xs">{job.id.substring(0, 8)}...</td>
                  <td className="px-4 py-3 font-mono text-xs">{job.oldCpid}</td>
                  <td className="px-4 py-3 font-mono text-xs">{job.newCpid}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                        STATUS_STYLES[job.status] ?? "bg-neutral-100 text-neutral-700"
                      }`}
                    >
                      {job.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{job.resourcesUpdated}</td>
                  <td className="px-4 py-3 text-neutral-500 text-xs">
                    {new Date(job.requestedAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-neutral-500 text-xs">
                    {job.completedAt
                      ? new Date(job.completedAt).toLocaleString()
                      : "\u2014"}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {jobs.some((j) => j.status === "PENDING" || j.status === "IN_PROGRESS") && (
        <p className="text-xs text-neutral-400 mt-3 text-center">
          Auto-refreshing in-progress jobs every 10 seconds...
        </p>
      )}
    </div>
  );
}
