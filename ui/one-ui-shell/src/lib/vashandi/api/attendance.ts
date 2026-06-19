import { getVashandi, postVashandi } from "./client";
import type { AttendanceEvent, CheckInRequest, CheckOutRequest, VashandiActionResponse } from "../types";

export async function listAttendance(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: AttendanceEvent[] }>("/attendance", params);
}

export async function checkIn(body: CheckInRequest) {
  return postVashandi<AttendanceEvent>("/attendance/check-in", body);
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
