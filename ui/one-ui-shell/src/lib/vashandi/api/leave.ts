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

export interface LeaveType {
  id: string;
  name: string;
  annualEntitlement?: number;
  carryOverMax?: number;
  requiresApproval?: boolean;
}

export interface LeaveBalance {
  id: string;
  workforceProfileId: string;
  leaveType?: string;
  fiscalYear?: number;
  entitlement?: number;
  used?: number;
  carriedOver?: number;
  available?: number;
}

export async function listLeaveTypes() {
  return getVashandi<{ items?: LeaveType[] }>("/leave/types");
}

export async function createLeaveType(body: Record<string, unknown>) {
  return postVashandi<LeaveType>("/leave/types", body);
}

export async function listLeaveBalances(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: LeaveBalance[] }>("/leave/balances", params);
}

export async function upsertLeaveBalance(body: Record<string, unknown>) {
  return postVashandi<LeaveBalance>("/leave/balances", body);
}

export function leaveTypesFromResponse(response: VashandiActionResponse): LeaveType[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as LeaveType[];
  if (Array.isArray(data)) return data as LeaveType[];
  return [];
}

export function leaveBalancesFromResponse(response: VashandiActionResponse): LeaveBalance[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as LeaveBalance[];
  if (Array.isArray(data)) return data as LeaveBalance[];
  return [];
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
