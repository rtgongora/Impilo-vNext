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

export async function syncMonitoringDevice(
  deviceId: string,
  body?: { readings?: Array<{ vitalType?: string; value: number; unit?: string; measuredAt?: string; notes?: string }> },
): Promise<{ readingsIngested: number }> {
  const res = await apiClient.post<{ readingsIngested?: number }>(
    `${BASE}/devices/${encodeURIComponent(deviceId)}/sync`,
    body ?? {},
  );
  return { readingsIngested: res.readingsIngested ?? 0 };
}

export async function ingestMonitoringReadings(body: {
  patientId: string;
  deviceId?: string;
  readings: Array<{ vitalType?: string; value: number; unit?: string; measuredAt?: string; notes?: string }>;
}): Promise<{ readingsIngested: number }> {
  const res = await apiClient.post<{ data?: { readingsIngested?: number } }>(`${BASE}/readings`, body);
  return { readingsIngested: res.data?.readingsIngested ?? 0 };
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
  const rows = res.data ?? [];
  return rows.map(mapReadingRow);
}

export interface MonitoringAlert {
  id: string;
  vitalType: string;
  severity: string;
  status: string;
  observedValue?: number;
  unit: string;
  measuredAt: string;
  escalationStatus: string;
}

function mapAlertRow(row: Row): MonitoringAlert {
  return {
    id: str(row.id, ""),
    vitalType: str(row.vital_type ?? row.vitalType, "UNKNOWN"),
    severity: str(row.severity, "MEDIUM"),
    status: str(row.status, "OPEN"),
    observedValue:
      row.observed_value != null
        ? Number(row.observed_value)
        : row.observedValue != null
          ? Number(row.observedValue)
          : undefined,
    unit: str(row.unit, ""),
    measuredAt: str(row.measured_at ?? row.measuredAt ?? row.created_at ?? row.createdAt, ""),
    escalationStatus: str(row.escalation_status ?? row.escalationStatus, "PENDING_REVIEW"),
  };
}

/** Remote monitoring alerts from wellness personal-data (threshold breaches). */
export async function fetchMonitoringAlerts(patientId: string): Promise<MonitoringAlert[]> {
  const res = await apiClient.get<{ data: Row[] }>(
    `/internal/v1/wellness/personal-data/remote-alerts?patientId=${encodeURIComponent(patientId)}`,
  );
  const rows = res.data ?? [];
  return rows.map(mapAlertRow);
}

export async function reviewMonitoringAlert(
  alertId: string,
  body: { status?: string; reviewNotes?: string },
): Promise<void> {
  await apiClient.post(`/internal/v1/wellness/personal-data/remote-alerts/${encodeURIComponent(alertId)}/review`, {
    status: body.status ?? "REVIEWED",
    reviewNotes: body.reviewNotes,
  });
}
