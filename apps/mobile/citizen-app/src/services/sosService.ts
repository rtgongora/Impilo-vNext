/**
 * Emergency SOS Service — Emergency contacts and alerts.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { EmergencyContact } from "../types";

const V1 = "/internal/v1/mobile/citizen/sos";

export async function fetchEmergencyContacts(patientId: string): Promise<EmergencyContact[]> {
  const response = await apiClient.get<{ data: EmergencyContact[] }>(`${V1}/contacts?patientId=${patientId}`);
  return response.data.data;
}

export async function addEmergencyContact(body: {
  patientId: string; name: string; relationship: string; phone: string; isPrimary?: boolean;
}): Promise<{ id: string }> {
  const response = await apiClient.post<{ data: { id: string } }>(`${V1}/contacts`, body);
  return response.data.data;
}

export async function deleteEmergencyContact(id: string): Promise<void> {
  await apiClient.delete(`${V1}/contacts/${id}`);
}

export async function triggerSOS(body: {
  patientId: string; latitude?: number; longitude?: number; alertType?: string;
}): Promise<{ id: string; status: string; message: string }> {
  const response = await apiClient.post<{ data: { id: string; status: string; message: string } }>(`${V1}/alert`, body);
  return response.data.data;
}
