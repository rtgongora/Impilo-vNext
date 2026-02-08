"use client";

import { useState, useEffect, useCallback } from "react";
import {
  mushexApi,
  type FraudFlag,
  type FraudSeverity,
  type FraudKind,
  type PagedResponse,
} from "@/lib/mushexApi";

const SEVERITY_BADGE: Record<FraudSeverity, string> = {
  LOW: "badge-low",
  MEDIUM: "badge-medium",
  HIGH: "badge-high",
  CRITICAL: "badge-critical",
};

const KIND_LABEL: Record<FraudKind, string> = {
  DUPLICATE_PAYMENT: "Duplicate Payment",
  REPEATED_REFUND: "Repeated Refund",
  UNUSUAL_CLAIM_AMOUNT: "Unusual Claim Amount",
  ABNORMAL_FREQUENCY: "Abnormal Frequency",
  VELOCITY_BREACH: "Velocity Breach",
};

export default function FraudPage() {
  const [flags, setFlags] = useState<FraudFlag[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [severityFilter, setSeverityFilter] = useState<FraudSeverity | "">("");

  const loadFlags = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const params: { page: number; size: number; severity?: FraudSeverity } = {
        page,
        size: 20,
      };
      if (severityFilter) {
        params.severity = severityFilter;
      }
      const data: PagedResponse<FraudFlag> = await mushexApi.listFraudFlags(params);
      setFlags(data.items);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load fraud flags");
    } finally {
      setLoading(false);
    }
  }, [page, severityFilter]);

  useEffect(() => {
    loadFlags();
  }, [loadFlags]);

  const parseEvidence = (evidence: string | null): Record<string, unknown> | null => {
    if (!evidence) return null;
    try {
      return JSON.parse(evidence);
    } catch {
      return null;
    }
  };

  return (
    <div className="p-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Fraud Flags</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Review flagged transactions for potential fraud. Investigate evidence and take action.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <label className="text-xs font-medium text-neutral-600">Severity:</label>
          <select
            value={severityFilter}
            onChange={(e) => {
              setSeverityFilter(e.target.value as FraudSeverity | "");
              setPage(0);
            }}
            className="select-field w-40"
          >
            <option value="">All</option>
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
            <option value="CRITICAL">Critical</option>
          </select>
        </div>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {loading ? (
        <div className="p-8 text-center text-neutral-500 text-sm">Loading...</div>
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-neutral-200 bg-neutral-50">
                <th className="text-left px-4 py-3 font-medium text-neutral-600">ID</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Kind</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Severity</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Entity</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Evidence</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
                <th className="text-left px-4 py-3 font-medium text-neutral-600">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-100">
              {flags.map((flag) => {
                const evidence = parseEvidence(flag.evidence);
                return (
                  <tr key={flag.id} className="hover:bg-neutral-50 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs font-medium text-neutral-900">
                      {flag.id}
                    </td>
                    <td className="px-4 py-3 text-xs">
                      {KIND_LABEL[flag.kind] || flag.kind}
                    </td>
                    <td className="px-4 py-3">
                      <span className={SEVERITY_BADGE[flag.severity]}>
                        {flag.severity}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="text-xs">
                        <span className="text-neutral-500">{flag.entityType}:</span>{" "}
                        <span className="font-mono">{flag.entityId}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-xs text-neutral-500 max-w-xs">
                      {evidence ? (
                        <pre className="truncate font-mono text-[10px] bg-neutral-50 rounded p-1 border border-neutral-200">
                          {JSON.stringify(evidence, null, 0).substring(0, 80)}
                          {JSON.stringify(evidence).length > 80 ? "..." : ""}
                        </pre>
                      ) : (
                        "---"
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`badge ${
                        flag.status === "OPEN" ? "bg-red-100 text-red-800" :
                        flag.status === "INVESTIGATING" ? "bg-amber-100 text-amber-800" :
                        flag.status === "RESOLVED" ? "bg-emerald-100 text-emerald-800" :
                        flag.status === "DISMISSED" ? "bg-neutral-100 text-neutral-600" :
                        "bg-neutral-100 text-neutral-600"
                      }`}>
                        {flag.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-neutral-500">
                      {new Date(flag.createdAt).toLocaleString()}
                    </td>
                  </tr>
                );
              })}
              {flags.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-4 py-12 text-center text-neutral-500">
                    No fraud flags found
                    {severityFilter ? ` with severity ${severityFilter}` : ""}.
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
            {totalElements} flag{totalElements !== 1 ? "s" : ""} total
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

      <div className="mt-3 text-xs text-neutral-400">
        {flags.length} flag{flags.length !== 1 ? "s" : ""} on this page
      </div>
    </div>
  );
}
