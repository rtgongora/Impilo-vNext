import { getVashandi, patchVashandi, postVashandi } from "./client";
import type { LeaveAvailability, VashandiActionResponse } from "../types";

export async function listAvailability(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: LeaveAvailability[] }>("/availability", params);
}

export async function createLeave(body: Record<string, unknown>) {
  return postVashandi<LeaveAvailability>("/leave", body);
}

export async function updateLeave(id: string, body: Record<string, unknown>) {
  return patchVashandi<LeaveAvailability>(`/leave/${encodeURIComponent(id)}`, body);
}

export function leaveFromResponse(response: VashandiActionResponse): LeaveAvailability[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as LeaveAvailability[];
  if (Array.isArray(record.leaveRecords)) return record.leaveRecords as LeaveAvailability[];
  if (Array.isArray(data)) return data as LeaveAvailability[];
  return [];
}
