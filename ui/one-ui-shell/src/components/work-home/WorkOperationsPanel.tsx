"use client";

/**
 * WorkOperationsPanel (Phase F6) — the real feeds /provider-workspace carried that Work Home's
 * BFF-driven sections (Phase E4) don't produce: core-transaction journey composition, and
 * workflow/dispatch operator telemetry. Migrated component-by-component, not rewritten — same
 * hooks, same filter query params, same components (OperatorTelemetryPanel,
 * ProviderJourneyStepper, CoreTransactionShell) /provider-workspace used.
 *
 * Deliberately does NOT port /provider-workspace's "Worklist-first entry" panel — that was a
 * client composition of the same queue/referral/task/order/pharmacy/telemedicine feeds Work
 * Home's own `clinical-worklist` BFF section (Phase E1/E4) already renders; porting it here
 * would duplicate the same data in two places on the same page.
 */

import { useMemo } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { OperatorTelemetryPanel } from "@/components/operations/OperatorTelemetryPanel";
import { ProviderJourneyStepper, CoreTransactionShell } from "@/features/core-transaction/components";
import {
  useCoreTransactionFeed,
  useDispatchOperatorFeed,
  useWorkflowOperatorFeed,
} from "@/hooks/queries/useCoreTransactionExperience";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { hasAdministrationGovernanceEntry } from "@/lib/administration-governance";
import type { CoreTransactionView } from "@/features/core-transaction/types";
import {
  DISPATCH_STATUS_OPTIONS,
  WORKFLOW_STATUS_OPTIONS,
  WORKFLOW_TYPE_OPTIONS,
  normalizeFilter,
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

function buildOperationsHref(basePath: string, params: Record<string, string | null | undefined>): string {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) qs.set(key, value);
  }
  const query = qs.toString();
  return query ? `${basePath}?${query}` : basePath;
}

export function WorkOperationsPanel() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const workflowStatus = normalizeFilter(searchParams.get("pWfStatus"), WORKFLOW_STATUS_OPTIONS);
  const workflowType = normalizeFilter(searchParams.get("pWfType"), WORKFLOW_TYPE_OPTIONS);
  const dispatchStatus = normalizeFilter(searchParams.get("pDpStatus"), DISPATCH_STATUS_OPTIONS);
  const { contract } = useSessionExperienceContract();
  const showAdminGovernance = contract && hasAdministrationGovernanceEntry(contract);

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
    setFilter({ pWfStatus: null, pWfType: null, pDpStatus: null });
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
    () => [dispatchStatus !== "ALL" ? { key: "status", value: dispatchStatus } : null].filter(
      (item): item is NonNullable<typeof item> => item !== null,
    ),
    [dispatchStatus],
  );

  return (
    <div className="space-y-4">
      {showAdminGovernance ? (
        <div className="rounded-xl border border-info/25 bg-info-soft/80 px-4 py-3 text-sm text-primary-hover">
          <p className="font-medium">Administration & Governance</p>
          <p className="mt-1 text-primary-hover">
            Organisation-scoped onboarding, user management and access review — filtered by your Session Experience Contract.
          </p>
          <Link
            href="/work/administration-governance"
            className="mt-2 inline-flex text-sm font-semibold text-primary-hover hover:text-primary-hover"
          >
            Open Administration & Governance →
          </Link>
        </div>
      ) : null}

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
              <label className="text-xs text-muted-foreground">
                Workflow status
                <select
                  className="mt-1 w-full rounded-md border border-border bg-card px-2 py-1 text-sm"
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
              <label className="text-xs text-muted-foreground">
                Workflow type
                <select
                  className="mt-1 w-full rounded-md border border-border bg-card px-2 py-1 text-sm"
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
              source: "work",
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
            <label className="text-xs text-muted-foreground">
              Dispatch status
              <select
                className="mt-1 w-full rounded-md border border-border bg-card px-2 py-1 text-sm"
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
              source: "work",
            })
          }
          rowLinkLabel="Open dispatch ops"
        />
      </div>

      <div className="rounded-xl border border-border bg-neutral-100 p-3 text-sm text-fuchsia-900">
        <div className="flex items-center gap-2">
          <FeatureMaturityBadge
            status={transactions.length > 0 ? "live" : isError ? "partial" : "connected"}
            detail={
              transactions.length === 0
                ? "Core-transaction journey renders only live records and does not inject sample fallback."
                : "Core-transaction journey is loaded from /internal/v1/core-transactions."
            }
          />
          <span>
            {isLoading
              ? "Loading core-transaction journey from live composition..."
              : isError
                ? "Live core-transaction fetch failed; retry to restore real transaction context."
                : isEmpty
                  ? "No core-transaction records returned by the BFF feed."
                  : "Core-transaction journey is linked to live core-transaction APIs."}
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
  );
}
