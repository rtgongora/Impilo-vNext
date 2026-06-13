"use client";

import Link from "next/link";
import { Building2, LayoutDashboard, Loader2 } from "lucide-react";
import { useFacilityActiveShiftCount, useFacilityQueueStats } from "@/hooks/queries/useFacilityOperations";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";

export function WorkspaceContextOrchestrationRail() {
  const facility = useFacilityStore((s) => s.facility);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const queueQ = useFacilityQueueStats(facility?.id);
  const shiftQ = useFacilityActiveShiftCount(facility?.id);
  const waiting = Number(queueQ.data?.data?.waiting ?? 0);
  const activeShifts = Number(shiftQ.activeShiftCount ?? 0);

  return (
    <section
      className="mb-4 rounded-2xl border border-sky-200 bg-sky-50/80 p-4"
      data-testid="workspace-context-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-sky-900">
            <LayoutDashboard className="h-4 w-4" />
            Workspace context orchestration
          </p>
          <p className="mt-1 text-xs text-sky-800" data-testid="workspace-context-kpi">
            {!facility
              ? "Select a facility to bind workspace/shift control tower"
              : queueQ.isLoading
                ? "Loading TUSO queue + shift snapshot…"
                : `${workspace?.name ?? "No workspace"} · ${waiting} waiting · ${activeShifts} active shift(s)`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/clinical/control-tower"
            className="inline-flex items-center gap-1 rounded-lg border border-sky-200 bg-card px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            <Building2 className="h-3.5 w-3.5" />
            Control tower
          </Link>
          <Link
            href="/operations/facility-operations"
            className="rounded-lg border border-sky-200 bg-card px-2.5 py-1.5 font-medium text-sky-900 hover:border-sky-300"
          >
            Facility ops
          </Link>
        </div>
      </div>
      {facility && (queueQ.isLoading || shiftQ.isLoading) && (
        <div className="mt-2 flex items-center gap-2 text-xs text-sky-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Binding workspace → TUSO operational dashboards…
        </div>
      )}
    </section>
  );
}
