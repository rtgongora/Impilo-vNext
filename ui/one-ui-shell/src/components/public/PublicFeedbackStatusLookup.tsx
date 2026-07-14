"use client";

/** Claim-code status lookup for anonymous public reports (gateway ADR W4). */

import { useState } from "react";
import { apiClient } from "@/lib/api-client";

interface PublicCaseStatus {
  caseReference?: string;
  caseType?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
}

const STATUS_LABEL: Record<string, string> = {
  SUBMITTED: "Received — waiting for review",
  ACKNOWLEDGED: "Acknowledged",
  IN_REVIEW: "Being reviewed",
  INVESTIGATING: "Being investigated",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
};

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.status === 404) return "That claim code was not found. Check it and try again.";
    if (obj.error?.message) return obj.error.message;
  }
  return "We could not check that report just now. Please try again later.";
}

export function PublicFeedbackStatusLookup() {
  const [claimCode, setClaimCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<PublicCaseStatus | null>(null);

  const lookup = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setStatus(null);
    try {
      const res = await apiClient.get<PublicCaseStatus>(
        `/internal/v1/public/gateway/feedback/${encodeURIComponent(claimCode.trim())}`,
      );
      setStatus(res);
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div data-testid="feedback-status-lookup">
      <form onSubmit={lookup} className="flex gap-2">
        <input
          value={claimCode}
          onChange={(e) => setClaimCode(e.target.value)}
          required
          maxLength={64}
          placeholder="Your claim code"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 font-mono text-sm tracking-wider"
        />
        <button
          type="submit"
          disabled={loading || !claimCode.trim()}
          className="shrink-0 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
        >
          {loading ? "Checking…" : "Check"}
        </button>
      </form>

      {error && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
          {error}
        </div>
      )}

      {status && (
        <dl className="mt-4 rounded-xl border border-slate-200 bg-white p-5" data-testid="feedback-status-result">
          <div className="flex items-baseline justify-between">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Reference</dt>
            <dd className="font-mono text-sm font-semibold text-slate-900">{status.caseReference}</dd>
          </div>
          <div className="mt-2 flex items-baseline justify-between">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Status</dt>
            <dd className="text-sm font-semibold text-emerald-700">
              {STATUS_LABEL[status.status ?? ""] ?? status.status}
            </dd>
          </div>
          {status.updatedAt && (
            <div className="mt-2 flex items-baseline justify-between">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Last updated</dt>
              <dd className="text-sm text-slate-700">
                {new Date(status.updatedAt).toLocaleDateString()}
              </dd>
            </div>
          )}
        </dl>
      )}
    </div>
  );
}
