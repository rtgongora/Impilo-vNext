"use client";

import { useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { OperatorTelemetryPanel } from "@/components/operations/OperatorTelemetryPanel";
import { PageShell } from "@/components/PageShell";
import {
  useWorkflowDefinitions,
  useWorkflowInstances,
  useWorkflowOperatorFeed,
  useStartWorkflowInstance,
  useTransitionWorkflowInstance,
} from "@/hooks/queries/useCoreTransactionExperience";
import {
  WORKFLOW_STATUS_OPTIONS,
  WORKFLOW_TYPE_OPTIONS,
  normalizeFilter,
  readOperatorString,
} from "@/lib/operator-telemetry";

function parseJsonObject(text: string): Record<string, unknown> {
  const parsed = JSON.parse(text);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Payload must be a JSON object.");
  }
  return parsed as Record<string, unknown>;
}

export default function WorkflowOperationsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const status = normalizeFilter(searchParams.get("status"), WORKFLOW_STATUS_OPTIONS);
  const type = normalizeFilter(searchParams.get("type"), WORKFLOW_TYPE_OPTIONS);
  const focus = searchParams.get("focus");

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
    setFilter({ status: null, type: null, focus: null });
  };

  const { items, isLoading, isError } = useWorkflowOperatorFeed({
    status: status === "ALL" ? undefined : status,
    type: type === "ALL" ? undefined : type,
  });
  const definitions = useWorkflowDefinitions();
  const instances = useWorkflowInstances({ status: status === "ALL" ? undefined : status });
  const startInstance = useStartWorkflowInstance();
  const transitionInstance = useTransitionWorkflowInstance();
  const [startPayloadText, setStartPayloadText] = useState(
    JSON.stringify(
      {
        definitionId: "00000000-0000-4000-8000-000000000000",
        initiatorRef: "provider:current",
        subjectRef: "client:current",
        context: {
          source: "operations-workflow-console",
        },
      },
      null,
      2,
    ),
  );
  const [transitionInstanceId, setTransitionInstanceId] = useState(focus ?? "");
  const [transitionPayloadText, setTransitionPayloadText] = useState(
    JSON.stringify(
      {
        action: "COMPLETE_STEP",
        output: {
          note: "Operator transition from frontend workflow console.",
        },
      },
      null,
      2,
    ),
  );
  const [startFeedback, setStartFeedback] = useState<string | null>(null);
  const [transitionFeedback, setTransitionFeedback] = useState<string | null>(null);

  const activeFilters = useMemo(
    () =>
      [
        status !== "ALL" ? { key: "status", value: status } : null,
        type !== "ALL" ? { key: "type", value: type } : null,
      ].filter((item): item is NonNullable<typeof item> => item !== null),
    [status, type],
  );

  function submitStartInstance() {
    setStartFeedback(null);
    let payload: Record<string, unknown>;
    try {
      payload = parseJsonObject(startPayloadText);
    } catch (error) {
      setStartFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
      return;
    }

    startInstance.mutate(payload, {
      onSuccess: () => {
        setStartFeedback("Workflow instance start request accepted.");
      },
      onError: () => {
        setStartFeedback("Workflow instance start failed. Verify definition, payload, and trust context.");
      },
    });
  }

  function submitTransitionInstance() {
    setTransitionFeedback(null);
    if (!transitionInstanceId.trim()) {
      setTransitionFeedback("Instance id is required.");
      return;
    }

    let payload: Record<string, unknown>;
    try {
      payload = parseJsonObject(transitionPayloadText);
    } catch (error) {
      setTransitionFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
      return;
    }

    transitionInstance.mutate(
      { instanceId: transitionInstanceId.trim(), payload },
      {
        onSuccess: () => {
          setTransitionFeedback("Workflow transition request accepted.");
        },
        onError: () => {
          setTransitionFeedback("Workflow transition failed. Verify instance, action, and trust context.");
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Workflow Orchestration"
        subtitle="Operator visibility for workflow definitions, live instances, and orchestration health"
      >
        <div className="space-y-4">
          <div className="grid gap-3 md:grid-cols-3">
            {[
              {
                label: "Workflow feed",
                value: items.length,
                detail: isError ? "feed unavailable" : "from /internal/v1/workflows",
              },
              {
                label: "Definitions",
                value: definitions.items.length,
                detail: definitions.isError ? "definitions unavailable" : "from /internal/v1/workflows/definitions",
              },
              {
                label: "Instances",
                value: instances.items.length,
                detail: instances.isError ? "instances unavailable" : "from /internal/v1/workflows/instances",
              },
            ].map((metric) => (
              <section key={metric.label} className="impilo-surface-card p-4">
                <p className="text-xs uppercase tracking-wide text-slate-500">{metric.label}</p>
                <p className="mt-1 text-2xl font-semibold text-slate-900">{metric.value}</p>
                <p className="mt-1 text-xs text-slate-600">{metric.detail}</p>
              </section>
            ))}
          </div>

          <OperatorTelemetryPanel
            title="Workflow telemetry"
            kind="workflow"
            items={items}
            isLoading={isLoading}
            isError={isError}
            detail="Backed by /internal/v1/workflows."
            emptyLabel="No workflow events available."
            maxItems={12}
            controls={
              <div className="grid gap-2 sm:grid-cols-2">
                <label className="text-xs text-slate-600">
                  Workflow status
                  <select
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                    value={status}
                    onChange={(event) => setFilter({ status: event.target.value })}
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
                    value={type}
                    onChange={(event) => setFilter({ type: event.target.value })}
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
            activeFilters={activeFilters}
            onResetFilters={resetFilters}
            focusedRowId={focus}
          />

          <div className="grid gap-3 md:grid-cols-2">
            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Start workflow instance</h3>
              <p className="mt-1 text-xs text-slate-600">
                Sends a live command to <code>/internal/v1/workflows/instances</code>. No fixture mode.
              </p>
              <textarea
                className="mt-3 h-40 w-full rounded-md border border-slate-300 bg-white p-2 font-mono text-xs text-slate-800"
                value={startPayloadText}
                onChange={(event) => setStartPayloadText(event.target.value)}
              />
              <div className="mt-2 flex items-center gap-2">
                <button
                  type="button"
                  className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  onClick={submitStartInstance}
                  disabled={startInstance.isPending}
                >
                  {startInstance.isPending ? "Starting..." : "Start instance"}
                </button>
                {startFeedback ? <span className="text-xs text-slate-700">{startFeedback}</span> : null}
              </div>
            </section>

            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Transition workflow instance</h3>
              <p className="mt-1 text-xs text-slate-600">
                Sends a live command to <code>/internal/v1/workflows/instances/:id/transition</code>.
              </p>
              <label className="mt-3 block text-xs text-slate-600">
                Instance id
                <input
                  className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-800"
                  value={transitionInstanceId}
                  onChange={(event) => setTransitionInstanceId(event.target.value)}
                  placeholder="Workflow instance UUID"
                />
              </label>
              <textarea
                className="mt-3 h-32 w-full rounded-md border border-slate-300 bg-white p-2 font-mono text-xs text-slate-800"
                value={transitionPayloadText}
                onChange={(event) => setTransitionPayloadText(event.target.value)}
              />
              <div className="mt-2 flex items-center gap-2">
                <button
                  type="button"
                  className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  onClick={submitTransitionInstance}
                  disabled={transitionInstance.isPending || !transitionInstanceId.trim()}
                >
                  {transitionInstance.isPending ? "Transitioning..." : "Submit transition"}
                </button>
                {transitionFeedback ? <span className="text-xs text-slate-700">{transitionFeedback}</span> : null}
              </div>
            </section>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Workflow definitions</h3>
              <p className="mt-1 text-xs text-slate-600">
                Canonical definitions exposed by workflow service through the Experience edge.
              </p>
              {definitions.isLoading ? (
                <p className="mt-3 text-sm text-slate-600">Loading definitions...</p>
              ) : definitions.isError ? (
                <p className="mt-3 text-sm text-rose-700">Workflow definitions unavailable.</p>
              ) : definitions.items.length === 0 ? (
                <p className="mt-3 text-sm text-slate-600">No workflow definitions returned.</p>
              ) : (
                <ol className="mt-3 space-y-2">
                  {definitions.items.slice(0, 8).map((entry, index) => {
                    const id = readOperatorString(entry, ["workflowCode", "id", "definitionId"], `definition-${index + 1}`);
                    const name = readOperatorString(entry, ["name", "title"], "Workflow definition");
                    const version = readOperatorString(entry, ["version", "revision"], "n/a");
                    return (
                      <li key={`${id}-${index}`} className="rounded-md border border-slate-200 bg-slate-50 p-2">
                        <p className="text-sm font-medium text-slate-900">{name}</p>
                        <p className="text-xs text-slate-600">{id}</p>
                        <p className="text-xs text-slate-600">Version: {version}</p>
                      </li>
                    );
                  })}
                </ol>
              )}
            </section>

            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Workflow instances</h3>
              <p className="mt-1 text-xs text-slate-600">
                Runtime instances for operational monitoring and handoff continuity.
              </p>
              {instances.isLoading ? (
                <p className="mt-3 text-sm text-slate-600">Loading instances...</p>
              ) : instances.isError ? (
                <p className="mt-3 text-sm text-rose-700">Workflow instances unavailable.</p>
              ) : instances.items.length === 0 ? (
                <p className="mt-3 text-sm text-slate-600">No workflow instances returned.</p>
              ) : (
                <ol className="mt-3 space-y-2">
                  {instances.items.slice(0, 8).map((entry, index) => {
                    const id = readOperatorString(entry, ["id", "instanceId", "workflowInstanceId"], `instance-${index + 1}`);
                    const state = readOperatorString(entry, ["state", "status"], "UNKNOWN");
                    const workflowRef = readOperatorString(entry, ["workflowCode", "workflowRef", "definitionId"], "workflow");
                    return (
                      <li key={`${id}-${index}`} className="rounded-md border border-slate-200 bg-slate-50 p-2">
                        <p className="text-sm font-medium text-slate-900">{id}</p>
                        <p className="text-xs text-slate-600">Workflow: {workflowRef}</p>
                        <p className="text-xs text-slate-600">State: {state}</p>
                        <button
                          type="button"
                          className="mt-2 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
                          onClick={() => setTransitionInstanceId(id)}
                        >
                          Use for transition
                        </button>
                      </li>
                    );
                  })}
                </ol>
              )}
            </section>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
