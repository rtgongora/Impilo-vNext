/**
 * Clinical Records Service — citizen's own immunizations, care plans, and referrals.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/{immunizations,care-plans,referrals}).
 * The patient identity is the logged-in citizen (resolved server-side from the trust context),
 * so these take no patientId — they return the authenticated citizen's records.
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { Immunization, CarePlan, Referral } from "../types";

const BASE = "/internal/v1/mobile/citizen";

/** Unwrap the BFF `{ data: <list|paged> }` envelope into a plain array, tolerant of shapes. */
function unwrap<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === "object") {
    const d = data as Record<string, unknown>;
    for (const key of ["items", "content", "data"]) {
      if (Array.isArray(d[key])) return d[key] as T[];
    }
  }
  return [];
}

async function fetchList<T>(path: string, params: { page?: number; size?: number } = {}): Promise<{ items: T[] }> {
  const query: string[] = [];
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<{ data: unknown }>(`${BASE}${path}${qs}`);
  return { items: unwrap<T>(response.data?.data) };
}

export function fetchImmunizations(params: { page?: number; size?: number } = {}): Promise<{ items: Immunization[] }> {
  return fetchList<Immunization>("/immunizations", params);
}

export function fetchCarePlans(): Promise<{ items: CarePlan[] }> {
  return fetchList<CarePlan>("/care-plans");
}

export function fetchReferrals(params: { page?: number; size?: number } = {}): Promise<{ items: Referral[] }> {
  return fetchList<Referral>("/referrals", params);
}
