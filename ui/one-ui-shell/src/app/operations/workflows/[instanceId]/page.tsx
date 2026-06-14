"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Loader2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { formatServiceError } from "@/lib/service-error";
import {
  useTransitionWorkflowInstance,
  useWorkflowInstanceDetail,
} from "@/hooks/queries/useCoreTransactionExperience";

export default function WorkflowInstanceDetailPage() {
  const params = useParams();
  const instanceId = String(params.instanceId ?? "");
  const { data, isLoading, isError, error, refetch } = useWorkflowInstanceDetail(instanceId);
  const transition = useTransitionWorkflowInstance();
  const record = data?.data as Record<string, unknown> | undefined;

  return (
    <AppLayout>
      <PageShell title="Workflow instance" subtitle={`Instance ${instanceId}`}>
        <Link
          href="/operations/workflows"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to workflows
        </Link>

        {isLoading ? (
          <div className="flex items-center gap-2 py-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" />
            Loading instance…
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
          <p className="text-sm text-muted-foreground">Instance not found.</p>
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
            <button
              type="button"
              disabled={transition.isPending}
              className="impilo-btn-secondary text-sm"
              onClick={() =>
                transition.mutate({
                  instanceId,
                  payload: { action: "advance", note: "Operator advance from detail page" },
                })
              }
            >
              Advance instance
            </button>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
