/**
 * Base Vashandi BFF client — /internal/v1/vashandi/**
 */

import { apiClient } from "@/lib/api-client";
import type { ApiClientErrorLike, VashandiActionResponse } from "../types";

export const VASHANDI_BASE_PATH = "/internal/v1/vashandi";

function isUnavailableError(error: unknown): boolean {
  if (!error || typeof error !== "object") return false;
  const e = error as ApiClientErrorLike;
  const code = e.error?.code ?? "";
  const status = e.status ?? e.statusCode;
  return status === 404 || status === 502 || status === 503 || code === "SERVICE_UNAVAILABLE";
}

export function upstreamUnavailableResponse<TData = unknown>(
  friendlyTitle: string,
  friendlyMessage: string,
): VashandiActionResponse<TData> {
  return {
    success: false,
    status: "upstream_unavailable",
    friendlyTitle,
    friendlyMessage,
    blockedReason: "upstream_unavailable",
    integrationStatus: "UPSTREAM_UNAVAILABLE",
    integrationMessage: friendlyMessage,
    policyDecision: { allowed: false },
  };
}

export async function getVashandi<TData>(
  path: string,
  params?: Record<string, string | undefined>,
  unavailableMessage = "Vashandi workforce service is not currently available.",
): Promise<VashandiActionResponse<TData>> {
  try {
    const query = new URLSearchParams();
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value) query.set(key, value);
      });
    }
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return await apiClient.get<VashandiActionResponse<TData>>(`${VASHANDI_BASE_PATH}${path}${suffix}`);
  } catch (error) {
    if (isUnavailableError(error)) {
      return upstreamUnavailableResponse("Service unavailable", unavailableMessage);
    }
    throw error;
  }
}

export async function postVashandi<TData>(
  path: string,
  body?: unknown,
  unavailableMessage = "Vashandi workforce service is not currently available.",
): Promise<VashandiActionResponse<TData>> {
  try {
    return await apiClient.post<VashandiActionResponse<TData>>(`${VASHANDI_BASE_PATH}${path}`, body ?? {});
  } catch (error) {
    if (isUnavailableError(error)) {
      return upstreamUnavailableResponse("Service unavailable", unavailableMessage);
    }
    throw error;
  }
}

export async function patchVashandi<TData>(
  path: string,
  body: unknown,
  unavailableMessage = "Vashandi workforce service is not currently available.",
): Promise<VashandiActionResponse<TData>> {
  try {
    return await apiClient.patch<VashandiActionResponse<TData>>(`${VASHANDI_BASE_PATH}${path}`, body);
  } catch (error) {
    if (isUnavailableError(error)) {
      return upstreamUnavailableResponse("Service unavailable", unavailableMessage);
    }
    throw error;
  }
}

export function isVashandiActionResponse(value: unknown): value is VashandiActionResponse {
  return typeof value === "object" && value !== null && "status" in value && "success" in value;
}

export function isUpstreamUnavailable(value: VashandiActionResponse | unknown): boolean {
  return isVashandiActionResponse(value) && value.status === "upstream_unavailable";
}

export function isDenied(value: VashandiActionResponse | unknown): boolean {
  return isVashandiActionResponse(value) && (value.status === "denied" || value.success === false && value.blockedReason === "denied");
}

export function extractListItems<T>(response: VashandiActionResponse): T[] {
  const data = response.data;
  if (!data || typeof data !== "object") return [];
  const record = data as Record<string, unknown>;
  if (Array.isArray(record.items)) return record.items as T[];
  if (Array.isArray(record.content)) return record.content as T[];
  if (Array.isArray(data)) return data as T[];
  return [];
}

export function extractSingleItem<T>(response: VashandiActionResponse): T | undefined {
  const data = response.data;
  if (!data || typeof data !== "object") return undefined;
  const record = data as Record<string, unknown>;
  if (record.profile) return record.profile as T;
  if (record.assignment) return record.assignment as T;
  if (record.roster) return record.roster as T;
  if (record.shift) return record.shift as T;
  if (record.event) return record.event as T;
  if (record.leave) return record.leave as T;
  if (record.risk) return record.risk as T;
  return data as T;
}
