import { getVashandi, postVashandi } from "./client";
import type { AttendanceEvent, CheckInRequest, CheckOutRequest, VashandiActionResponse } from "../types";

export async function listAttendance(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: AttendanceEvent[] }>("/attendance", params);
}

export async function checkIn(body: CheckInRequest) {
  return postVashandi<AttendanceEvent>("/attendance/check-in", body);
}

/** Body for ad-hoc self-service check-in, with optional biometric verification. */
export interface AdhocCheckInBody {
  workforceProfileId: string;
  facilityId?: string;
  checkInMode?: string;
  biometricSubjectRef?: string;
  biometricModality?: string;
  biometricProbeBase64?: string;
}

/** Self-service check-in without a rostered shift — anchored to facility/assignment context. */
export async function adhocCheckIn(body: AdhocCheckInBody) {
  return postVashandi<AttendanceEvent>("/attendance/adhoc-check-in", body);
}

export async function checkOut(body: CheckOutRequest) {
  return postVashandi<AttendanceEvent>("/attendance/check-out", body);
}

export async function supervisorConfirmAttendance(body: Record<string, unknown>) {
  return postVashandi<AttendanceEvent>("/attendance/supervisor-confirm", body);
}

export function attendanceFromResponse(response: VashandiActionResponse): AttendanceEvent[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as AttendanceEvent[];
  if (Array.isArray(record.events)) return record.events as AttendanceEvent[];
  if (Array.isArray(data)) return data as AttendanceEvent[];
  return [];
}
