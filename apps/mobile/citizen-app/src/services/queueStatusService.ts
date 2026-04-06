/**
 * Queue Status Service — Real-time citizen queue tracking.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { QueueTicket } from "../types";

const V1 = "/internal/v1/mobile/citizen/queue";

export async function fetchQueueStatus(patientId: string): Promise<QueueTicket[]> {
  const response = await apiClient.get<{ data: QueueTicket[] }>(`${V1}/status?patientId=${patientId}`);
  return response.data.data;
}

export async function joinQueue(body: {
  patientId: string; facilityId: string; facilityName?: string; serviceType?: string;
}): Promise<{ id: string; ticketNumber: string }> {
  const response = await apiClient.post<{ data: { id: string; ticketNumber: string } }>(`${V1}/join`, body);
  return response.data.data;
}
