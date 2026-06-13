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
      <div className="rounded-xl border border-border bg-card px-4 py-3 text-sm text-muted-foreground">
        Running OPA precheck…
      </div>
    );
  }

  if (errorMessage) {
    return (
      <div className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
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
          ? "border-danger/28 bg-danger-soft text-danger"
          : pending
            ? "border-warning/35 bg-warning-soft text-warning-foreground"
            : "border-success/25 bg-success-soft text-emerald-950"
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
