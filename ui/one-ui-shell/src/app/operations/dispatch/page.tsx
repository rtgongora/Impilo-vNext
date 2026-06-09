"use client";

import { useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { OperatorTelemetryPanel } from "@/components/operations/OperatorTelemetryPanel";
import { UnifiedLogisticsMapPanel } from "@/components/operations/UnifiedLogisticsMapPanel";
import { PageShell } from "@/components/PageShell";
import { useDispatchOperatorFeed } from "@/hooks/queries/useCoreTransactionExperience";
import {
  useAssignDispatchTask,
  useCreateDelivery,
  useCreateDispatchTask,
  useCompleteDispatchTask,
  useDispatchConsole,
  useDispatchCouriers,
  useDispatchDashboard,
  useDispatchDeliveryAction,
  useDispatchDeliveries,
  useDispatchFleet,
  useDispatchMissions,
} from "@/hooks/queries/useDispatchOps";
import {
  DISPATCH_STATUS_OPTIONS,
  normalizeFilter,
  readOperatorString,
} from "@/lib/operator-telemetry";
import { DispatchDeliveryOrchestrationPanel } from "@/components/operations/DispatchDeliveryOrchestrationPanel";

type TaskCommandMode = "CREATE_TASK" | "ASSIGN_TASK" | "COMPLETE_TASK";

function parseJsonObject(text: string): Record<string, unknown> {
  const parsed = JSON.parse(text);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Payload must be a JSON object.");
  }
  return parsed as Record<string, unknown>;
}

