"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useWorkflowOperatorFeed } from "@/hooks/queries/useCoreTransactionExperience";

export default function WorkflowOperationsPage() {
  const { items, isLoading, isError } = useWorkflowOperatorFeed();

  return (
    <AppLayout>
      <PageShell
        title="Workflow Orchestration"
        subtitle="Operator visibility for workflow state and orchestration health"
      >
        <div className="space-y-4">
          <div className="flex items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900">
            <FeatureMaturityBadge
              status={items.length > 0 ? "connected" : "partial"}
              detail="Backed by /internal/v1/workflows."
            />
            <span>
              {isLoading
                ? "Loading workflow orchestration feed..."
                : isError
                  ? "Workflow endpoint unavailable. Try again or verify service health."
                  : `Loaded ${items.length} workflow item(s) from the orchestration endpoint.`}
            </span>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            {items.slice(0, 12).map((workflow, index) => {
              const workflowId = String(workflow.id ?? workflow.workflowId ?? `workflow-${index}`);
              const state = String(workflow.state ?? workflow.status ?? "UNKNOWN");
              const type = String(workflow.type ?? workflow.definition ?? "WORKFLOW");
              return (
                <section key={workflowId} className="impilo-surface-card p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{type}</p>
                  <h3 className="mt-1 text-sm font-semibold text-slate-900">{workflowId}</h3>
                  <p className="mt-1 text-sm text-slate-700">State: {state}</p>
                </section>
              );
            })}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
