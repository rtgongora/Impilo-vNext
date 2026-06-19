import { getVashandi, postVashandi } from "./client";
import type { AccessRisk, VashandiActionResponse } from "../types";

export async function listAccessRisks(params?: Record<string, string | undefined>) {
  return getVashandi<{ items?: AccessRisk[] }>("/access-risks", params);
}

export async function scanAccessRisks(body?: Record<string, unknown>) {
  return postVashandi("/access-risks/scan", body);
}

export async function resolveAccessRisk(id: string, body?: Record<string, unknown>) {
  return postVashandi(`/access-risks/${encodeURIComponent(id)}/resolve`, body);
}

export async function revokeAccessForRisk(id: string, body?: Record<string, unknown>) {
  return postVashandi(`/access-risks/${encodeURIComponent(id)}/revoke-access`, body);
}

export function accessRisksFromResponse(response: VashandiActionResponse): AccessRisk[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.risks)) return record.risks as AccessRisk[];
  if (Array.isArray(record.items)) return record.items as AccessRisk[];
  if (Array.isArray(data)) return data as AccessRisk[];
  return [];
}
