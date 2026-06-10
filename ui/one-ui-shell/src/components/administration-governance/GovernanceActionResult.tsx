"use client";

import Link from "next/link";
import type { AdminGovernanceActionResponse } from "@/lib/admin-governance/types";

interface GovernanceActionResultProps {
  result: AdminGovernanceActionResponse;
  requestHref?: string;
}

export function GovernanceActionResult({ result, requestHref }: GovernanceActionResultProps) {
  const tone =
    result.status === "denied"
      ? "border-rose-200 bg-rose-50 text-rose-950"
      : result.status === "pending_backend"
        ? "border-amber-200 bg-amber-50 text-amber-950"
        : result.status === "pending"
          ? "border-indigo-200 bg-indigo-50 text-indigo-950"
          : "border-emerald-200 bg-emerald-50 text-emerald-950";

  return (
    <div className={`rounded-xl border px-4 py-4 text-sm ${tone}`}>
      <p className="font-semibold">{result.friendlyTitle}</p>
      <p className="mt-1">{result.friendlyMessage}</p>
      {result.policyDecision?.warnings?.length ? (
        <ul className="mt-2 list-disc pl-5 text-xs">
          {result.policyDecision.warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      ) : null}
      {result.auditEventId ? (
        <p className="mt-2 text-xs opacity-80">Audit event: {result.auditEventId}</p>
      ) : null}
      {result.auditStatus ? (
        <p className="mt-1 text-xs opacity-80">Audit status: {result.auditStatus}</p>
      ) : null}
      {result.payload?.sourceOfRecordStatus === "fallback_queue" || result.payload?.reconciliationRequired ? (
        <p className="mt-2 text-xs font-medium">
          This request is queued in BFF fallback storage and will require reconciliation with workforce-governance.
        </p>
      ) : null}
      {result.integrationStatus === "pending_backend" ? (
        <p className="mt-2 text-xs font-medium">Integration status: pending backend — not persisted as production truth.</p>
      ) : null}
      <div className="mt-3 flex flex-wrap gap-2">
        {result.requestId && requestHref ? (
          <Link href={`${requestHref}/${result.requestId}`} className="text-xs font-semibold underline">
            Open request
          </Link>
        ) : null}
        {result.requestId ? (
          <Link href="/work/administration-governance/access-requests" className="text-xs font-semibold underline">
            View access requests
          </Link>
        ) : null}
      </div>
    </div>
  );
}
