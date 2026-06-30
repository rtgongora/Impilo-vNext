/**
 * Equipment Service — Provider/biomedical/facility-admin asset & device operations
 * (WS#5 assets/devices/equipment/IoT).
 *
 * Backend: experience-bff equipment lane → asset-registry-service
 *   GET  /internal/v1/equipment?facility_id=&page=&size=   — registry search (manual tag search)
 *   GET  /internal/v1/equipment/{id}/detail                — equipment profile
 *   POST /internal/v1/equipment/{id}/faults                — report fault (safety -> Rito)
 *   GET  /internal/v1/equipment/maintenance/open           — open maintenance tasks
 *   POST /internal/v1/equipment/maintenance/{taskId}/complete
 *
 * The BFF forwards raw asset-registry payloads ({ items: [...] } or an object),
 * so responses are NOT ApiEnvelope-wrapped — values arrive directly on response.data.
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/equipment";

export interface EquipmentRow {
  equipment_id: string;
  facility_id: string;
  name: string;
  equipment_type: string;
  serial_number?: string | null;
  tag?: string | null;
  status: string;
  criticality: string;
  device_id?: string | null;
  next_calibration?: string | null;
  next_maintenance?: string | null;
}

export interface MaintenanceTask {
  task_id: string;
  equipment_id: string;
  task_type: string;
  title: string;
  status: string;
  priority: string;
}

export type FaultSeverity = "MINOR" | "MAJOR" | "SAFETY";

function items<T>(raw: unknown): T[] {
  if (raw && typeof raw === "object" && Array.isArray((raw as { items?: unknown[] }).items)) {
    return (raw as { items: T[] }).items;
  }
  return [];
}

/** Manual tag/text registry search (camera scan substrate not yet wired — see parity doc). */
export async function searchEquipment(facilityId?: string): Promise<EquipmentRow[]> {
  const qs = facilityId ? `?facility_id=${encodeURIComponent(facilityId)}&size=100` : "?size=100";
  const response = await apiClient.get<unknown>(`${BASE}${qs}`);
  return items<EquipmentRow>(response.data);
}

export async function getEquipmentDetail(equipmentId: string): Promise<Record<string, unknown>> {
  const response = await apiClient.get<Record<string, unknown>>(`${BASE}/${encodeURIComponent(equipmentId)}/detail`);
  return (response.data ?? {}) as Record<string, unknown>;
}

export async function reportFault(input: {
  equipmentId: string;
  summary: string;
  detail?: string;
  severity: FaultSeverity;
  safetyConcern: boolean;
}): Promise<Record<string, unknown>> {
  const response = await apiClient.post<Record<string, unknown>>(
    `${BASE}/${encodeURIComponent(input.equipmentId)}/faults`,
    {
      summary: input.summary,
      detail: input.detail,
      severity: input.severity,
      safetyConcern: input.safetyConcern,
    },
  );
  return (response.data ?? {}) as Record<string, unknown>;
}

export async function listOpenMaintenance(): Promise<MaintenanceTask[]> {
  const response = await apiClient.get<unknown>(`${BASE}/maintenance/open`);
  return items<MaintenanceTask>(response.data);
}

export async function completeMaintenance(taskId: string, returnToService: boolean): Promise<Record<string, unknown>> {
  const response = await apiClient.post<Record<string, unknown>>(
    `${BASE}/maintenance/${encodeURIComponent(taskId)}/complete`,
    { returnToService, serviceReport: "Completed from provider mobile" },
  );
  return (response.data ?? {}) as Record<string, unknown>;
}

/** Confirm receipt of a custody transfer (handover) from the field. */
export async function confirmReceipt(transferId: string): Promise<Record<string, unknown>> {
  const response = await apiClient.post<Record<string, unknown>>(
    `${BASE}/transfers/${encodeURIComponent(transferId)}/receive`,
    {},
  );
  return (response.data ?? {}) as Record<string, unknown>;
}
