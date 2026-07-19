/**
 * Indawo SITE self-service (SJ1 operator grants, SJ2 registration) — D-L4.
 *
 * Talks to the BFF `SiteSelfServiceController` (`/api/v1/site-self-service`),
 * which binds every submit to the session Health ID (X-Actor-ID) — the applicant
 * is never taken from the body — and gates submits on a proven personal identity
 * (≥LOA2). This is DISTINCT from the regulatory `/public-health/site-registry`
 * licensing/inspection application rail. Responses use the `{ data, meta }`
 * envelope.
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface Envelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

/** Error shape thrown by apiClient: `{ status, error: { code, message } }`. */
export interface SiteSelfServiceApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

const BASE = "/api/v1/site-self-service";

export const SITE_OPERATOR_ROLES = [
  "SITE_VIEWER",
  "SITE_OPERATOR",
  "SITE_ADMINISTRATOR",
  "REGULATORY_LIAISON",
] as const;

export type SiteOperatorRole = (typeof SITE_OPERATOR_ROLES)[number];

// ── Site registration (SJ2) ─────────────────────────────────────────────────

export interface SiteRegistrationInput {
  name: string;
  type: string;
  siteCategory?: string;
  province?: string;
  district?: string;
  siteCode?: string;
  evidenceRef: string;
  notes?: string;
}

export interface SiteRegistrationResult {
  submitted: boolean;
  caseRef?: string;
  status?: string;
  note?: string;
}

export function useRegisterSite() {
  return useMutation<SiteRegistrationResult, SiteSelfServiceApiError, SiteRegistrationInput>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<SiteRegistrationResult>>(`${BASE}/registrations`, input)).data,
  });
}

// ── Operator grants (SJ1) ────────────────────────────────────────────────────

export interface OperatorGrantInput {
  siteId: string;
  role: SiteOperatorRole;
  claimType?: "NEW" | "RECOVERY";
  evidenceRef: string;
  validFrom?: string;
  validTo?: string;
  notes?: string;
}

export interface OperatorGrantResult {
  submitted: boolean;
  grantId?: string;
  siteId?: string;
  role?: string;
  approvalState?: string;
  note?: string;
}

export function useRequestOperatorGrant() {
  return useMutation<OperatorGrantResult, SiteSelfServiceApiError, OperatorGrantInput>({
    mutationFn: async ({ siteId, ...body }) =>
      (
        await apiClient.post<Envelope<OperatorGrantResult>>(
          `${BASE}/${encodeURIComponent(siteId)}/operator-grants`,
          body,
        )
      ).data,
  });
}

// ── Steward queues (optional; steward-gated upstream) ────────────────────────

export interface OperatorGrantRow {
  id?: string;
  siteId?: string;
  role?: string;
  approvalState?: string;
  claimType?: string;
}

export function useOperatorGrantQueue(state: string, enabled = false) {
  return useQuery<OperatorGrantRow[]>({
    queryKey: ["site-self-service", "grants", state],
    queryFn: async () =>
      (await apiClient.get<Envelope<{ grants: OperatorGrantRow[] }>>(`${BASE}/operator-grants?state=${state}`))
        .data.grants ?? [],
    enabled,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

/** Human-readable message from a BFF error (honest copy travels in error.message). */
export function siteSelfServiceErrorMessage(err: unknown, fallback: string): string {
  const e = err as SiteSelfServiceApiError | undefined;
  return e?.error?.message || fallback;
}
