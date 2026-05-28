import { apiClient } from "@impilo/mobile-api-client";

export type WorkflowDispatchRecord = Record<string, unknown>;

function normalizeItems(value: unknown): WorkflowDispatchRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is WorkflowDispatchRecord => typeof item === "object" && item !== null);
  }
  if (typeof value === "object" && value !== null) {
    const record = value as WorkflowDispatchRecord;
    if (Array.isArray(record.items)) {
      return normalizeItems(record.items);
    }
    if (Array.isArray(record.data)) {
      return normalizeItems(record.data);
    }
    if (Array.isArray(record.content)) {
      return normalizeItems(record.content);
    }
    if (Array.isArray(record.tasks)) {
      return normalizeItems(record.tasks);
    }
  }
  return [];
}

async function getItems(path: string): Promise<WorkflowDispatchRecord[]> {
  const response = await apiClient.get<{ data?: unknown }>(path);
  return normalizeItems(response.data.data);
}

export function listWorkflowFeed() {
  return getItems("/internal/v1/workflows");
}

export function listWorkflowDefinitions() {
  return getItems("/internal/v1/workflows/definitions");
}

export function listWorkflowInstances() {
  return getItems("/internal/v1/workflows/instances");
}

export async function startWorkflowInstance(payload: WorkflowDispatchRecord) {
  const response = await apiClient.post<{ data?: unknown }>("/internal/v1/workflows/instances", payload);
  return response.data.data;
}

export async function transitionWorkflowInstance(instanceId: string, payload: WorkflowDispatchRecord) {
  const response = await apiClient.post<{ data?: unknown }>(
    `/internal/v1/workflows/instances/${encodeURIComponent(instanceId)}/transition`,
    payload,
  );
  return response.data.data;
}

export function listDispatchTasks() {
  return getItems("/internal/v1/dispatch/tasks");
}

export async function createDispatchTask(payload: WorkflowDispatchRecord) {
  const response = await apiClient.post<{ data?: unknown }>("/internal/v1/dispatch/tasks", payload);
  return response.data.data;
}

export async function assignDispatchTask(taskId: string, payload: WorkflowDispatchRecord) {
  const response = await apiClient.patch<{ data?: unknown }>(
    `/internal/v1/dispatch/tasks/${encodeURIComponent(taskId)}/assign`,
    payload,
  );
  return response.data.data;
}

export async function completeDispatchTask(taskId: string, payload: WorkflowDispatchRecord) {
  const response = await apiClient.patch<{ data?: unknown }>(
    `/internal/v1/dispatch/tasks/${encodeURIComponent(taskId)}/complete`,
    payload,
  );
  return response.data.data;
}

export async function runDeliveryAction(
  deliveryId: string,
  action: string,
  payload: WorkflowDispatchRecord,
) {
  const response = await apiClient.post<{ data?: unknown }>(
    `/internal/v1/dispatch/deliveries/${encodeURIComponent(deliveryId)}/${encodeURIComponent(action)}`,
    payload,
  );
  return response.data.data;
}
