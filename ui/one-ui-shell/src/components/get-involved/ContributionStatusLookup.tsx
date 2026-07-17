"use client";

/**
 * Claim-code status lookup for anonymous "Get Involved" contributions (gateway ADR W4).
 * Mirrors the report claim-code lookup pattern. The claim code is the ONLY key to a
 * contribution's status — no account, no PII.
 *
 * Backend: GET /internal/v1/public/gateway/get-involved/contributions/{claimCode}
 *   -> { reference, type, status, moderationState, supportCount, createdAt, updatedAt }
 */

import { useState } from "react";
import { apiClient } from "@/lib/api-client";

interface ContributionStatus {
  reference?: string;
  type?: string;
  status?: string;
  moderationState?: string;
  supportCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

const STATUS_LABEL: Record<string, string> = {
  RECEIVED: "Received — waiting for review",
  UNDER_REVIEW: "Being reviewed",
  ACKNOWLEDGED: "Acknowledged",
  IN_DISCOVERY: "In discovery",
  PLANNED: "Planned",
  IN_PROGRESS: "Being built",
  RELEASED: "Released",
  MERGED: "Merged with a related idea",
  NOT_FEASIBLE: "Not feasible right now",
};

function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.status === 404) return "That claim code was not found. Check it and try again.";
    if (obj.error?.message) return obj.error.message;
  }
  return "We could not check that idea just now. Please try again later.";
}

export function ContributionStatusLookup() {
  const [claimCode, setClaimCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<ContributionStatus | null>(null);

  const lookup = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setStatus(null);
    try {
      const res = await apiClient.get<ContributionStatus>(
        `/internal/v1/public/gateway/get-involved/contributions/${encodeURIComponent(claimCode.trim())}`,
      );
      setStatus(res);
    } catch (e2) {
      setError(errMessage(e2));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div data-testid="contribution-status-lookup">
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
        <dl className="mt-4 rounded-xl border border-slate-200 bg-white p-5" data-testid="contribution-status-result">
          <div className="flex items-baseline justify-between">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Reference</dt>
            <dd className="font-mono text-sm font-semibold text-slate-900">{status.reference}</dd>
          </div>
          <div className="mt-2 flex items-baseline justify-between">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Status</dt>
            <dd className="text-sm font-semibold text-emerald-700">
              {STATUS_LABEL[status.status ?? ""] ?? status.status}
            </dd>
          </div>
          {typeof status.supportCount === "number" && status.supportCount > 0 && (
            <div className="mt-2 flex items-baseline justify-between">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Support</dt>
              <dd className="text-sm text-slate-700">{status.supportCount} supporters</dd>
            </div>
          )}
          {status.updatedAt && (
            <div className="mt-2 flex items-baseline justify-between">
              <dt className="text-xs uppercase tracking-wide text-slate-500">Last updated</dt>
              <dd className="text-sm text-slate-700">{new Date(status.updatedAt).toLocaleDateString()}</dd>
            </div>
          )}
        </dl>
      )}
    </div>
  );
}
