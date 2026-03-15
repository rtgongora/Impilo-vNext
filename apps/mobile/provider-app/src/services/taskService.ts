/**
 * Task Service — Worklist/task queue management.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/tasks/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Task, TaskStatus, TaskPriority } from "../types";

interface TaskResource {
  id: string;
  type: "Task";
  attributes: {
    title: string;
    description?: string;
    assignee_id: string;
    assignee_name: string;
    patient_id?: string;
    patient_name?: string;
    facility_id: string;
    task_type: string;
    priority: TaskPriority;
    status: TaskStatus;
    due_at?: string;
    completed_at?: string;
    escalated_to?: string;
    created_at: string;
  };
}

function mapTask(r: TaskResource): Task {
  return {
    id: r.id,
    title: r.attributes.title,
    description: r.attributes.description,
    assigneeId: r.attributes.assignee_id,
    assigneeName: r.attributes.assignee_name,
    patientId: r.attributes.patient_id,
    patientName: r.attributes.patient_name,
    facilityId: r.attributes.facility_id,
    taskType: r.attributes.task_type,
    priority: r.attributes.priority,
    status: r.attributes.status,
    dueAt: r.attributes.due_at,
    completedAt: r.attributes.completed_at,
    escalatedTo: r.attributes.escalated_to,
    createdAt: r.attributes.created_at,
  };
}

export async function getMyTasks(
  status?: TaskStatus,
  page = 0,
  size = 20
): Promise<{ tasks: Task[]; totalElements: number }> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  const response = await apiClient.get<{
    data: TaskResource[];
    meta: { page: { total_elements: number } };
  }>(`/internal/v1/mobile/provider/tasks/mine?${params.toString()}`);
  return {
    tasks: response.data.data.map(mapTask),
    totalElements: response.data.meta.page.total_elements,
  };
}

export async function getTasksByFacility(
  facilityId: string,
  status?: TaskStatus,
  page = 0,
  size = 20
): Promise<{ tasks: Task[]; totalElements: number }> {
  const params = new URLSearchParams({
    facility_id: facilityId,
    page: String(page),
    size: String(size),
  });
  if (status) params.set("status", status);
  const response = await apiClient.get<{
    data: TaskResource[];
    meta: { page: { total_elements: number } };
  }>(`/internal/v1/mobile/provider/tasks?${params.toString()}`);
  return {
    tasks: response.data.data.map(mapTask),
    totalElements: response.data.meta.page.total_elements,
  };
}

export async function getTask(id: string): Promise<Task> {
  const response = await apiClient.get<{ data: TaskResource }>(
    `/internal/v1/mobile/provider/tasks/${id}`
  );
  return mapTask(response.data.data);
}

export async function updateTaskStatus(
  id: string,
  status: TaskStatus
): Promise<Task> {
  const response = await apiClient.patch<{ data: TaskResource }>(
    `/internal/v1/mobile/provider/tasks/${id}`,
    { status }
  );
  return mapTask(response.data.data);
}

export async function escalateTask(
  id: string,
  escalateTo: string,
  reason: string
): Promise<Task> {
  const response = await apiClient.post<{ data: TaskResource }>(
    `/internal/v1/mobile/provider/tasks/${id}/escalate`,
    { escalate_to: escalateTo, reason }
  );
  return mapTask(response.data.data);
}

export async function completeTask(id: string): Promise<Task> {
  const response = await apiClient.post<{ data: TaskResource }>(
    `/internal/v1/mobile/provider/tasks/${id}/complete`
  );
  return mapTask(response.data.data);
}
