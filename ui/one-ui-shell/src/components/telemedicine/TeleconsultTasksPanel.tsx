"use client";

import { useState } from "react";
import { AlertTriangle, CheckCircle2, ClipboardList, Loader2, Plus } from "lucide-react";
import {
  useReferralTasks,
  useCreateReferralTask,
  usePlaceReferralOrder,
  type ReferralTask,
} from "@/hooks/queries/useTelemedicine";

/**
 * TM-B7 — the in-session execution + follow-up loop for a teleconsult referral.
 *
 * Surfaces the referral-scoped tasks (follow-ups, result reviews, patient actions) that the
 * closure precondition guards on: a task marked "blocks closure" holds the consultation open
 * until it is resolved. Also exposes a provenance-stamped coded-order shortcut so an order placed
 * here carries source_ref = the referral id (linking the fulfilment trail back to the case and
 * arming the OROS duplicate-order guard).
 */
export function TeleconsultTasksPanel({
  sessionId,
  patientCpid,
}: {
  sessionId: string;
  patientCpid?: string;
}) {
  const { data, isLoading, isError } = useReferralTasks(sessionId);
  const tasks = data?.data ?? [];
  const openBlocking = tasks.filter((t) => t.blocksClosure && !isResolved(t));

  return (
    <section
      className="rounded-xl border border-border bg-card p-4 shadow-sm"
      data-testid="teleconsult-tasks-panel"
    >
      <div className="flex items-center gap-2">
        <ClipboardList className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Follow-up &amp; tasks</h3>
        {openBlocking.length > 0 ? (
          <span
            className="ml-auto inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800"
            data-testid="teleconsult-blocking-badge"
          >
            <AlertTriangle className="h-3 w-3" />
            {openBlocking.length} blocking closure
          </span>
        ) : null}
      </div>

      <p className="mt-1 text-xs text-muted-foreground">
        Tasks that block closure must be resolved before this consultation can be completed.
      </p>

      {isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading tasks…
        </div>
      ) : isError ? (
        <p className="mt-3 text-sm text-red-600">Unable to load tasks. Check BFF/PCT availability.</p>
      ) : tasks.length === 0 ? (
        <p className="mt-3 text-sm text-muted-foreground">No tasks on this consultation yet.</p>
      ) : (
        <ul className="mt-3 space-y-1.5" data-testid="teleconsult-tasks-list">
          {tasks.map((task) => (
            <TaskRow key={task.taskId} task={task} />
          ))}
        </ul>
      )}

      <CreateTaskForm sessionId={sessionId} />
      {patientCpid ? <PlaceCodedOrderForm sessionId={sessionId} patientCpid={patientCpid} /> : null}
    </section>
  );
}

function isResolved(task: ReferralTask): boolean {
  const s = (task.status ?? "").toUpperCase();
  return s === "COMPLETED" || s === "CANCELLED";
}

function TaskRow({ task }: { task: ReferralTask }) {
  const resolved = isResolved(task);
  return (
    <li
      className="flex items-start justify-between gap-2 rounded-lg bg-background px-3 py-2"
      data-testid="teleconsult-task-row"
    >
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-foreground">{task.taskType}</span>
          {task.blocksClosure ? (
            <span className="inline-flex items-center gap-1 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-800">
              blocks closure
            </span>
          ) : null}
        </div>
        {task.notes ? <p className="truncate text-xs text-muted-foreground">{task.notes}</p> : null}
      </div>
      <span
        className={`inline-flex shrink-0 items-center gap-1 text-xs font-medium uppercase ${
          resolved ? "text-primary-hover" : "text-muted-foreground"
        }`}
      >
        {resolved ? <CheckCircle2 className="h-3.5 w-3.5" /> : null}
        {task.status}
      </span>
    </li>
  );
}

