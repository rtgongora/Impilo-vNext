"use client";

import { useMemo, useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { OpaPrecheckPanel } from "./OpaPrecheckPanel";
import {
  isAccessRequestList,
  isGovernanceActionResult,
  useAdminAccessRequests,
  useDecideAccessRequest,
} from "@/hooks/useAdminGovernance";
import type { AccessRequest, AdminGovernanceActionResponse } from "@/lib/admin-governance/types";

export function AccessRequestBoard() {
  const { data, isLoading, isError } = useAdminAccessRequests();
  const decideMutation = useDecideAccessRequest();
  const [lastResult, setLastResult] = useState<AdminGovernanceActionResponse | null>(null);

  const requests = useMemo(() => {
    if (isAccessRequestList(data)) return data;
    return [];
  }, [data]);

  const pendingBackend = isGovernanceActionResult(data);

  async function handleDecision(requestId: string, decision: "approve" | "reject" | "request-more-information" | "escalate" | "revoke") {
    const result = await decideMutation.mutateAsync({ requestId, decision });
    setLastResult(result);
  }

  if (isLoading) {
    return <p className="text-sm text-slate-600">Loading access requests in your authority scope…</p>;
  }

  if (pendingBackend) {
    return <GovernanceActionResult result={data} />;
  }

  if (isError) {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
        Precheck unavailable — backend integration pending. Access requests cannot be loaded yet.
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {lastResult ? <GovernanceActionResult result={lastResult} /> : null}
      {requests.length === 0 ? (
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-6 text-sm text-slate-600">
          No access requests in your current authority scope.
        </div>
      ) : (
        requests.map((request) => (
          <AccessRequestCard
            key={request.id}
            request={request}
            onDecision={handleDecision}
            busy={decideMutation.isPending}
          />
        ))
      )}
    </div>
  );
}

function AccessRequestCard({
  request,
  onDecision,
  busy,
}: {
  request: AccessRequest;
  onDecision: (requestId: string, decision: "approve" | "reject" | "request-more-information" | "escalate" | "revoke") => void;
  busy: boolean;
}) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{request.requestType}</p>
          <h4 className="mt-1 text-base font-semibold text-slate-900">{request.requestedRole ?? request.targetSubjectId ?? "Access request"}</h4>
          <p className="mt-1 text-sm text-slate-600">
            Requester: {request.requesterName ?? request.requesterId} · Organisation: {request.organisationName ?? request.organisationId ?? "—"}
          </p>
          {request.sourceOfRecordStatus === "fallback_queue" || request.reconciliationRequired ? (
            <p className="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-950">
              This request is queued in BFF fallback storage and will require reconciliation with workforce-governance.
            </p>
          ) : null}
          {request.durableRequestId ? (
            <p className="mt-1 text-xs text-slate-500">Durable request ID: {request.durableRequestId}</p>
          ) : null}
        </div>
        <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">{request.status}</span>
      </div>
      <dl className="mt-4 grid gap-2 text-xs text-slate-600 md:grid-cols-2">
        <div>Scope: {request.requestedScope ?? "—"}</div>
        <div>Environment: {request.requestedEnvironment ?? "—"}</div>
        <div>Data scope: {request.requestedDataScope ?? "—"}</div>
        <div>Risk: {request.riskLevel ?? "—"}</div>
        <div>Precheck: {request.policyPrecheckResult ?? "—"}</div>
        <div>Approvals: {(request.approvalsRequired ?? []).join(", ") || "—"}</div>
      </dl>
      <div className="mt-4 flex flex-wrap gap-2">
        {(["approve", "reject", "request-more-information", "escalate", "revoke"] as const).map((decision) => (
          <button
            key={decision}
            type="button"
            disabled={busy}
            onClick={() => void onDecision(request.id, decision)}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            {decision.replace(/-/g, " ")}
          </button>
        ))}
      </div>
    </article>
  );
}
