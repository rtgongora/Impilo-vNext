"use client";

import Link from "next/link";
import { useState } from "react";
import {
  ArrowLeft,
  CheckCircle2,
  ExternalLink,
  HelpCircle,
  Loader2,
  ShieldAlert,
  Wallet,
  XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useChangeProviderStatus, useProviders } from "@/hooks/queries/useRegistry";
import {
  type CouncilApplicationRow,
  useAdvanceCouncilApplication,
  useProviderCouncilQueue,
  useRecordCouncilReview,
} from "@/hooks/queries/useProviderCouncil";

const STATUS_OPTIONS = ["PENDING", "ACTIVE", "SUSPENDED", "REVOKED", "INACTIVE"] as const;
const DEFAULT_COUNCIL_ID = "1";

function councilApplicationId(app: CouncilApplicationRow): number | null {
  const raw = app.id;
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim()) {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

export default function ProviderVerificationQueuePage() {
  const [statusFilter, setStatusFilter] = useState<string>("PENDING");
  const { data, isLoading, refetch } = useProviders({ status: statusFilter });
  const changeStatus = useChangeProviderStatus();
  const councilQueue = useProviderCouncilQueue(
    DEFAULT_COUNCIL_ID,
    "SUBMITTED,UNDER_ADMIN_REVIEW,AWAITING_PAYMENT,READY_FOR_REVIEW",
  );
  const advanceCouncil = useAdvanceCouncilApplication(DEFAULT_COUNCIL_ID);
  const recordCouncilReview = useRecordCouncilReview(DEFAULT_COUNCIL_ID);
  const councilNumeric = Number(DEFAULT_COUNCIL_ID);

  const providers = data?.data ?? [];
  const councilApps = councilQueue.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Provider verification queue"
        subtitle="Review VARAPI registrations and transition provider status"
      >
        <FeatureMaturityBadge status="partial" detail="VARAPI list + council queue via Experience BFF" />

        {councilApps.length > 0 ? (
          <div
            className="mb-4 rounded-lg border border-blue-100 bg-info-soft px-4 py-3 text-xs text-blue-900"
            data-testid="verification-council-banner"
          >
            Council applications awaiting review: <strong>{councilApps.length}</strong>{" "}
            <Link
              href={`/registry/provider-council/council-workspace?councilId=${encodeURIComponent(DEFAULT_COUNCIL_ID)}`}
              className="ml-2 inline-flex items-center gap-1 font-medium underline"
            >
              Open council workspace
              <ExternalLink className="h-3 w-3" />
            </Link>
          </div>
        ) : null}

        {councilApps.length > 0 ? (
          <section
            className="mb-6 rounded-xl border border-border bg-card p-4"
            data-testid="verification-council-workflow"
          >
            <h2 className="mb-3 text-sm font-semibold text-foreground">Council workflow actions</h2>
            <ul className="divide-y divide-gray-100">
              {councilApps.slice(0, 5).map((app) => {
                const appId = councilApplicationId(app);
                const workflow = String(app.workflowState ?? "").toUpperCase();
                const busy = advanceCouncil.isPending || recordCouncilReview.isPending;
                if (!appId || !Number.isFinite(councilNumeric)) return null;

                return (
                  <li
                    key={appId}
                    className="flex flex-wrap items-center justify-between gap-3 py-3"
                    data-testid={`verification-council-app-${appId}`}
                  >
                    <div>
                      <p className="text-sm font-medium text-foreground">
                        Council app #{appId} · {String(app.applicationType ?? "REGISTRATION")}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {workflow} · review {String(app.reviewState ?? "pending")}
                      </p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {workflow === "SUBMITTED" ? (
                        <button
                          type="button"
                          disabled={busy}
                          data-testid={`verification-council-admin-${appId}`}
                          onClick={() =>
                            advanceCouncil.mutate(
                              { applicationId: appId, action: "under-admin-review" },
                              { onSuccess: () => void councilQueue.refetch() },
                            )
                          }
                          className="rounded-lg bg-blue-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                        >
                          Admin review
                        </button>
                      ) : null}
                      {workflow === "UNDER_ADMIN_REVIEW" ? (
                        <button
                          type="button"
                          disabled={busy}
                          data-testid={`verification-council-payment-${appId}`}
                          onClick={() =>
                            advanceCouncil.mutate(
                              { applicationId: appId, action: "awaiting-payment" },
                              { onSuccess: () => void councilQueue.refetch() },
                            )
                          }
                          className="inline-flex items-center gap-1 rounded-lg bg-amber-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-amber-700 disabled:opacity-50"
                        >
                          <Wallet className="h-3.5 w-3.5" />
                          Await fee
                        </button>
                      ) : null}
                      {workflow === "AWAITING_PAYMENT" ? (
                        <button
                          type="button"
                          disabled={busy}
                          data-testid={`verification-council-fee-paid-${appId}`}
                          onClick={() =>
                            advanceCouncil.mutate(
                              { applicationId: appId, action: "fee-paid" },
                              { onSuccess: () => void councilQueue.refetch() },
                            )
                          }
                          className="rounded-lg bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                        >
                          Fee paid
                        </button>
                      ) : null}
                      <button
                        type="button"
                        disabled={busy}
                        data-testid={`verification-council-approve-${appId}`}
                        onClick={() =>
                          recordCouncilReview.mutate(
                            {
                              applicationId: appId,
                              councilId: councilNumeric,
                              decision: "APPROVED",
                            },
                            { onSuccess: () => void councilQueue.refetch() },
                          )
                        }
                        className="inline-flex items-center gap-1 rounded-lg bg-green-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        Approve
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        data-testid={`verification-council-needs-info-${appId}`}
                        onClick={() =>
                          recordCouncilReview.mutate(
                            {
                              applicationId: appId,
                              councilId: councilNumeric,
                              decision: "NEEDS_INFO",
                            },
                            { onSuccess: () => void councilQueue.refetch() },
                          )
                        }
                        className="inline-flex items-center gap-1 rounded-lg border border-warning/35 px-2.5 py-1 text-xs font-medium text-warning-foreground hover:bg-warning-soft disabled:opacity-50"
                      >
                        <HelpCircle className="h-3.5 w-3.5" />
                        Needs info
                      </button>
                      <button
                        type="button"
                        disabled={busy}
                        data-testid={`verification-council-reject-${appId}`}
                        onClick={() =>
                          recordCouncilReview.mutate(
                            {
                              applicationId: appId,
                              councilId: councilNumeric,
                              decision: "REJECTED",
                            },
                            { onSuccess: () => void councilQueue.refetch() },
                          )
                        }
                        className="inline-flex items-center gap-1 rounded-lg border border-danger/28 px-2.5 py-1 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-50"
                      >
                        <XCircle className="h-3.5 w-3.5" />
                        Reject
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        ) : null}

        <Link
          href="/registry/providers"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to provider registry
        </Link>

        <div className="mb-4 flex flex-wrap items-center gap-2">
          {STATUS_OPTIONS.map((status) => (
            <button
              key={status}
              type="button"
              onClick={() => setStatusFilter(status)}
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                statusFilter === status
                  ? "bg-blue-600 text-white"
                  : "bg-neutral-100 text-foreground hover:bg-neutral-100"
              }`}
            >
              {status}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : providers.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-10 text-center text-sm text-muted-foreground">
            No providers in <strong>{statusFilter}</strong> status.
          </div>
        ) : (
          <ul className="divide-y divide-gray-100 rounded-xl border border-border bg-card">
            {providers.map((provider) => {
              const attrs = provider.attributes ?? {};
              const name = String(attrs.displayName ?? provider.id);
              const currentStatus = String(attrs.status ?? "—");
              return (
                <li key={provider.id} className="flex flex-wrap items-center justify-between gap-3 p-4">
                  <div>
                    <p className="text-sm font-medium text-foreground">{name}</p>
                    <p className="text-xs text-muted-foreground">
                      {String(attrs.registrationNumber ?? "—")} · {String(attrs.speciality ?? "—")} ·{" "}
                      <span className="font-medium">{currentStatus}</span>
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {statusFilter === "PENDING" ? (
                      <>
                        <button
                          type="button"
                          disabled={changeStatus.isPending}
                          onClick={() =>
                            changeStatus.mutate(
                              { id: provider.id, newStatus: "ACTIVE", reason: "Verification approved" },
                              { onSuccess: () => void refetch() },
                            )
                          }
                          className="inline-flex items-center gap-1 rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          Approve
                        </button>
                        <button
                          type="button"
                          disabled={changeStatus.isPending}
                          onClick={() =>
                            changeStatus.mutate(
                              { id: provider.id, newStatus: "SUSPENDED", reason: "Verification rejected" },
                              { onSuccess: () => void refetch() },
                            )
                          }
                          className="inline-flex items-center gap-1 rounded-lg border border-danger/28 px-3 py-1.5 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-50"
                        >
                          <XCircle className="h-3.5 w-3.5" />
                          Suspend
                        </button>
                      </>
                    ) : null}
                    {currentStatus === "ACTIVE" ? (
                      <button
                        type="button"
                        disabled={changeStatus.isPending}
                        onClick={() =>
                          changeStatus.mutate(
                            { id: provider.id, newStatus: "REVOKED", reason: "Registry revocation" },
                            { onSuccess: () => void refetch() },
                          )
                        }
                        className="inline-flex items-center gap-1 rounded-lg border border-warning/35 px-3 py-1.5 text-xs font-medium text-warning-foreground hover:bg-warning-soft disabled:opacity-50"
                      >
                        <ShieldAlert className="h-3.5 w-3.5" />
                        Revoke
                      </button>
                    ) : null}
                    <Link
                      href={`/registry/providers/${encodeURIComponent(provider.id)}`}
                      className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background"
                    >
                      Profile
                    </Link>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
