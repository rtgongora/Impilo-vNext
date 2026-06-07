/**
 * Citizen monitoring devices — same BFF contract as mobile (`/internal/v1/mobile/citizen/monitoring/*`).
 */

import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/mobile/citizen/monitoring";

type Row = Record<string, unknown>;

function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}

export interface MonitoringDevice {
  id: string;
  deviceName: string;
  deviceType: string;
  manufacturer: string;
  model: string;
  connectionType: string;
  status: string;
  lastSyncAt?: string;
  batteryLevel?: number;
}

function mapDeviceRow(row: Row): MonitoringDevice {
  return {
    id: str(row.id, ""),
    deviceName: str(row.device_name ?? row.deviceName, "Device"),
    deviceType: str(row.device_type ?? row.deviceType, ""),
    manufacturer: str(row.manufacturer, ""),
    model: str(row.model, ""),
    connectionType: str(row.connection_type ?? row.connectionType, "BLUETOOTH"),
    status: str(row.status, "PAIRED"),
    lastSyncAt: row.last_sync_at != null ? str(row.last_sync_at) : row.lastSyncAt != null ? str(row.lastSyncAt) : undefined,
    batteryLevel:
      row.battery_level != null
        ? Number(row.battery_level)
        : row.batteryLevel != null
          ? Number(row.batteryLevel)
          : undefined,
  };
}

export async function fetchMonitoringDevices(patientId: string): Promise<MonitoringDevice[]> {
  const res = await apiClient.get<{ data: Row[] }>(
    `${BASE}/devices?patientId=${encodeURIComponent(patientId)}`,
  );
  const rows = res.data ?? [];
  return rows.map(mapDeviceRow);
}

export async function pairMonitoringDevice(body: {
  patientId: string;
  deviceName: string;
  deviceType: string;
  manufacturer?: string;
  model?: string;
  connectionType?: string;
}): Promise<{ id: string; status: string }> {
  const res = await apiClient.post<{ data: { id: string; status: string } }>(`${BASE}/devices`, {
    patientId: body.patientId,
    deviceName: body.deviceName,
    deviceType: body.deviceType,
    manufacturer: body.manufacturer ?? "",
    model: body.model ?? "",
    connectionType: body.connectionType ?? "BLUETOOTH",
  });
  return res.data ?? { id: "", status: "UNKNOWN" };
}

export async function syncMonitoringDevice(deviceId: string): Promise<void> {
  await apiClient.post(`${BASE}/devices/${encodeURIComponent(deviceId)}/sync`, {});
}

const WELLNESS_VITALS = "/internal/v1/mobile/citizen/wellness/vitals";

export interface MonitoringReading {
  id: string;
  vitalType: string;
  value: number;
  unit: string;
  measuredAt: string;
  source: string;
  notes?: string;
}

function num(v: unknown, fallback = 0): number {
  if (typeof v === "number" && !Number.isNaN(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    return Number.isNaN(n) ? fallback : n;
  }
  return fallback;
}

function mapReadingRow(row: Row): MonitoringReading {
  return {
    id: str(row.id, "reading"),
    vitalType: str(row.vital_type ?? row.vitalType, "UNKNOWN"),
    value: num(row.value),
    unit: str(row.unit, ""),
    measuredAt: str(row.measured_at ?? row.measuredAt, ""),
    source: str(row.source, "MANUAL"),
    notes: row.notes != null ? str(row.notes) : undefined,
  };
}

/** Live vitals from wellness-service (manual, Health Connect, etc.) — not device-sync metadata alone. */
export async function fetchMonitoringReadings(patientId: string, type?: string): Promise<MonitoringReading[]> {
  const params = type ? `&type=${encodeURIComponent(type)}` : "";
  const res = await apiClient.get<{ data: Row[] }>(
    `${WELLNESS_VITALS}?patientId=${encodeURIComponent(patientId)}${params}`,
  );
  const rows = res.data?.data ?? [];
  return rows.map(mapReadingRow);
}
