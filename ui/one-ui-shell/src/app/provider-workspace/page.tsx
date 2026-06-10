"use client";

import { useMemo } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { OperatorTelemetryPanel } from "@/components/operations/OperatorTelemetryPanel";
import { PageShell } from "@/components/PageShell";
import {
  ProviderJourneyStepper,
  CoreTransactionShell,
} from "@/features/core-transaction/components";
import {
  useCoreTransactionFeed,
  useDispatchOperatorFeed,
  useWorkflowOperatorFeed,
} from "@/hooks/queries/useCoreTransactionExperience";
import { useClinicalWorklist } from "@/hooks/queries/useClinicalWorklist";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { hasAdministrationGovernanceEntry } from "@/lib/administration-governance";
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

const PROVIDER_PRIORITY_TYPES = new Set([
  "FACILITY_WALK_IN",
  "EMERGENCY",
  "REFERRAL",
  "TELEMEDICINE",
  "LABORATORY",
  "IMAGING",
  "PHARMACY",
]);

function prioritizeProviderJourney(items: CoreTransactionView[]): CoreTransactionView[] {
  const preferred = items.filter((item) => PROVIDER_PRIORITY_TYPES.has(item.transaction.type));
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

export default function ProviderWorkspacePage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const workflowStatus = normalizeFilter(searchParams.get("pWfStatus"), WORKFLOW_STATUS_OPTIONS);
  const workflowType = normalizeFilter(searchParams.get("pWfType"), WORKFLOW_TYPE_OPTIONS);
  const dispatchStatus = normalizeFilter(searchParams.get("pDpStatus"), DISPATCH_STATUS_OPTIONS);
  const facility = useFacilityStore((s) => s.facility);
  const { contract } = useSessionExperienceContract();
  const showAdminGovernance = contract && hasAdministrationGovernanceEntry(contract);
  const worklistQ = useClinicalWorklist({ facilityId: facility?.id, size: 24 });

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
      pWfStatus: null,
      pWfType: null,
      pDpStatus: null,
    });
  };

  const { items, isLoading, isError } = useCoreTransactionFeed();
  const transactions = prioritizeProviderJourney(items);
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
  const worklistItems = worklistQ.data?.data?.items ?? [];
  const worklistSummary = worklistQ.data?.data?.summary;

  return (
    <AppLayout>
      <PageShell
        title="Provider Workspace"
        subtitle="Provider-facing orchestration with journey alignment and Nompilo provider assist"
      >
        <div className="space-y-4">
          {showAdminGovernance ? (
            <div className="rounded-xl border border-indigo-200 bg-indigo-50/80 px-4 py-3 text-sm text-indigo-950">
              <p className="font-medium">Administration & Governance</p>
              <p className="mt-1 text-indigo-900">
                Organisation-scoped onboarding, user management and access review — filtered by your Session Experience Contract.
              </p>
              <Link
                href="/work/administration-governance"
                className="mt-2 inline-flex text-sm font-semibold text-indigo-700 hover:text-indigo-900"
              >
                Open Administration & Governance →
              </Link>
            </div>
          ) : null}
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-emerald-700">Worklist-first entry</p>
                <h2 className="mt-1 text-base font-semibold text-emerald-900">
                  Start from composed clinical inbox, then open other routes
                </h2>
                <p className="mt-1 text-sm text-emerald-800">
                  Existing queue, results, telemedicine, and tools routes remain accessible from this screen.
                </p>
              </div>
              <div className="text-right text-sm text-emerald-900">
                <p className="font-semibold">{worklistSummary?.total ?? 0} total items</p>
                <p>{worklistSummary?.urgent ?? 0} urgent · {worklistSummary?.overdue ?? 0} overdue</p>
              </div>
            </div>
            <div className="mt-3 grid gap-2 md:grid-cols-2">
              {(worklistItems.length > 0 ? worklistItems.slice(0, 6) : []).map((item) => (
                <div key={String(item.id)} className="rounded-lg border border-emerald-200 bg-white px-3 py-2">
                  <p className="text-sm font-medium text-slate-900">{String(item.title ?? "Clinical action")}</p>
                  <p className="text-xs text-slate-500">
                    {String(item.kind ?? "ITEM")} · {String(item.status ?? "PENDING")} · {String(item.priority ?? "MEDIUM")}
                  </p>
                  <Link href={typeof item.href === "string" && item.href ? item.href : "/clinical"} className="mt-1 inline-block text-xs font-medium text-emerald-700 hover:text-emerald-900">
                    Open item →
                  </Link>
                </div>
              ))}
              {worklistItems.length === 0 ? (
                <div className="rounded-lg border border-emerald-200 bg-white px-3 py-3 text-sm text-slate-600 md:col-span-2">
                  {worklistQ.isLoading
                    ? "Loading composed clinical worklist..."
                    : "No composed worklist items in this facility context."}
                </div>
              ) : null}
            </div>
            <div className="mt-3 flex flex-wrap gap-2 text-xs">
              <Link href="/learning" className="rounded-md border border-emerald-300 bg-white px-2.5 py-1.5 text-emerald-800 hover:bg-emerald-100">Impilo Fundo</Link>
              <Link href="/queue" className="rounded-md border border-emerald-300 bg-white px-2.5 py-1.5 text-emerald-800 hover:bg-emerald-100">Queue</Link>
              <Link href="/queue/search" className="rounded-md border border-emerald-300 bg-white px-2.5 py-1.5 text-emerald-800 hover:bg-emerald-100">Patient Charts</Link>
              <Link href="/telemedicine" className="rounded-md border border-emerald-300 bg-white px-2.5 py-1.5 text-emerald-800 hover:bg-emerald-100">Telemedicine</Link>
              <Link href="/clinical-tools" className="rounded-md border border-emerald-300 bg-white px-2.5 py-1.5 text-emerald-800 hover:bg-emerald-100">Clinical Tools</Link>
              <Link href="/operations/workflows" className="rounded-md border border-emerald-300 bg-white px-2.5 py-1.5 text-emerald-800 hover:bg-emerald-100">Workflows</Link>
              <Link href="/operations/dispatch" className="rounded-md border border-emerald-300 bg-white px-2.5 py-1.5 text-emerald-800 hover:bg-emerald-100">Dispatch</Link>
            </div>
          </div>
          <div className="rounded-xl border border-cyan-200 bg-cyan-50 p-3 text-sm text-cyan-900">
            Provider workspace calls are trust/context propagated through the shared API client header contract,
            including actor/provider identity and facility/workspace/shift context when available.
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <OperatorTelemetryPanel
              title="Provider workflow telemetry"
              kind="workflow"
              items={workflowFeed.items}
              isLoading={workflowFeed.isLoading}
              isError={workflowFeed.isError}
              detail="Backed by /internal/v1/workflows for provider operational context."
              emptyLabel="No provider workflow events available."
              controls={
                <div className="grid gap-2 sm:grid-cols-2">
                  <label className="text-xs text-slate-600">
                    Workflow status
                    <select
                      className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                      value={workflowStatus}
                      onChange={(event) => setFilter({ pWfStatus: event.target.value })}
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
                      onChange={(event) => setFilter({ pWfType: event.target.value })}
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
                  source: "provider-workspace",
                })
              }
              rowLinkLabel="Open workflow ops"
            />
            <OperatorTelemetryPanel
              title="Provider dispatch telemetry"
              kind="dispatch"
              items={dispatchFeed.items}
              isLoading={dispatchFeed.isLoading}
              isError={dispatchFeed.isError}
              detail="Backed by /internal/v1/dispatch/tasks for provider handoff and delivery operations."
              emptyLabel="No provider dispatch events available."
              controls={
                <label className="text-xs text-slate-600">
                  Dispatch status
                  <select
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                    value={dispatchStatus}
                    onChange={(event) => setFilter({ pDpStatus: event.target.value })}
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
                  source: "provider-workspace",
                })
              }
              rowLinkLabel="Open dispatch ops"
            />
          </div>
          <div className="rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-3 text-sm text-fuchsia-900">
            <div className="flex items-center gap-2">
              <FeatureMaturityBadge
                status={transactions.length > 0 ? "live" : isError ? "partial" : "connected"}
                detail={
                  transactions.length === 0
                    ? "Provider workspace renders only live core-transaction records and does not inject sample fallback."
                    : "Provider workspace examples are loaded from /internal/v1/core-transactions."
                }
              />
              <span>
                {isLoading
                  ? "Loading provider workspace transactions from live composition..."
                  : isError
                    ? "Live provider workspace fetch failed; retry to restore real transaction context."
                    : isEmpty
                      ? "No provider workspace transactions returned by the BFF feed."
                      : "Provider workspace is now linked to live core-transaction APIs."}
              </span>
            </div>
          </div>
          <ProviderJourneyStepper />
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
