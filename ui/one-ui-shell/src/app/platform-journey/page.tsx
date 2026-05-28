"use client";

import { useMemo } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { OperatorTelemetryPanel } from "@/components/operations/OperatorTelemetryPanel";
import { PageShell } from "@/components/PageShell";
import { CoreTransactionShell, PlatformJourneyMonitor } from "@/features/core-transaction/components";
import {
  useCoreTransactionFeed,
  useDispatchOperatorFeed,
  useWorkflowOperatorFeed,
} from "@/hooks/queries/useCoreTransactionExperience";
import type { CoreTransactionView } from "@/features/core-transaction/types";
import {
  DISPATCH_STATUS_OPTIONS,
  WORKFLOW_STATUS_OPTIONS,
  WORKFLOW_TYPE_OPTIONS,
  normalizeFilter,
  type DispatchStatusFilter,
  type WorkflowStatusFilter,
  type WorkflowTypeFilter,
} from "@/lib/operator-telemetry";

function prioritizePlatformJourney(items: CoreTransactionView[]): CoreTransactionView[] {
  const preferred = items.filter(
    (item) =>
      item.failureModes.length > 0 ||
      item.offlineSyncStatus !== "ONLINE" ||
      item.financialContext.paymentStatus === "PAYMENT_FAILED" ||
      item.financialContext.paymentStatus === "PENDING",
  );
  if (preferred.length > 0) return preferred.slice(0, 2);
  return items.slice(0, 2);
}

function buildOperationsHref(
  basePath: string,
  params: Record<string, string | null | undefined>,
): string {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) qs.set(key, value);
  }
  const query = qs.toString();
  return query ? `${basePath}?${query}` : basePath;
}

