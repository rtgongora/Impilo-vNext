"use client";

import type { AdminGovernanceActionResponse } from "@/lib/admin-governance/types";

interface OpaPrecheckPanelProps {
  result?: AdminGovernanceActionResponse | null;
  isLoading?: boolean;
  errorMessage?: string;
}

export function OpaPrecheckPanel({ result, isLoading, errorMessage }: OpaPrecheckPanelProps) {
  if (isLoading) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
        Running OPA precheck…
      </div>
    );
  }

  if (errorMessage) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
        Precheck unavailable — backend integration pending. Do not submit until precheck is available.
      </div>
    );
  }

  if (!result) return null;

  const allowed = result.status === "allowed" || result.status === "submitted" || result.status === "completed";
  const pending = result.status === "pending" || result.status === "pending_backend";

  return (
    <div
      className={`rounded-xl border px-4 py-3 text-sm ${
        result.status === "denied"
          ? "border-rose-200 bg-rose-50 text-rose-950"
          : pending
            ? "border-amber-200 bg-amber-50 text-amber-950"
            : "border-emerald-200 bg-emerald-50 text-emerald-950"
      }`}
    >
      <p className="font-semibold">Policy precheck: {allowed ? "permitted" : result.status}</p>
      <p className="mt-1">{result.friendlyMessage}</p>
      {result.policyDecision?.approvalsRequired?.length ? (
        <p className="mt-2 text-xs">Approvals required: {result.policyDecision.approvalsRequired.join(", ")}</p>
      ) : null}
      {result.policyDecision?.warnings?.length ? (
        <ul className="mt-2 list-disc pl-5 text-xs">
          {result.policyDecision.warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

export function submitDisabledFromPrecheck(result?: AdminGovernanceActionResponse | null): boolean {
  if (!result) return true;
  return result.status === "denied" || result.status === "pending_backend";
}
