/**
 * Vashandi operational workforce — mobile BFF (`ProviderVashandiController`).
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/mobile/provider/vashandi";

export interface VashandiRosterRow {
  id: string;
  rosterType: string;
  status: string;
  periodStart: string;
  periodEnd: string;
  facilityId?: string | null;
  departmentId?: string | null;
  unitId?: string | null;
}

export interface VashandiShiftRow {
  id: string;
  rosterId: string;
  workforceProfileId: string;
  shiftType: string;
  status: string;
  startTime: string;
  endTime: string;
  facilityId?: string | null;
  checkInRequired: boolean;
}

export interface VashandiAttendanceRow {
  id: string;
  shiftId?: string | null;
  workforceProfileId: string;
  eventType: string;
  eventTime: string;
  checkInMode?: string | null;
  offline: boolean;
}

export interface VashandiAvailabilityRow {
  id: string;
  workforceProfileId: string;
  leaveType: string;
  status: string;
  startDate: string;
  endDate: string;
}

export interface VashandiCheckInInput {
  shiftId: string;
  workforceProfileId: string;
  checkInMode?: string;
  deviceId?: string;
  offline?: boolean;
}

export interface VashandiCheckOutInput {
  shiftId: string;
  workforceProfileId: string;
  checkInMode?: string;
  offline?: boolean;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function extractPayload(raw: unknown): unknown {
  const root = asRecord(raw);
  const data = root.data ?? raw;
  if (data && typeof data === "object" && "data" in (data as Record<string, unknown>)) {
    return (data as Record<string, unknown>).data;
  }
  return data;
}

function extractItems(raw: unknown): unknown[] {
  const payload = extractPayload(raw);
  if (Array.isArray(payload)) return payload;
  const record = asRecord(payload);
  if (Array.isArray(record.items)) return record.items;
  return [];
}

function readString(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value != null && String(value).trim() !== "") return String(value);
  }
  return "";
}

function readBool(row: Record<string, unknown>, key: string, fallback = false): boolean {
  const value = row[key];
  if (typeof value === "boolean") return value;
  if (value === "true") return true;
  if (value === "false") return false;
  return fallback;
}

function normalizeRoster(row: unknown): VashandiRosterRow {
  const r = asRecord(row);
  return {
    id: readString(r, "id"),
    rosterType: readString(r, "rosterType", "roster_type") || "operational",
    status: readString(r, "status") || "draft",
    periodStart: readString(r, "periodStart", "period_start"),
    periodEnd: readString(r, "periodEnd", "period_end"),
    facilityId: readString(r, "facilityId", "facility_id") || null,
    departmentId: readString(r, "departmentId", "department_id") || null,
    unitId: readString(r, "unitId", "unit_id") || null,
  };
}

function normalizeShift(row: unknown): VashandiShiftRow {
  const r = asRecord(row);
  return {
    id: readString(r, "id"),
    rosterId: readString(r, "rosterId", "roster_id"),
    workforceProfileId: readString(r, "workforceProfileId", "workforce_profile_id"),
    shiftType: readString(r, "shiftType", "shift_type") || "day",
    status: readString(r, "status") || "scheduled",
    startTime: readString(r, "startTime", "start_time"),
    endTime: readString(r, "endTime", "end_time"),
    facilityId: readString(r, "facilityId", "facility_id") || null,
    checkInRequired: readBool(r, "checkInRequired", true) || readBool(r, "check_in_required", true),
  };
}

function normalizeAttendance(row: unknown): VashandiAttendanceRow {
  const r = asRecord(row);
  return {
    id: readString(r, "id"),
    shiftId: readString(r, "shiftId", "shift_id") || null,
    workforceProfileId: readString(r, "workforceProfileId", "workforce_profile_id"),
    eventType: readString(r, "eventType", "event_type") || "check_in",
    eventTime: readString(r, "eventTime", "event_time"),
    checkInMode: readString(r, "checkInMode", "check_in_mode") || null,
    offline: readBool(r, "offline"),
  };
}

function normalizeAvailability(row: unknown): VashandiAvailabilityRow {
  const r = asRecord(row);
  return {
    id: readString(r, "id"),
    workforceProfileId: readString(r, "workforceProfileId", "workforce_profile_id"),
    leaveType: readString(r, "leaveType", "leave_type") || "annual",
    status: readString(r, "status") || "pending",
    startDate: readString(r, "startDate", "start_date"),
    endDate: readString(r, "endDate", "end_date"),
  };
}

export async function fetchMyRosters(query?: Record<string, string>): Promise<VashandiRosterRow[]> {
  const qs = query ? `?${new URLSearchParams(query).toString()}` : "";
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/roster${qs}`);
  return extractItems(r.data).map(normalizeRoster);
}

export async function fetchMyAttendance(query?: Record<string, string>): Promise<VashandiAttendanceRow[]> {
  const qs = query ? `?${new URLSearchParams(query).toString()}` : "";
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/attendance${qs}`);
  return extractItems(r.data).map(normalizeAttendance);
}

export async function fetchMyAvailability(query?: Record<string, string>): Promise<VashandiAvailabilityRow[]> {
  const qs = query ? `?${new URLSearchParams(query).toString()}` : "";
  const r = await apiClient.get<{ data: unknown }>(`${BASE}/availability${qs}`);
  return extractItems(r.data).map(normalizeAvailability);
}

export async function checkInAttendance(input: VashandiCheckInInput): Promise<{ eventId?: string; status: string; message?: string }> {
  const r = await apiClient.post<{ data: unknown }>(`${BASE}/attendance/check-in`, {
    shift_id: input.shiftId,
    workforce_profile_id: input.workforceProfileId,
    event_time: new Date().toISOString(),
    check_in_mode: input.checkInMode ?? "mobile_self_check_in",
    device_id: input.deviceId,
    offline: input.offline ?? false,
  });
  const payload = asRecord(extractPayload(r.data));
  return {
    eventId: readString(payload, "eventId", "event_id", "id") || undefined,
    status: readString(payload, "status") || "completed",
    message: readString(payload, "message") || undefined,
  };
}

export async function checkOutAttendance(input: VashandiCheckOutInput): Promise<{ eventId?: string; status: string; message?: string }> {
  const r = await apiClient.post<{ data: unknown }>(`${BASE}/attendance/check-out`, {
    shift_id: input.shiftId,
    workforce_profile_id: input.workforceProfileId,
    event_time: new Date().toISOString(),
    check_in_mode: input.checkInMode ?? "mobile_self_check_out",
    offline: input.offline ?? false,
  });
  const payload = asRecord(extractPayload(r.data));
  return {
    eventId: readString(payload, "eventId", "event_id", "id") || undefined,
    status: readString(payload, "status") || "completed",
    message: readString(payload, "message") || undefined,
  };
}

/** Shifts from session contract activeRosteredShifts when upstream list is unavailable. */
export function normalizeSessionShifts(raw: Array<Record<string, unknown>> | undefined): VashandiShiftRow[] {
  return (raw ?? []).map(normalizeShift);
}
