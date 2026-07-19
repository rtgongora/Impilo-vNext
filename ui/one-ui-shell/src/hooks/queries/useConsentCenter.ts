/**
 * Experience UI — Citizen Consent Center (CJ13).
 *
 * One surface for the two questions a person asks about their record:
 *   - "Who did I allow?"      → consent directives (tshepo-consent)
 *   - "Who actually looked?"  → record access-log  (tshepo-audit)
 *
 * Backed by the composed BFF endpoint /internal/v1/citizen/consent-center, which
 * resolves the subject server-side from the authenticated actor (the citizen never
 * supplies a patient id) and returns the house {data, meta} envelope.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ConsentDirective {
  id: string;
  status: string;
  scope?: string;
  purpose?: string;
  provision?: string;
  granteeRef?: string;
  grantorRef?: string;
  periodStart?: string;
  periodEnd?: string;
  createdAt?: string;
  revokedAt?: string;
  revocationReason?: string;
}

export interface AccessHistoryEntry {
  eventType?: string;
  actorType?: string;
  action?: string;
  outcome?: string;
  purposeOfUse?: string;
  timestamp?: string;
}

/** Upstream sends a PagedResponse ({items}); tolerate a bare array too. */
type Paged<T> = { items?: T[] } | T[] | null;

export interface ConsentCenterData {
  consents?: Paged<ConsentDirective>;
  accessLog?: Paged<AccessHistoryEntry>;
}

/** Normalise a PagedResponse-or-array-or-null into a plain array. */
export function itemsOf<T>(paged: Paged<T> | undefined): T[] {
  if (!paged) return [];
  if (Array.isArray(paged)) return paged;
  return paged.items ?? [];
}

export function useConsentCenter() {
  return useQuery({
    queryKey: ["consent-center"],
    queryFn: () =>
      apiClient.get<ApiResponse<ConsentCenterData>>("/internal/v1/citizen/consent-center"),
  });
}

export function useRevokeConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (consentId: string) =>
      apiClient.post<ApiResponse<{ status: string }>>(
        `/internal/v1/citizen/consent-center/revoke/${consentId}`,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["consent-center"] }),
  });
}
