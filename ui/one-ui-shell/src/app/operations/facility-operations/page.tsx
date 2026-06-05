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
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { WorkspaceOpsHub } from "@/components/workspace-ops/WorkspaceOpsHub";

export default function FacilityOperationsHubPage() {
  const facility = useFacilityStore((s) => s.facility);
  const { isClinical, isAdmin, isFinance, isDispenser, isQueueManager, isOperationsOversight } = useRoleGroup();
  const readinessQ = useQuery({
    queryKey: ["facility-digital-readiness", facility?.id],
    queryFn: () =>
      apiClient.get<ApiResponse<{ id: string; attributes?: Record<string, unknown> }[]>>(
        "/internal/v1/registry/facilities?size=5&page=0",
      ),
    enabled: !!facility,
  });

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
          <div className="rounded-xl border border-amber-200 bg-amber-50/80 p-6 text-sm text-amber-950">
            <p className="font-medium">Select a facility from the header context</p>
            <p className="mt-1 text-amber-900/90">
              Operational metrics and queue APIs are facility-scoped. Choose a facility on Home before opening the
              control tower or patient flow board.
            </p>
            <Link href="/home" className="mt-3 inline-block text-sm font-semibold text-impilo-700 underline">
              Return to Home
            </Link>
          </div>
        ) : (
          <p className="text-xs text-slate-500">
            Active facility: <span className="font-semibold text-slate-800">{facility.name}</span>
          </p>
        )}

        {facility ? (
          <section className="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4">
            <h3 className="text-sm font-semibold text-emerald-950">TUSO digital readiness</h3>
            <p className="mt-1 text-xs text-emerald-900/80">
              Facility operating model from registry/TUSO — connectivity, workforce, and queue maturity for{" "}
              {facility.name}.
            </p>
            <ul className="mt-3 grid gap-2 sm:grid-cols-2 text-xs text-emerald-950">
              {(readinessQ.data?.data ?? []).slice(0, 4).map((row) => (
                <li key={row.id} className="rounded-lg bg-white/80 px-3 py-2 border border-emerald-100">
                  {String(row.attributes?.name ?? row.id)} · {String(row.attributes?.status ?? "unknown")}
                </li>
              ))}
            </ul>
            <Link href="/clinical/control-tower" className="mt-3 inline-block text-xs font-semibold text-emerald-800 underline">
              Open control tower
            </Link>
          </section>
        ) : null}

        {facility ? (
          <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-4 min-h-[480px]">
            <h2 className="text-sm font-semibold text-slate-900 mb-1">Workspace operations hub</h2>
            <p className="text-xs text-slate-500 mb-4">
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
                className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-impilo-300 hover:shadow-md"
              >
                <div className="flex items-start gap-3">
                  <div className="rounded-xl bg-rose-50 p-2 text-rose-700">
                    <Icon className="h-5 w-5" aria-hidden />
                  </div>
                  <div>
                    <h2 className="text-sm font-semibold text-slate-900 group-hover:text-impilo-800">{label}</h2>
                    <p className="mt-1 text-xs leading-relaxed text-slate-600">{description}</p>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        ) : null}

        {isAdmin ? (
          <section className="mt-10 border-t border-slate-200 pt-6">
            <h2 className="text-sm font-semibold text-slate-800">Platform operations (admin)</h2>
            <p className="mt-1 text-xs text-slate-500">
              Sovereign registry and asset planes â€” separate from day-to-day facility command.
            </p>
            <Link
              href="/operations"
              className="mt-3 inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-slate-50 px-4 py-2 text-xs font-semibold text-slate-800 hover:bg-white"
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
