"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useDispatchOperatorFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function DispatchOperationsPage() {
  const { items, isLoading, isError } = useDispatchOperatorFeed();

  return (
    <AppLayout>
      <PageShell
        title="Dispatch Operations"
        subtitle="Operational task dispatch, assignment, and completion telemetry"
      >
        <div className="space-y-4">
          <div className="flex items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">
            <FeatureMaturityBadge
              status={items.length > 0 ? "connected" : "partial"}
              detail="Backed by /internal/v1/dispatch/tasks."
            />
            <span>
              {isLoading
                ? "Loading dispatch task feed..."
                : isError
                  ? "Dispatch endpoint unavailable. Try again or verify service health."
                  : `Loaded ${items.length} dispatch task(s) from the operator endpoint.`}
            </span>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            {items.slice(0, 12).map((task, index) => {
              const taskId = String(task.id ?? task.taskId ?? `task-${index}`);
              const status = String(task.status ?? task.state ?? "UNKNOWN");
              const assignee = String(task.assigneeId ?? task.assignedTo ?? "UNASSIGNED");
              return (
                <section key={taskId} className="impilo-surface-card p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Dispatch Task</p>
                  <h3 className="mt-1 text-sm font-semibold text-slate-900">{taskId}</h3>
                  <p className="mt-1 text-sm text-slate-700">Status: {status}</p>
                  <p className="text-sm text-slate-700">Assignee: {assignee}</p>
                </section>
              );
            })}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