function CreateTaskForm({ sessionId }: { sessionId: string }) {
  const createTask = useCreateReferralTask(sessionId);
  const [taskType, setTaskType] = useState("FOLLOW_UP");
  const [notes, setNotes] = useState("");
  const [blocksClosure, setBlocksClosure] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await createTask.mutateAsync({ taskType, notes: notes.trim() || undefined, blocksClosure });
      setNotes("");
      setBlocksClosure(false);
    } catch {
      setError("Failed to create task. Verify BFF/PCT availability.");
    }
  }

  return (
    <form
      onSubmit={(e) => void submit(e)}
      className="mt-4 space-y-3 border-t border-border pt-4"
      data-testid="teleconsult-task-form"
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <select
          value={taskType}
          onChange={(e) => setTaskType(e.target.value)}
          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
          aria-label="Task type"
        >
          <option value="FOLLOW_UP">Follow-up</option>
          <option value="RESULT_REVIEW">Result review</option>
          <option value="PATIENT_ACTION">Patient action</option>
          <option value="REFERRAL">Onward referral</option>
          <option value="DOCUMENTATION">Documentation</option>
        </select>
        <input
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Notes / instructions"
          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </div>
      <label className="flex items-center gap-2 text-xs text-muted-foreground">
        <input
          type="checkbox"
          checked={blocksClosure}
          onChange={(e) => setBlocksClosure(e.target.checked)}
          data-testid="teleconsult-task-blocks-closure"
        />
        Must be resolved before this consultation can be completed
      </label>
      {error ? <p className="text-xs text-red-600">{error}</p> : null}
      <button
        type="submit"
        disabled={createTask.isPending}
        className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-xs font-medium text-white hover:opacity-90 disabled:opacity-50"
      >
        {createTask.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
        Add task
      </button>
    </form>
  );
}

function PlaceCodedOrderForm({ sessionId, patientCpid }: { sessionId: string; patientCpid: string }) {
  const placeOrder = usePlaceReferralOrder(sessionId);
  const [orderType, setOrderType] = useState("LAB");
  const [ziboOrderCode, setZiboOrderCode] = useState("");
  const [clinicalNotes, setClinicalNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    try {
      await placeOrder.mutateAsync({
        orderType,
        patientCpid,
        ziboOrderCode: ziboOrderCode.trim() || undefined,
        clinicalNotes: clinicalNotes.trim() || undefined,
      });
      setZiboOrderCode("");
      setClinicalNotes("");
      setSuccess("Order placed with teleconsult provenance.");
    } catch (err) {
      // A0 passthrough surfaces the OROS 409 duplicate-order code verbatim.
      const code = (err as { code?: string; body?: { error?: { code?: string } } })?.body?.error?.code;
      setError(
        code === "CONFLICT"
          ? "An active order for this consultation and item already exists."
          : "Failed to place order. Verify BFF/OROS availability.",
      );
    }
  }

  return (
    <form
      onSubmit={(e) => void submit(e)}
      className="mt-4 space-y-3 border-t border-border pt-4"
      data-testid="teleconsult-order-form"
    >
      <p className="text-xs font-medium text-foreground">Place coded order (linked to this consultation)</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <select
          value={orderType}
          onChange={(e) => setOrderType(e.target.value)}
          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
          aria-label="Order type"
        >
          <option value="LAB">Lab</option>
          <option value="IMAGING">Imaging</option>
        </select>
        <input
          value={ziboOrderCode}
          onChange={(e) => setZiboOrderCode(e.target.value)}
          placeholder="ZIBO code (e.g. ZIBO-CBC)"
          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
        />
      </div>
      <input
        value={clinicalNotes}
        onChange={(e) => setClinicalNotes(e.target.value)}
        placeholder="Clinical notes"
        className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      />
      {error ? <p className="text-xs text-red-600">{error}</p> : null}
      {success ? <p className="text-xs text-primary-hover">{success}</p> : null}
      <button
        type="submit"
        disabled={placeOrder.isPending}
        className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
      >
        {placeOrder.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
        Place order
      </button>
    </form>
  );
}
