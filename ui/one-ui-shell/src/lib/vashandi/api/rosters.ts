import { getVashandi, patchVashandi, postVashandi } from "./client";
import type { Roster, Shift, VashandiActionResponse } from "../types";

export async function listRosters(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: Roster[] }>("/rosters", params);
}

export async function getRoster(id: string) {
  return getVashandi<Roster>(`/rosters/${encodeURIComponent(id)}`);
}

export async function createRoster(body: Record<string, unknown>) {
  return postVashandi<Roster>("/rosters", body);
}

export async function approveRoster(id: string, body?: Record<string, unknown>) {
  return postVashandi(`/rosters/${encodeURIComponent(id)}/approve`, body);
}

export async function createShift(body: Record<string, unknown>) {
  return postVashandi<Shift>("/shifts", body);
}

export async function updateShift(id: string, body: Record<string, unknown>) {
  return patchVashandi<Shift>(`/shifts/${encodeURIComponent(id)}`, body);
}

export function rostersFromResponse(response: VashandiActionResponse): Roster[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as Roster[];
  if (Array.isArray(record.rosters)) return record.rosters as Roster[];
  if (Array.isArray(data)) return data as Roster[];
  return [];
}

export function shiftsFromResponse(response: VashandiActionResponse): Shift[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.shifts)) return record.shifts as Shift[];
  if (Array.isArray(record.items)) return record.items as Shift[];
  return [];
}