export default function DispatchOperationsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const status = normalizeFilter(searchParams.get("status"), DISPATCH_STATUS_OPTIONS);
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
    setFilter({ status: null, focus: null });
  };

  const { items, isLoading, isError } = useDispatchOperatorFeed({
    status: status === "ALL" ? undefined : status,
  });
  const dashboard = useDispatchDashboard();
  const deliveries = useDispatchDeliveries();
  const fleet = useDispatchFleet();
  const couriers = useDispatchCouriers();
  const missions = useDispatchMissions();
  const consoleView = useDispatchConsole();
  const createDelivery = useCreateDelivery();
  const createTask = useCreateDispatchTask();
  const assignTask = useAssignDispatchTask();
  const completeTask = useCompleteDispatchTask();
  const deliveryAction = useDispatchDeliveryAction();
  const [payloadText, setPayloadText] = useState(
    JSON.stringify(
      {
        reference: "DELIVERY-REF",
        priority: "NORMAL",
      },
      null,
      2,
    ),
  );
  const [createFeedback, setCreateFeedback] = useState<string | null>(null);
  const [taskMode, setTaskMode] = useState<TaskCommandMode>("CREATE_TASK");
  const [taskId, setTaskId] = useState(focus ?? "");
  const [taskPayloadText, setTaskPayloadText] = useState(
    JSON.stringify(
      {
        type: "DELIVERY_TASK",
        priority: "NORMAL",
        assigneeId: "courier:current",
      },
      null,
      2,
    ),
  );
  const [taskFeedback, setTaskFeedback] = useState<string | null>(null);
  const [deliveryActionId, setDeliveryActionId] = useState("");
  const [deliveryActionName, setDeliveryActionName] = useState("dispatch");
  const [deliveryActionPayloadText, setDeliveryActionPayloadText] = useState(
    JSON.stringify(
      {
        source: "operations-dispatch-console",
      },
      null,
      2,
    ),
  );
  const [deliveryActionFeedback, setDeliveryActionFeedback] = useState<string | null>(null);

  const activeFilters = useMemo(
    () =>
      [
        status !== "ALL" ? { key: "status", value: status } : null,
      ].filter((item): item is NonNullable<typeof item> => item !== null),
    [status],
  );

  const dispatchMetrics = useMemo(
    () => [
      {
        label: "Total deliveries",
        value: (dashboard.data?.data as { total_deliveries?: number } | undefined)?.total_deliveries ?? "-",
      },
      {
        label: "In transit",
        value: (dashboard.data?.data as { in_transit?: number } | undefined)?.in_transit ?? "-",
      },
      {
        label: "Delivered",
        value: (dashboard.data?.data as { delivered?: number } | undefined)?.delivered ?? "-",
      },
      {
        label: "Active fleet",
        value: (dashboard.data?.data as { active_fleet_assets?: number } | undefined)?.active_fleet_assets ?? "-",
      },
      {
        label: "Courier records",
        value: couriers.data?.data && typeof couriers.data.data === "object" && "items" in (couriers.data.data as object)
          ? String(((couriers.data.data as { items?: unknown[] }).items ?? []).length)
          : "-",
      },
      {
        label: "Mission records",
        value: missions.data?.data && typeof missions.data.data === "object" && "items" in (missions.data.data as object)
          ? String(((missions.data.data as { items?: unknown[] }).items ?? []).length)
          : "-",
      },
    ],
    [dashboard.data?.data, couriers.data?.data, missions.data?.data],
  );

  function submitCreateDelivery() {
    setCreateFeedback(null);
    let payload: Record<string, unknown>;
    try {
      const parsed = JSON.parse(payloadText);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("Payload must be a JSON object.");
      }
      payload = parsed as Record<string, unknown>;
    } catch (error) {
      setCreateFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
      return;
    }

    createDelivery.mutate(payload, {
      onSuccess: () => {
        setCreateFeedback("Dispatch delivery creation request accepted.");
      },
      onError: () => {
        setCreateFeedback("Dispatch delivery creation failed. Verify payload and trust context.");
      },
    });
  }

  function submitTaskCommand() {
    setTaskFeedback(null);
    let payload: Record<string, unknown>;
    try {
      payload = parseJsonObject(taskPayloadText);
    } catch (error) {
      setTaskFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
      return;
    }

    if (taskMode === "CREATE_TASK") {
      createTask.mutate(payload, {
        onSuccess: () => setTaskFeedback("Dispatch task creation request accepted."),
        onError: () => setTaskFeedback("Dispatch task creation failed. Verify payload and trust context."),
      });
      return;
    }

    if (!taskId.trim()) {
      setTaskFeedback("Task id is required for assign/complete commands.");
      return;
    }

    if (taskMode === "ASSIGN_TASK") {
      assignTask.mutate(
        { taskId: taskId.trim(), payload },
        {
          onSuccess: () => setTaskFeedback("Dispatch task assignment accepted."),
          onError: () => setTaskFeedback("Dispatch task assignment failed. Verify task, assignee, and trust context."),
        },
      );
      return;
    }

    completeTask.mutate(
      { taskId: taskId.trim(), payload },
      {
        onSuccess: () => setTaskFeedback("Dispatch task completion accepted."),
        onError: () => setTaskFeedback("Dispatch task completion failed. Verify task and trust context."),
      },
    );
  }

  function submitDeliveryAction() {
    setDeliveryActionFeedback(null);
    if (!deliveryActionId.trim() || !deliveryActionName.trim()) {
      setDeliveryActionFeedback("Delivery id and action are required.");
      return;
    }

    let payload: Record<string, unknown>;
    try {
      payload = parseJsonObject(deliveryActionPayloadText);
    } catch (error) {
      setDeliveryActionFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
      return;
    }

    deliveryAction.mutate(
      {
        deliveryId: deliveryActionId.trim(),
        action: deliveryActionName.trim(),
        payload,
      },
      {
        onSuccess: () => setDeliveryActionFeedback("Delivery action request accepted."),
        onError: () => setDeliveryActionFeedback("Delivery action failed. Verify delivery, action, and trust context."),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Dispatch Operations"
        subtitle="Operational dispatch telemetry plus backend fleet, courier, mission, and delivery control surfaces"
      >
        <div className="space-y-4">
          <DispatchDeliveryOrchestrationPanel />
          <div className="grid gap-3 md:grid-cols-3">
            {dispatchMetrics.map((metric) => (
              <section key={metric.label} className="impilo-surface-card p-4">
                <p className="text-xs uppercase tracking-wide text-slate-500">{metric.label}</p>
                <p className="mt-1 text-2xl font-semibold text-slate-900">{String(metric.value)}</p>
              </section>
            ))}
          </div>

          <UnifiedLogisticsMapPanel
            title="Dispatch + Nhume map"
            subtitle="Task ops and last-mile deliveries on shared Ndila tiles"
            includeDispatch
            includeNhume
          />

          <div className="grid gap-3 md:grid-cols-2">
            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Dispatch backend datasets</h3>
              <p className="mt-1 text-xs text-slate-600">
                Surfacing backend controllers beyond task feed: deliveries, fleet, couriers, missions, and console.
              </p>
              <ul className="mt-3 space-y-1 text-sm text-slate-700">
                <li>Deliveries: {deliveries.isError ? "unavailable" : "connected"}</li>
                <li>Fleet: {fleet.isError ? "unavailable" : "connected"}</li>
                <li>Couriers: {couriers.isError ? "unavailable" : "connected"}</li>
                <li>Missions: {missions.isError ? "unavailable" : "connected"}</li>
                <li>Dispatcher console: {consoleView.isError ? "unavailable" : "connected"}</li>
              </ul>
            </section>
            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Create delivery command</h3>
              <p className="mt-1 text-xs text-slate-600">
                Sends live command to <code>/internal/v1/dispatch/deliveries</code>. No demo mode.
              </p>
              <textarea
                className="mt-3 h-32 w-full rounded-md border border-slate-300 bg-white p-2 font-mono text-xs text-slate-800"
                value={payloadText}
                onChange={(event) => setPayloadText(event.target.value)}
              />
              <div className="mt-2 flex items-center gap-2">
                <button
                  type="button"
                  className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  onClick={submitCreateDelivery}
                  disabled={createDelivery.isPending}
                >
                  {createDelivery.isPending ? "Submitting..." : "Submit delivery"}
                </button>
                {createFeedback ? (
                  <span className="text-xs text-slate-700">{createFeedback}</span>
                ) : null}
              </div>
            </section>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Dispatch task command console</h3>
              <p className="mt-1 text-xs text-slate-600">
                Uses live task commands: create, assign, and complete. No demo mode.
              </p>
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                <label className="text-xs text-slate-600">
                  Command
                  <select
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                    value={taskMode}
                    onChange={(event) => setTaskMode(event.target.value as TaskCommandMode)}
                  >
                    <option value="CREATE_TASK">Create task</option>
                    <option value="ASSIGN_TASK">Assign task</option>
                    <option value="COMPLETE_TASK">Complete task</option>
                  </select>
                </label>
                <label className="text-xs text-slate-600">
                  Task id
                  <input
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-800"
                    value={taskId}
                    onChange={(event) => setTaskId(event.target.value)}
                    placeholder="Required for assign/complete"
                  />
                </label>
              </div>
              <textarea
                className="mt-3 h-32 w-full rounded-md border border-slate-300 bg-white p-2 font-mono text-xs text-slate-800"
                value={taskPayloadText}
                onChange={(event) => setTaskPayloadText(event.target.value)}
              />
              <div className="mt-2 flex items-center gap-2">
                <button
                  type="button"
                  className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  onClick={submitTaskCommand}
                  disabled={createTask.isPending || assignTask.isPending || completeTask.isPending}
                >
                  {createTask.isPending || assignTask.isPending || completeTask.isPending
                    ? "Submitting..."
                    : "Submit task command"}
                </button>
                {taskFeedback ? <span className="text-xs text-slate-700">{taskFeedback}</span> : null}
              </div>
            </section>

            <section className="impilo-surface-card p-4">
              <h3 className="text-sm font-semibold text-slate-900">Delivery action command</h3>
              <p className="mt-1 text-xs text-slate-600">
                Sends live action commands to <code>/internal/v1/dispatch/deliveries/:id/:action</code>.
              </p>
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                <label className="text-xs text-slate-600">
                  Delivery id
                  <input
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-800"
                    value={deliveryActionId}
                    onChange={(event) => setDeliveryActionId(event.target.value)}
                    placeholder="Delivery UUID/reference"
                  />
                </label>
                <label className="text-xs text-slate-600">
                  Action
                  <input
                    className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-800"
                    value={deliveryActionName}
                    onChange={(event) => setDeliveryActionName(event.target.value)}
                    placeholder="dispatch, cancel, complete..."
                  />
                </label>
              </div>
              <textarea
                className="mt-3 h-32 w-full rounded-md border border-slate-300 bg-white p-2 font-mono text-xs text-slate-800"
                value={deliveryActionPayloadText}
                onChange={(event) => setDeliveryActionPayloadText(event.target.value)}
              />
              <div className="mt-2 flex items-center gap-2">
                <button
                  type="button"
                  className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  onClick={submitDeliveryAction}
                  disabled={deliveryAction.isPending || !deliveryActionId.trim() || !deliveryActionName.trim()}
                >
                  {deliveryAction.isPending ? "Submitting..." : "Submit delivery action"}
                </button>
                {deliveryActionFeedback ? (
                  <span className="text-xs text-slate-700">{deliveryActionFeedback}</span>
                ) : null}
              </div>
            </section>
          </div>

          <OperatorTelemetryPanel
            title="Dispatch telemetry"
            kind="dispatch"
            items={items}
            isLoading={isLoading}
            isError={isError}
            detail="Backed by /internal/v1/dispatch/tasks."
            emptyLabel="No dispatch events available."
            maxItems={12}
            controls={
              <label className="text-xs text-slate-600">
                Dispatch status
                <select
                  className="mt-1 w-full rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
                  value={status}
                  onChange={(event) => setFilter({ status: event.target.value })}
                >
                  {DISPATCH_STATUS_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
            }
            activeFilters={activeFilters}
            onResetFilters={resetFilters}
            focusedRowId={focus}
          />

          <section className="impilo-surface-card p-4">
            <h3 className="text-sm font-semibold text-slate-900">Console preview</h3>
            <p className="mt-1 text-xs text-slate-600">
              Top records from dispatcher console payload for rapid backend visibility.
            </p>
            {consoleView.isLoading ? (
              <p className="mt-3 text-sm text-slate-600">Loading console payload...</p>
            ) : consoleView.isError ? (
              <p className="mt-3 text-sm text-rose-700">Dispatcher console unavailable.</p>
            ) : (
              <ol className="mt-3 space-y-2">
                {(
                  (consoleView.data?.data as { deliveries?: Array<Record<string, unknown>> } | undefined)?.deliveries ??
                  []
                )
                  .slice(0, 6)
                  .map((entry, index) => {
                    const id = readOperatorString(entry, ["id", "deliveryId", "reference"], `delivery-${index + 1}`);
                    const state = readOperatorString(entry, ["status", "state"], "UNKNOWN");
                    return (
                      <li key={`${id}-${index}`} className="rounded-md border border-slate-200 bg-slate-50 p-2">
                        <p className="text-sm font-medium text-slate-900">{id}</p>
                        <p className="text-xs text-slate-600">State: {state}</p>
                        <button
                          type="button"
                          className="mt-2 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100"
                          onClick={() => setDeliveryActionId(id)}
                        >
                          Use for delivery action
                        </button>
                      </li>
                    );
                  })}
              </ol>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
