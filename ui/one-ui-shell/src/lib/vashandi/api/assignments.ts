import { getVashandi, patchVashandi, postVashandi } from "./client";
import type { CreateAssignmentRequest, VashandiActionResponse, WorkforceAssignment, WorkforceEligibilityResult } from "../types";

export async function listAssignments(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: WorkforceAssignment[] }>("/assignments", params);
}

export async function getAssignment(id: string) {
  return getVashandi<WorkforceAssignment>(`/assignments/${encodeURIComponent(id)}`);
}

export async function createAssignment(body: CreateAssignmentRequest) {
  return postVashandi<WorkforceAssignment>("/assignments", body);
}

export async function updateAssignment(id: string, body: Partial<CreateAssignmentRequest>) {
  return patchVashandi<WorkforceAssignment>(`/assignments/${encodeURIComponent(id)}`, body);
}

export async function precheckAssignment(id: string, body?: Record<string, unknown>) {
  return postVashandi<WorkforceEligibilityResult>(`/assignments/${encodeURIComponent(id)}/precheck`, body);
}

export async function approveAssignment(id: string, body?: Record<string, unknown>) {
  return postVashandi(`/assignments/${encodeURIComponent(id)}/approve`, body);
}

export async function activateAssignment(id: string, body?: Record<string, unknown>) {
  return postVashandi(`/assignments/${encodeURIComponent(id)}/activate`, body);
}

export async function suspendAssignment(id: string, body?: Record<string, unknown>) {
  return postVashandi(`/assignments/${encodeURIComponent(id)}/suspend`, body);
}

export async function endAssignment(id: string, body?: Record<string, unknown>) {
  return postVashandi(`/assignments/${encodeURIComponent(id)}/end`, body);
}

export function assignmentsFromResponse(response: VashandiActionResponse): WorkforceAssignment[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as WorkforceAssignment[];
  if (Array.isArray(record.assignments)) return record.assignments as WorkforceAssignment[];
  if (Array.isArray(data)) return data as WorkforceAssignment[];
  return [];
}
