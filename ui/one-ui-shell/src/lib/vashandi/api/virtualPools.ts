import { getVashandi } from "./client";
import type { VashandiActionResponse, VirtualPool, VirtualPoolOnDuty, Shift } from "../types";

/** All virtual (on-call) pools with rostered coverage, tenant-scoped. */
export async function listVirtualPools(at?: string) {
  return getVashandi<VirtualPool[]>("/virtual-pools", at ? { at } : undefined);
}

/** Who is on duty for a pool right now (confirmed/checked_in, window covers `at`). */
export async function getPoolOnDuty(poolId: string, at?: string) {
  return getVashandi<VirtualPoolOnDuty>(
    `/virtual-pools/${encodeURIComponent(poolId)}/on-duty`,
    at ? { at } : undefined,
  );
}

/** Every shift rostered against a pool, any status (management read). */
export async function getPoolShifts(poolId: string) {
  return getVashandi<{ poolId: string; shiftCount: number; shifts: Shift[] }>(
    `/virtual-pools/${encodeURIComponent(poolId)}/shifts`,
  );
}

export function poolsFromResponse(response: VashandiActionResponse): VirtualPool[] {
  const data = response.data;
  if (Array.isArray(data)) return data as VirtualPool[];
  if (data && typeof data === "object" && Array.isArray((data as Record<string, unknown>).items)) {
    return (data as Record<string, unknown>).items as VirtualPool[];
  }
  return [];
}

export function poolShiftsFromResponse(response: VashandiActionResponse): Shift[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.shifts)) return record.shifts as Shift[];
  if (Array.isArray(record.items)) return record.items as Shift[];
  return [];
}

export function onDutyFromResponse(response: VashandiActionResponse): VirtualPoolOnDuty | undefined {
  const data = response.data;
  if (!data || typeof data !== "object" || Array.isArray(data)) return undefined;
  return data as VirtualPoolOnDuty;
}
