/**
 * Durable self-service provider-access requests (IATG Journey D).
 *
 * Talks to the BFF `ProviderClaimController` self-service endpoints:
 *   POST /api/v1/provider-claim/access-request   → submit (durable)
 *   GET  /api/v1/provider-claim/status           → the applicant's requests
 *   GET  /api/v1/provider-claim/status/{publicId} → one request
 *
 * Every submission persists a record with a status and a named next actor — no
 * data entry goes nowhere. The person anchor is the session Health ID (server
 * side, from X-Actor-ID); it is never sent in the body.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

interface Envelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

export type ProviderAccessRequestType =
  | "NEW_PROVIDER"
  | "ORG_INVITATION"
  | "COUNCIL_NUMBER"
  | "EC_NUMBER"
  | "HAVE_PROVIDER_ID"
  | "RECOVER";

export interface ProviderAccessRequestView {
  publicId: string;
  requestType: string;
  status: string;
  nextActor?: string | null;
  profession?: string | null;
  councilCode?: string | null;
  councilNumberMasked?: string | null;
  ecNumberMasked?: string | null;
  organizationRef?: string | null;
  evidenceSummary?: string | null;
  reason?: string | null;
  providerPublicId?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface SubmitProviderAccessRequestInput {
  requestType: ProviderAccessRequestType;
  profession?: string;
  councilCode?: string;
  councilNumber?: string;
  ecNumber?: string;
  organizationRef?: string;
  evidenceSummary?: string;
}

export interface ProviderAccessApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

const BASE = "/api/v1/provider-claim";

const qk = {
  status: () => ["provider-access-request", "status"] as const,
  one: (publicId: string) => ["provider-access-request", "one", publicId] as const,
};

export async function submitProviderAccessRequest(input: SubmitProviderAccessRequestInput) {
  return (await apiClient.post<Envelope<ProviderAccessRequestView>>(`${BASE}/access-request`, input)).data;
}

export async function fetchProviderAccessRequests() {
  return (await apiClient.get<Envelope<ProviderAccessRequestView[]>>(`${BASE}/status`)).data;
}

export async function fetchProviderAccessRequest(publicId: string) {
  return (await apiClient.get<Envelope<ProviderAccessRequestView>>(`${BASE}/status/${publicId}`)).data;
}

/** Submit a durable provider-access request. */
export function useSubmitProviderAccessRequest() {
  const qc = useQueryClient();
  return useMutation<ProviderAccessRequestView, ProviderAccessApiError, SubmitProviderAccessRequestInput>({
    mutationFn: submitProviderAccessRequest,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: qk.status() });
    },
  });
}

/** The signed-in applicant's provider-access requests (check status). */
export function useProviderAccessRequests() {
  const { user, isAuthenticated } = useAuthStore();
  return useQuery<ProviderAccessRequestView[]>({
    queryKey: qk.status(),
    queryFn: fetchProviderAccessRequests,
    enabled: isAuthenticated && !!user?.id,
    refetchOnWindowFocus: false,
  });
}

/** A single provider-access request by opaque public id. */
export function useProviderAccessRequest(publicId: string | undefined) {
  return useQuery<ProviderAccessRequestView>({
    queryKey: qk.one(publicId ?? ""),
    queryFn: () => fetchProviderAccessRequest(publicId as string),
    enabled: !!publicId,
  });
}

export function providerAccessErrorMessage(err: unknown, fallback: string): string {
  const e = err as ProviderAccessApiError | undefined;
  return e?.error?.message || fallback;
}
