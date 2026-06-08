"use client";

/**
 * Council staff workspace — application queues by council and workflow state.
 * Route: /registry/provider-council/council-workspace
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  CheckCircle2,
  HelpCircle,
  Loader2,
  PlayCircle,
  Wallet,
  XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { CouncilLearningEvidencePanel } from "@/components/learning/CouncilLearningEvidencePanel";
import {
  councilQueueReviewBucket,
  type CouncilApplicationRow,
  useAdvanceCouncilApplication,
  useProviderCouncilQueue,
  useRecordCouncilReview,
} from "@/hooks/queries/useProviderCouncil";
import { useSearchParams } from "next/navigation";

type QueueTab = "pending" | "approved" | "rejected" | "needs-info";

const QUEUE_TABS: { id: QueueTab; label: string }[] = [
  { id: "pending", label: "Pending" },
  { id: "approved", label: "Approved" },
  { id: "rejected", label: "Rejected" },
  { id: "needs-info", label: "Needs info" },
];

function applicationProviderId(app: CouncilApplicationRow): string {
  const direct = app.providerId ?? app.provider_id;
  if (direct != null && String(direct).trim()) return String(direct);
  const nested = app.provider?.id;
  if (nested != null && String(nested).trim()) return String(nested);
  return "";
}

function applicationId(app: CouncilApplicationRow): number | null {
  const raw = app.id;
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim()) {
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function CouncilApplicationActions({
  app,
  councilId,
  onMutated,
}: {
  app: CouncilApplicationRow;
  councilId: string;
  onMutated: () => void;
}) {
  const advance = useAdvanceCouncilApplication(councilId);
  const recordReview = useRecordCouncilReview(councilId);
  const appId = applicationId(app);
  const workflow = String(app.workflowState ?? "").toUpperCase();
  const councilNumeric = Number(councilId);
  const busy = advance.isPending || recordReview.isPending;

  if (!appId || !Number.isFinite(councilNumeric)) {
    return null;
  }

  const runAdvance = (action: "under-admin-review" | "awaiting-payment" | "fee-paid") => {
    advance.mutate({ applicationId: appId, action }, { onSuccess: onMutated });
  };

  const runReview = (decision: "APPROVED" | "REJECTED" | "NEEDS_INFO") => {
    recordReview.mutate(
      { applicationId: appId, councilId: councilNumeric, decision },
      { onSuccess: onMutated },
    );
  };

  return (
    <div className="flex flex-wrap gap-2">
      {workflow === "SUBMITTED" ? (
        <button
          type="button"
          data-testid={`council-advance-admin-${appId}`}
          disabled={busy}
          onClick={() => runAdvance("under-admin-review")}
          className="inline-flex items-center gap-1 rounded-lg bg-blue-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          <PlayCircle className="h-3.5 w-3.5" />
          Start admin review
        </button>
      ) : null}
      {workflow === "UNDER_ADMIN_REVIEW" ? (
        <button
          type="button"
          data-testid={`council-advance-payment-${appId}`}
          disabled={busy}
          onClick={() => runAdvance("awaiting-payment")}
          className="inline-flex items-center gap-1 rounded-lg bg-amber-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-amber-700 disabled:opacity-50"
        >
          <Wallet className="h-3.5 w-3.5" />
          Await payment
        </button>
      ) : null}
      {workflow === "AWAITING_PAYMENT" ? (
        <>
          <Link
            href={`/registry/provider-council/self-service?providerId=${encodeURIComponent(
              applicationProviderId(app),
            )}&obligationHint=${encodeURIComponent(String(appId))}`}
            data-testid={`council-self-service-payment-${appId}`}
            className="inline-flex items-center gap-1 rounded-lg border border-violet-200 bg-violet-50 px-2.5 py-1 text-xs font-medium text-violet-800 hover:bg-violet-100"
          >
            <Wallet className="h-3.5 w-3.5" />
            Pay via self-service
          </Link>
          <button
            type="button"
            data-testid={`council-advance-fee-paid-${appId}`}
            disabled={busy}
            onClick={() => runAdvance("fee-paid")}
            className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            <CheckCircle2 className="h-3.5 w-3.5" />
            Mark fee paid
          </button>
        </>
      ) : null}
      {workflow === "READY_FOR_REVIEW" || workflow === "UNDER_ADMIN_REVIEW" ? (
        <>
          <button
            type="button"
            data-testid={`council-review-approve-${appId}`}
            disabled={busy}
            onClick={() => runReview("APPROVED")}
            className="inline-flex items-center gap-1 rounded-lg bg-green-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
          >
            <CheckCircle2 className="h-3.5 w-3.5" />
            Approve
          </button>
          <button
            type="button"
            data-testid={`council-review-reject-${appId}`}
            disabled={busy}
            onClick={() => runReview("REJECTED")}
            className="inline-flex items-center gap-1 rounded-lg border border-red-200 px-2.5 py-1 text-xs font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
          >
            <XCircle className="h-3.5 w-3.5" />
            Reject
          </button>
          <button
            type="button"
            data-testid={`council-review-needs-info-${appId}`}
            disabled={busy}
            onClick={() => runReview("NEEDS_INFO")}
            className="inline-flex items-center gap-1 rounded-lg border border-amber-200 px-2.5 py-1 text-xs font-medium text-amber-800 hover:bg-amber-50 disabled:opacity-50"
          >
            <HelpCircle className="h-3.5 w-3.5" />
            Needs info
          </button>
        </>
      ) : null}
    </div>
  );
}

export default function CouncilWorkspacePage() {
  const searchParams = useSearchParams();
  const councilId = searchParams.get("councilId") ?? undefined;
  const providerPublicId = searchParams.get("providerPublicId") ?? undefined;
  const workflowStates =
    searchParams.get("workflowStates") ??
    "SUBMITTED,UNDER_ADMIN_REVIEW,AWAITING_PAYMENT,READY_FOR_REVIEW";
  const { data, isLoading, refetch } = useProviderCouncilQueue(councilId, workflowStates);
  const [activeTab, setActiveTab] = useState<QueueTab>("pending");

  const filtered = useMemo(() => {
    const rows = data ?? [];
    return rows.filter((app) => councilQueueReviewBucket(app) === activeTab);
  }, [activeTab, data]);

  const tabCounts = useMemo(() => {
    const rows = data ?? [];
    return QUEUE_TABS.reduce(
      (acc, tab) => {
        acc[tab.id] = rows.filter((app) => councilQueueReviewBucket(app) === tab.id).length;
        return acc;
      },
      {} as Record<QueueTab, number>,
    );
  }, [data]);

  return (
    <AppLayout>
      <PageShell
        title="Council operations workspace"
        subtitle="Intake and workflow queues scoped to a professional council"
      >
        <RegistryPlaneContextBar />
        <div className="mb-4">
          <Link
            href="/registry-admin"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to registry administration
          </Link>
        </div>

        <p className="text-sm text-gray-600 mb-4">
          Use <code className="bg-gray-100 px-1 rounded">?councilId=&lt;id&gt;</code> and optional{" "}
          <code className="bg-gray-100 px-1 rounded">workflowStates=</code> (comma-separated). Add{" "}
          <code className="bg-gray-100 px-1 rounded">providerPublicId=</code> to surface learning-service
          completion evidence for a provider.
        </p>

        <div className="mb-6">
          <CouncilLearningEvidencePanel providerPublicId={providerPublicId} />
        </div>

        {!councilId ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            Add <strong>councilId</strong> to load the council queue from Varapi.
          </div>
        ) : null}

        {councilId ? (
          <section
            className="rounded-lg border border-gray-200 bg-white p-4"
            data-testid="council-workspace-queue"
          >
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-sm font-semibold text-gray-900">Council application queue</h2>
              <div className="flex flex-wrap gap-2">
                {QUEUE_TABS.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    data-testid={`council-queue-tab-${tab.id}`}
                    onClick={() => setActiveTab(tab.id)}
                    className={`rounded-full px-3 py-1 text-xs font-medium ${
                      activeTab === tab.id
                        ? "bg-blue-600 text-white"
                        : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                    }`}
                  >
                    {tab.label} ({tabCounts[tab.id] ?? 0})
                  </button>
                ))}
              </div>
            </div>

            {isLoading ? (
              <div className="flex items-center gap-2 text-gray-500 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" /> Loading…
              </div>
            ) : filtered.length === 0 ? (
              <p className="text-sm text-gray-500">No applications in the {activeTab} queue.</p>
            ) : (
              <ul className="divide-y divide-gray-100">
                {filtered.map((app) => {
                  const appId = applicationId(app);
                  return (
                    <li
                      key={String(appId ?? app.applicationType)}
                      className="flex flex-wrap items-center justify-between gap-3 py-3"
                      data-testid={appId ? `council-application-${appId}` : undefined}
                    >
                      <div>
                        <p className="text-sm font-medium text-gray-900">
                          Application #{appId ?? "—"} · {String(app.applicationType ?? "REGISTRATION")}
                        </p>
                        <p className="text-xs text-gray-500">
                          Workflow: {String(app.workflowState ?? "—")} · Review:{" "}
                          {String(app.reviewState ?? "pending")} · Fee: {String(app.feeState ?? "—")}
                        </p>
                      </div>
                      <CouncilApplicationActions
                        app={app}
                        councilId={councilId}
                        onMutated={() => void refetch()}
                      />
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
