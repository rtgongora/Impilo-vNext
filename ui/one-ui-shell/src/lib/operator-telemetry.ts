"use client";

export type AnyRecord = Record<string, unknown>;
export type WorkflowStatusFilter = "ALL" | "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED" | "BLOCKED";
export type WorkflowTypeFilter = "ALL" | "CLINICAL" | "FINANCIAL" | "DISPATCH" | "REGISTRY";
export type DispatchStatusFilter = "ALL" | "PENDING" | "ASSIGNED" | "IN_PROGRESS" | "DELIVERED" | "FAILED";

export const WORKFLOW_STATUS_OPTIONS: WorkflowStatusFilter[] = [
  "ALL",
  "PENDING",
  "IN_PROGRESS",
  "COMPLETED",
  "FAILED",
  "BLOCKED",
];

export const WORKFLOW_TYPE_OPTIONS: WorkflowTypeFilter[] = [
  "ALL",
  "CLINICAL",
  "FINANCIAL",
  "DISPATCH",
  "REGISTRY",
];

export const DISPATCH_STATUS_OPTIONS: DispatchStatusFilter[] = [
  "ALL",
  "PENDING",
  "ASSIGNED",
  "IN_PROGRESS",
  "DELIVERED",
  "FAILED",
];

export function normalizeFilter<T extends string>(value: string | null, options: readonly T[]): T {
  if (value && (options as readonly string[]).includes(value)) {
    return value as T;
  }
  return options[0];
}

export interface OperatorTelemetryRow {
  id: string;
  type: string;
  transition: string;
  assignee?: string;
  timestampLabel: string;
  severity: "normal" | "warning" | "critical";
}

export function readOperatorString(record: AnyRecord, keys: string[], fallback: string): string {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim().length > 0) return value;
    if (typeof value === "number" || typeof value === "boolean") return String(value);
  }
  return fallback;
}

export function readOperatorTimestamp(record: AnyRecord): string {
  return readOperatorString(
    record,
    ["updatedAt", "lastUpdatedAt", "occurredAt", "createdAt", "timestamp", "startedAt"],
    "",
  );
}

export function formatOperatorTimestamp(raw: string): string {
  if (!raw) return "time unknown";
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) return raw;
  return parsed.toLocaleString();
}

export function operatorTransition(record: AnyRecord): string {
  const before = readOperatorString(record, ["stateBefore", "previousState", "previousStatus"], "");
  const after = readOperatorString(record, ["stateAfter", "state", "status"], "UNKNOWN");
  return before ? `${before} -> ${after}` : after;
}

export function transitionSeverity(transition: string): OperatorTelemetryRow["severity"] {
  const normalized = transition.toUpperCase();
  if (normalized.includes("FAILED") || normalized.includes("BLOCKED") || normalized.includes("ERROR")) {
    return "critical";
  }
  if (normalized.includes("PENDING") || normalized.includes("IN_PROGRESS") || normalized.includes("QUEUED")) {
    return "warning";
  }
  return "normal";
}

export function toWorkflowTelemetryRow(record: AnyRecord, index: number): OperatorTelemetryRow {
  const id = readOperatorString(record, ["id", "workflowId", "correlationId"], `workflow-${index + 1}`);
  const type = readOperatorString(record, ["type", "definition", "workflowType"], "WORKFLOW");
  const transition = operatorTransition(record);
  return {
    id,
    type,
    transition,
    timestampLabel: formatOperatorTimestamp(readOperatorTimestamp(record)),
    severity: transitionSeverity(transition),
  };
}

export function toDispatchTelemetryRow(record: AnyRecord, index: number): OperatorTelemetryRow {
  const id = readOperatorString(record, ["id", "taskId", "dispatchId"], `dispatch-${index + 1}`);
  const transition = operatorTransition(record);
  const assignee = readOperatorString(record, ["assigneeId", "assignedTo", "ownerId"], "UNASSIGNED");
  return {
    id,
    type: "DISPATCH",
    transition,
    assignee,
    timestampLabel: formatOperatorTimestamp(readOperatorTimestamp(record)),
    severity: transitionSeverity(transition),
  };
}
