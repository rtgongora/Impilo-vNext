/**
 * Appointment Service — Citizen appointment management.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/appointments/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Appointment } from "../types";

const V1 = "/internal/v1/mobile/citizen/appointments";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchAppointments(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: Appointment[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<Appointment>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchAppointment(id: string): Promise<Appointment> {
  const response = await apiClient.get<{ data: Appointment }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}

export async function requestAppointment(params: {
  facilityId: string;
  appointmentType: string;
  preferredDate: string;
  reason?: string;
}): Promise<Appointment> {
  const response = await apiClient.post<{ data: Appointment }>(V1, params);
  return response.data.data;
}

export async function cancelAppointment(id: string, reason?: string): Promise<void> {
  await apiClient.post(`${V1}/${encodeURIComponent(id)}/cancel`, { reason });
}

export interface AppointmentCheckInMeta {
  patient_id?: string;
  journey_id?: string;
  encounter_id?: string;
  core_transaction_id?: string;
  queue_token_id?: string;
}

export interface AppointmentCheckInResult {
  data: {
    appointment_id: string;
    status: string;
    encounter_id?: string;
    queue_token_id?: string;
  };
  meta: AppointmentCheckInMeta & {
    request_id?: string;
    correlation_id?: string;
  };
}

export async function checkInAppointment(id: string): Promise<AppointmentCheckInResult> {
  const response = await apiClient.post<AppointmentCheckInResult>(
    `${V1}/${encodeURIComponent(id)}/check-in`,
  );
  return response.data;
}

export interface AppointmentMessage {
  id: string;
  appointmentId: string;
  direction: string;
  message: string;
  sentAt?: string;
  senderActorId?: string;
}

export async function fetchAppointmentMessages(id: string): Promise<AppointmentMessage[]> {
  const response = await apiClient.get<{ data: AppointmentMessage[] }>(
    `${V1}/${encodeURIComponent(id)}/messages`,
  );
  return response.data.data ?? [];
}

export async function sendAppointmentMessage(id: string, message: string): Promise<void> {
  await apiClient.post(`${V1}/${encodeURIComponent(id)}/messages`, { message });
}
