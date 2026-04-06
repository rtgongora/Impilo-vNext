/**
 * Remote Monitoring Service — Wearable device management.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { MonitoringDevice } from "../types";

const V1 = "/internal/v1/mobile/citizen/monitoring";

export async function fetchDevices(patientId: string): Promise<MonitoringDevice[]> {
  const response = await apiClient.get<{ data: MonitoringDevice[] }>(`${V1}/devices?patientId=${patientId}`);
  return response.data.data;
}

export async function pairDevice(body: {
  patientId: string; deviceName: string; deviceType: string; manufacturer?: string; model?: string;
}): Promise<{ id: string; status: string }> {
  const response = await apiClient.post<{ data: { id: string; status: string } }>(`${V1}/devices`, body);
  return response.data.data;
}

export async function syncDevice(deviceId: string): Promise<void> {
  await apiClient.post(`${V1}/devices/${deviceId}/sync`);
}