export default function PlatformJourneyPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const workflowStatus = normalizeFilter(searchParams.get("wfStatus"), WORKFLOW_STATUS_OPTIONS);
  const workflowType = normalizeFilter(searchParams.get("wfType"), WORKFLOW_TYPE_OPTIONS);
  const dispatchStatus = normalizeFilter(searchParams.get("dpStatus"), DISPATCH_STATUS_OPTIONS);

  const setFilter = (updates: Record<string, string | null>) => {
    const params = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(updates)) {
      if (!value || value === "ALL") {
        params.delete(key);
      } else {
        params.set(key, value);
      }
    }
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  };

  const resetFilters = () => {
    setFilter({
      wfStatus: null,
      wfType: null,
      dpStatus: null,
    });
  };

  const { items, isLoading, isError } = useCoreTransactionFeed();
  const transactions = prioritizePlatformJourney(items);
  const monitorTransaction = transactions[0];
  const isEmpty = !isLoading && !isError && transactions.length === 0;
  const workflowFeed = useWorkflowOperatorFeed({
    status: workflowStatus === "ALL" ? undefined : workflowStatus,
    type: workflowType === "ALL" ? undefined : workflowType,
  });
  const dispatchFeed = useDispatchOperatorFeed({
    status: dispatchStatus === "ALL" ? undefined : dispatchStatus,
  });

  const activeWorkflowFilters = useMemo(
    () =>
      [
        workflowStatus !== "ALL" ? { key: "status", value: workflowStatus } : null,
        workflowType !== "ALL" ? { key: "type", value: workflowType } : null,
      ].filter((item): item is NonNullable<typeof item> => item !== null),
    [workflowStatus, workflowType],
  );

  const activeDispatchFilters = useMemo(
    () =>
      [
        dispatchStatus !== "ALL" ? { key: "status", value: dispatchStatus } : null,
      ].filter((item): item is NonNullable<typeof item> => item !== null),
    [dispatchStatus],
  );

  return (
    <AppLayout>
      <PageShell
        title="Platform Journey"
        subtitle="Back-of-house orchestration visibility: blockers, sync, financial gates, and reconciliation"
      >
        <div className="space-y-4">
          <div className="rounded-xl border border-cyan-200 bg-cyan-50 p-3 text-sm text-cyan-900">
            Platform journey calls enforce trust-context propagation and now include live workflow and dispatch feeds
            for operational state visibility.
          </div>
          <div className="rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={transactions.length > 0 ? "live" : isError ? "partial" : "connected"}
                detail={
                  transactions.length === 0
                    ? "Platform journey shows only live BFF telemetry and never injects fixture transactions."
                    : "Platform journey blockers and transitions are sourced from live BFF composition."
                }
              />
              <span>
                {isLoading
                  ? "Loading platform journey telemetry from live core transactions..."
                  : isError
                    ? "Live platform telemetry unavailable; retry to restore real blocker visibility."
                    : isEmpty
                      ? "No live platform transaction feed returned by the BFF endpoint."
                      : "Platform journey blockers and transitions are now sourced from live BFF composition."}
              </span>
            </div>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <OperatorTelemetryPanel
              title="Workflow telemetry"
              kind="workflow"
              items={workflowFeed.items}
              isLoading={workflowFeed.isLoading}
              isError={workflowFeed.isError}
              detail="Backed by /internal/v1/workflows."
              emptyLabel="No workflow events available."
              controls={
                <div className="grid gap-2 sm:grid-cols-2">
                  <label className="text-xs text-slate-600">
                    Workflow status
                    <select
                      className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                      value={workflowStatus}
                      onChange={(event) => setFilter({ wfStatus: event.target.value })}
                    >
                      {WORKFLOW_STATUS_OPTIONS.map((option) => (
                        <option key={option} value={option}>
                          {option}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="text-xs text-slate-600">
                    Workflow type
                    <select
                      className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                      value={workflowType}
                      onChange={(event) => setFilter({ wfType: event.target.value })}
                    >
                      {WORKFLOW_TYPE_OPTIONS.map((option) => (
                        <option key={option} value={option}>
                          {option}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
              }
              activeFilters={activeWorkflowFilters}
              onResetFilters={resetFilters}
              rowLinkBuilder={(row) =>
                buildOperationsHref("/operations/workflows", {
                  status: workflowStatus !== "ALL" ? workflowStatus : null,
                  type: workflowType !== "ALL" ? workflowType : null,
                  focus: row.id,
                  source: "platform-journey",
                })
              }
              rowLinkLabel="Open workflow ops"
            />
            <OperatorTelemetryPanel
              title="Dispatch telemetry"
              kind="dispatch"
              items={dispatchFeed.items}
              isLoading={dispatchFeed.isLoading}
              isError={dispatchFeed.isError}
              detail="Backed by /internal/v1/dispatch/tasks."
              emptyLabel="No dispatch events available."
              controls={
                <label className="text-xs text-slate-600">
                  Dispatch status
                  <select
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                    value={dispatchStatus}
                    onChange={(event) => setFilter({ dpStatus: event.target.value })}
                  >
                    {DISPATCH_STATUS_OPTIONS.map((option) => (
                      <option key={option} value={option}>
                        {option}
                      </option>
                    ))}
                  </select>
                </label>
              }
              activeFilters={activeDispatchFilters}
              onResetFilters={resetFilters}
              rowLinkBuilder={(row) =>
                buildOperationsHref("/operations/dispatch", {
                  status: dispatchStatus !== "ALL" ? dispatchStatus : null,
                  focus: row.id,
                  source: "platform-journey",
                })
              }
              rowLinkLabel="Open dispatch ops"
            />
          </div>
          {monitorTransaction ? <PlatformJourneyMonitor transaction={monitorTransaction} /> : null}
          {transactions.length === 0 ? (
            <CoreTransactionShell status={isLoading ? "loading" : isError ? "error" : "empty"} />
          ) : (
            transactions.map((transaction) => (
              <CoreTransactionShell
                key={transaction.transaction.id}
                transaction={transaction}
                status={isLoading ? "loading" : "ready"}
              />
            ))
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
