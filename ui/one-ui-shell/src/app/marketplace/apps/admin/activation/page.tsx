"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { CheckCircle2, ChevronLeft, Loader2, XCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useActivationRequests,
  useDecideActivation,
  type ActivationRequest,
} from "@/hooks/queries/useHealthOsLauncher";

const STATUS_FILTERS: Array<ActivationRequest["status"] | "ALL"> = [
  "REQUESTED",
  "IN_REVIEW",
  "APPROVED",
  "REJECTED",
  "CHANGES_REQUESTED",
  "ALL",
];

export default function ActivationApprovalsPage() {
  const [status, setStatus] = useState<ActivationRequest["status"] | "ALL">("REQUESTED");
  const requestsQuery = useActivationRequests(status === "ALL" ? undefined : status);
  const decide = useDecideActivation();
  const [reasonById, setReasonById] = useState<Record<string, string>>({});

  const list = useMemo<ActivationRequest[]>(() => requestsQuery.data ?? [], [requestsQuery.data]);

  return (
    <AppLayout>
      <PageShell
        title="Capability activation approvals"
        subtitle="Governance queue for marketplace activation requests — review purpose, scope, data access and clinical safety before approving."
      >
        <div className="space-y-5">
          <Link href="/marketplace/apps" className="inline-flex items-center gap-1 text-sm text-primary-hover hover:underline">
            <ChevronLeft className="h-4 w-4" /> Back to marketplace
          </Link>

          <section className="flex flex-wrap items-center gap-2 rounded-2xl border border-border bg-card p-3 shadow-sm">
            {STATUS_FILTERS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setStatus(s)}
                className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
                  status === s
                    ? "bg-primary-hover text-white"
                    : "bg-neutral-100 text-foreground hover:bg-primary-soft"
                }`}
              >
                {s.replace("_", " ")}
              </button>
            ))}
          </section>

          <section className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="bg-background text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Request</th>
                  <th className="px-4 py-2">Requester</th>
                  <th className="px-4 py-2">Purpose</th>
                  <th className="px-4 py-2">Justification</th>
                  <th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2">Decision</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {requestsQuery.isLoading ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-muted-foreground">
                      <Loader2 className="mx-auto h-4 w-4 animate-spin" />
                    </td>
                  </tr>
                ) : list.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-muted-foreground">
                      No requests in this state.
                    </td>
                  </tr>
                ) : (
                  list.map((req) => (
                    <tr key={req.id} className="align-top">
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                        <div>{req.id.slice(0, 8)}</div>
                        <div className="mt-0.5 text-[10px] text-muted-foreground">
                          {new Date(req.createdAt).toLocaleString()}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-xs text-foreground">
                        <div>{req.requesterActorId}</div>
                        <div className="text-[10px] text-muted-foreground">
                          {req.requesterTenantId}
                          {req.requesterFacilityId ? ` · ${req.requesterFacilityId}` : ""}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-xs text-foreground">{req.purposeOfUse}</td>
                      <td className="px-4 py-3 text-sm text-foreground">{req.justification}</td>
                      <td className="px-4 py-3">
                        <StatusBadge value={req.status} />
                      </td>
                      <td className="px-4 py-3">
                        {req.status === "REQUESTED" || req.status === "IN_REVIEW" ? (
                          <div className="flex flex-col gap-2">
                            <input
                              value={reasonById[req.id] ?? ""}
                              onChange={(e) =>
                                setReasonById((m) => ({ ...m, [req.id]: e.target.value }))
                              }
                              placeholder="Reason (optional)"
                              className="w-44 rounded-lg border border-border px-2 py-1 text-xs"
                            />
                            <div className="flex gap-1">
                              <button
                                type="button"
                                disabled={decide.isPending}
                                onClick={() =>
                                  decide.mutate({
                                    requestId: req.id,
                                    decision: "APPROVED",
                                    reason: reasonById[req.id] || undefined,
                                  })
                                }
                                className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2 py-1 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
                              >
                                <CheckCircle2 className="h-3 w-3" /> Approve
                              </button>
                              <button
                                type="button"
                                disabled={decide.isPending}
                                onClick={() =>
                                  decide.mutate({
                                    requestId: req.id,
                                    decision: "REJECTED",
                                    reason: reasonById[req.id] || undefined,
                                  })
                                }
                                className="inline-flex items-center gap-1 rounded-lg bg-rose-600 px-2 py-1 text-xs font-semibold text-white hover:bg-rose-700 disabled:opacity-60"
                              >
                                <XCircle className="h-3 w-3" /> Reject
                              </button>
                            </div>
                          </div>
                        ) : (
                          <span className="text-[10px] text-muted-foreground">{req.decisionReason ?? "—"}</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}

function StatusBadge({ value }: { value: ActivationRequest["status"] }) {
  const tone: Record<string, string> = {
    REQUESTED: "bg-warning-soft text-warning-foreground",
    IN_REVIEW: "bg-info-soft text-primary-hover",
    APPROVED: "bg-success-soft text-primary-hover",
    REJECTED: "bg-danger-soft text-danger",
    CHANGES_REQUESTED: "bg-violet-50 text-violet-700",
  };
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-neutral-100 text-foreground"}`}
    >
      {value.replace("_", " ")}
    </span>
  );
}
