/**
 * Provider self-service claim / recovery journey (IATG WS-F) — Health OS §5–§6.
 *
 * Talks to the BFF `ProviderClaimController` (`/api/v1/provider-claim`), which
 * binds every downstream call to the signed-in person's Health ID. Responses
 * use the BFF `{ data, meta }` envelope; hooks unwrap `data`.
 *
 * Doctrine: recover-not-reissue (recovery surfaces the SAME masked Provider ID),
 * and honest pending states — 501 FEATURE_PENDING errors are surfaced as-is so
 * the journey can render "coming soon", never fake success.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

interface Envelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

export interface ProviderClaimPath {
  path: "CLAIM_TOKEN" | "COUNCIL_NUMBER" | "EC_NUMBER" | "RECOVER" | string;
  available: boolean;
}

export interface ProviderClaimEligibility {
  alreadyLinked: boolean;
  eligibleForClaim: boolean;
  /** Masked (first4***last2) — the BFF never returns the raw identifier here. */
  providerPublicId?: string;
  paths?: ProviderClaimPath[];
}

export interface ProviderClaimPreview {
  providerPublicId?: string;
  givenNameInitial?: string;
  familyName?: string;
  profession?: string;
  lifecycleStatus?: string;
}

export interface ProviderClaimResult {
  linked: boolean;
  providerPublicId?: string;
  lifecycleStatus?: string;
}

export interface ProviderEvidenceInput {
  type: "COUNCIL_NUMBER" | "EC_NUMBER" | "DOCUMENT";
  value: string;
  councilCode?: string;
}

export interface ProviderEvidenceResult {
  status?: string;
  evidenceRef?: string;
  providerPublicId?: string;
  matchStatus?: string;
  nextStep?: string;
}

export interface ProviderRecoveryInput {
  evidence: { type: string; value?: string; source?: string; confidence?: number };
}

export interface ProviderRecoveryResult {
  recovered: boolean;
  /** SAME masked Provider ID the profile always had — never a new one. */
  providerPublicId?: string;
  lifecycleStatus?: string;
  note?: string;
}

/** Error shape thrown by apiClient: `{ status, error: { code, message } }`. */
export interface ProviderClaimApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

const BASE = "/api/v1/provider-claim";

const qk = {
  eligibility: () => ["provider-claim", "eligibility"] as const,
};

export function useProviderClaimEligibility() {
  const { user, isAuthenticated } = useAuthStore();
  return useQuery<ProviderClaimEligibility>({
    queryKey: qk.eligibility(),
    queryFn: async () =>
      (await apiClient.get<Envelope<ProviderClaimEligibility>>(`${BASE}/eligibility`)).data,
    enabled: isAuthenticated && !!user?.id,
    staleTime: 60 * 1000,
    refetchOnWindowFocus: false,
  });
}

/** Masked preview of what a claim token would claim (confirm-before-commit). */
export function usePreviewClaimToken() {
  return useMutation<ProviderClaimPreview, ProviderClaimApiError, string>({
    mutationFn: async (claimToken: string) =>
      (await apiClient.post<Envelope<ProviderClaimPreview>>(`${BASE}/preview`, { claimToken })).data,
  });
}

/** Redeem a claim token onto the session Health ID (explicit consent required). */
export function useClaimProviderProfile() {
  const qc = useQueryClient();
  return useMutation<ProviderClaimResult, ProviderClaimApiError, { claimToken: string; consent: true }>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<ProviderClaimResult>>(`${BASE}/claim`, input)).data,
    onSuccess: async () => {
      // The person's linked-ID set changed: refresh the activation rail + eligibility.
      await qc.invalidateQueries({ queryKey: ["linked-ids"] });
      await qc.invalidateQueries({ queryKey: qk.eligibility() });
    },
  });
}

/** Submit identity evidence (council number / EC number / document). */
export function useSubmitClaimEvidence() {
  return useMutation<ProviderEvidenceResult, ProviderClaimApiError, ProviderEvidenceInput>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<ProviderEvidenceResult>>(`${BASE}/evidence`, input)).data,
  });
}

/** Recover an existing provider profile — recover-not-reissue. */
export function useRecoverProviderProfile() {
  const qc = useQueryClient();
  return useMutation<ProviderRecoveryResult, ProviderClaimApiError, ProviderRecoveryInput>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<ProviderRecoveryResult>>(`${BASE}/recover`, input)).data,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["linked-ids"] });
      await qc.invalidateQueries({ queryKey: qk.eligibility() });
    },
  });
}

/** Human-readable message from a BFF error (honest copy travels in error.message). */
export function providerClaimErrorMessage(err: unknown, fallback: string): string {
  const e = err as ProviderClaimApiError | undefined;
  return e?.error?.message || fallback;
}

/** True when the BFF answered 501 FEATURE_PENDING (render "coming soon", never fake success). */
export function isFeaturePending(err: unknown): boolean {
  const e = err as ProviderClaimApiError | undefined;
  return e?.status === 501 || e?.error?.code === "FEATURE_PENDING";
}
