"use client";

/**
 * Facility Operations â€” operational command hub for the selected facility.
 * Route: /operations/facility-operations
 */

import Link from "next/link";
import { Settings2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { filterFacilityOpsNavCards } from "@/lib/operations/facilityOperationsNav";
import { WorkspaceOpsHub } from "@/components/workspace-ops/WorkspaceOpsHub";
import { FacilityDigitalReadinessOrchestrationPanel } from "@/components/facility/FacilityDigitalReadinessOrchestrationPanel";
import { WorkspaceContextOrchestrationRail } from "@/components/workspace-ops/WorkspaceContextOrchestrationRail";

export default function FacilityOperationsHubPage() {
  const facility = useFacilityStore((s) => s.facility);
  const { isClinical, isAdmin, isFinance, isDispenser, isQueueManager, isOperationsOversight } = useRoleGroup();
  const cards = filterFacilityOpsNavCards({
    isClinical,
    isAdmin,
    isFinance,
    isDispenser,
    isQueueManager,
    isOperationsOversight,
  });

  return (
    <AppLayout>
      <PageShell
        title="Facility operations"
        subtitle="Queue, scheduling, workforce, beds, patient flow, and alerts â€” scoped to your selected facility and role."
      >
        {!facility ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft/80 p-6 text-sm text-warning-foreground">
            <p className="font-medium">Select a facility from the header context</p>
            <p className="mt-1 text-warning-foreground/90">
              Operational metrics and queue APIs are facility-scoped. Choose a facility on Home before opening the
              control tower or patient flow board.
            </p>
            <Link href="/home" className="mt-3 inline-block text-sm font-semibold text-primary-hover underline">
              Return to Home
            </Link>
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">
            Active facility: <span className="font-semibold text-foreground">{facility.name}</span>
          </p>
        )}

        {facility ? (
          <div className="mt-6 space-y-4">
            <WorkspaceContextOrchestrationRail />
            <FacilityDigitalReadinessOrchestrationPanel />
          </div>
        ) : null}

        {facility ? (
          <section className="mt-6 rounded-2xl border border-border bg-card p-4 min-h-[480px]">
            <h2 className="text-sm font-semibold text-foreground mb-1">Workspace operations hub</h2>
            <p className="text-xs text-muted-foreground mb-4">
              Billing, inventory, and staffing for the active facility — live data when BFF paths are available.
            </p>
            <WorkspaceOpsHub
              workspaceName={`${facility.name} — Operations`}
              workspaceType="clinical"
              facilityName={facility.name}
            />
          </section>
        ) : null}

        {facility ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {cards.map(({ id, label, description, href, Icon }) => (
              <Link
                key={id}
                href={href}
                className="group rounded-2xl border border-border bg-card p-5 shadow-sm transition hover:border-impilo-300 hover:shadow-md"
              >
                <div className="flex items-start gap-3">
                  <div className="rounded-xl bg-danger-soft p-2 text-danger">
                    <Icon className="h-5 w-5" aria-hidden />
                  </div>
                  <div>
                    <h2 className="text-sm font-semibold text-foreground group-hover:text-impilo-800">{label}</h2>
                    <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{description}</p>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        ) : null}

        {isAdmin ? (
          <section className="mt-10 border-t border-border pt-6">
            <h2 className="text-sm font-semibold text-foreground">Platform operations (admin)</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Sovereign registry and asset planes â€” separate from day-to-day facility command.
            </p>
            <Link
              href="/operations"
              className="mt-3 inline-flex items-center gap-2 rounded-lg border border-border bg-background px-4 py-2 text-xs font-semibold text-foreground hover:bg-card"
            >
              <Settings2 className="h-4 w-4" aria-hidden />
              Open platform operations
            </Link>
          </section>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
