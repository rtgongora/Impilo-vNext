"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Loader2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { formatServiceError } from "@/lib/service-error";
import {
  useAssignDispatchTask,
  useCompleteDispatchTask,
} from "@/hooks/queries/useDispatchOps";
import { useDispatchTaskDetail } from "@/hooks/queries/useCoreTransactionExperience";

export default function DispatchTaskDetailPage() {
  const params = useParams();
  const taskId = String(params.taskId ?? "");
  const { data, isLoading, isError, error, refetch } = useDispatchTaskDetail(taskId);
  const assign = useAssignDispatchTask();
  const complete = useCompleteDispatchTask();
  const record = data?.data as Record<string, unknown> | undefined;

  return (
    <AppLayout>
      <PageShell title="Dispatch task" subtitle={`Task ${taskId}`}>
        <Link
          href="/operations/dispatch"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to dispatch
        </Link>

        {isLoading ? (
          <div className="flex items-center gap-2 py-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" />
            Loading task…
          </div>
        ) : isError ? (
          <div className="rounded-lg border border-danger/30 bg-danger-soft p-6 text-center">
            <AlertCircle className="mx-auto h-8 w-8 text-danger" />
            <p className="mt-2 text-sm text-danger">{formatServiceError(error)}</p>
            <button type="button" className="impilo-btn-secondary mt-3 text-xs" onClick={() => refetch()}>
              Retry
            </button>
          </div>
        ) : !record ? (
          <p className="text-sm text-muted-foreground">Task not found.</p>
        ) : (
          <div className="impilo-surface-card space-y-4 p-4">
            <dl className="grid gap-3 sm:grid-cols-2">
              {Object.entries(record).map(([key, value]) => (
                <div key={key}>
                  <dt className="text-xs font-medium uppercase text-muted-foreground">{key}</dt>
                  <dd className="mt-0.5 font-mono text-sm text-foreground">
                    {typeof value === "object" ? JSON.stringify(value) : String(value ?? "—")}
                  </dd>
                </div>
              ))}
            </dl>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={assign.isPending}
                className="impilo-btn-secondary text-sm"
                onClick={() =>
                  assign.mutate({
                    taskId,
                    payload: { assigneeId: "provider:current" },
                  })
                }
              >
                Assign to me
              </button>
              <button
                type="button"
                disabled={complete.isPending}
                className="impilo-btn-primary text-sm"
                onClick={() =>
                  complete.mutate({
                    taskId,
                    payload: { outcome: "completed", note: "Closed from detail page" },
                  })
                }
              >
                Mark complete
              </button>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
