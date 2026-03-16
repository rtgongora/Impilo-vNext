/**
 * Reminders Service — Citizen medication / appointment reminders.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/reminders/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Reminder, ReminderType, Recurrence } from "../types";

const V1 = "/internal/v1/mobile/citizen/reminders";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function getReminders(params: {
  type?: ReminderType;
  page?: number;
  size?: number;
} = {}): Promise<{ items: Reminder[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.type) query.push(`type=${params.type}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<Reminder>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function createReminder(data: {
  title: string;
  type: ReminderType;
  description?: string;
  dateTime: string;
  recurrence: Recurrence;
}): Promise<Reminder> {
  const response = await apiClient.post<{ data: Reminder }>(V1, data);
  return response.data.data;
}

export async function updateReminder(
  id: string,
  data: Partial<{
    title: string;
    type: ReminderType;
    description: string;
    dateTime: string;
    recurrence: Recurrence;
    enabled: boolean;
  }>
): Promise<Reminder> {
  const response = await apiClient.patch<{ data: Reminder }>(`${V1}/${encodeURIComponent(id)}`, data);
  return response.data.data;
}

export async function deleteReminder(id: string): Promise<void> {
  await apiClient.delete(`${V1}/${encodeURIComponent(id)}`);
}
