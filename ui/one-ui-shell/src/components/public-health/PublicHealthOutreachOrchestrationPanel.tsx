"use client";

import Link from "next/link";
import { Loader2, MapPin, Users } from "lucide-react";
import { usePublicHealthFieldTasks } from "@/hooks/queries/usePublicHealth";

export function PublicHealthOutreachOrchestrationPanel() {
  const tasksQ = usePublicHealthFieldTasks();
  const tasks = Array.isArray(tasksQ.data) ? tasksQ.data : [];
  const openTasks = tasks.filter((t) => {
    const status = String((t as { status?: string }).status ?? "");
    return status !== "COMPLETED" && status !== "CANCELLED";
  }).length;

  return (
    <section
      className="mb-4 rounded-2xl border border-success/25 bg-success-soft/80 p-4"
      data-testid="public-health-outreach-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-primary-hover">
            <Users className="h-4 w-4" />
            Public health outreach orchestration
          </p>
          <p className="mt-1 text-xs text-primary-hover" data-testid="outreach-field-kpi-strip">
            {tasksQ.isLoading
              ? "Loading governed field tasks from surveillance-service…"
              : `${tasks.length} field task(s) · ${openTasks} open for CHW / outreach teams`}
          </p>
        </div>
        <Link
          href="/public-health?tab=field"
          className="inline-flex items-center gap-1 rounded-lg border border-success/25 bg-card px-2.5 py-1.5 text-xs font-medium text-primary-hover hover:border-emerald-300"
        >
          <MapPin className="h-3.5 w-3.5" />
          Field operations
        </Link>
      </div>
      {tasksQ.isLoading && (
        <div className="mt-2 flex items-center gap-2 text-xs text-primary-hover">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Bridging web field ops → mobile provider task board…
        </div>
      )}
    </section>
  );
}
