/**
 * Provider self-service claim / recovery journey (IATG WS-F) — Health OS §5–§6.
 *
 * Talks to the BFF `ProviderClaimController` (`/api/v1/provider-claim`), which
 * binds every downstream call to the signed-in person's Health ID. Responses
 * use the BFF `{ data, meta }` envelope; hooks unwrap `data`.
 *
 * Doctrine: recover-not-reissue (recovery surfaces the SAME masked Provider ID).
 * Every evidence lane binds a real endpoint — council-number, EC-number match,
 * document upload, and organisation invitation — and any downstream verdict is
 * surfaced verbatim, never faked.
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
  /**
   * Whether this practitioner has agreed to receive appointment and prescription requests.
   * Distinct from `alreadyLinked`: registration and linkage make someone findable and
   * verifiable, participation is the separate, revocable yes that booking gates on.
   */
  participating?: boolean;
  lifecycleStatus?: string;
  /** False for a profile that has not been claimed yet — it must be claimed before it can participate. */
  canSetParticipation?: boolean;
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

/** Submit identity evidence (council number / EC number). */
export function useSubmitClaimEvidence() {
  return useMutation<ProviderEvidenceResult, ProviderClaimApiError, ProviderEvidenceInput>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<ProviderEvidenceResult>>(`${BASE}/evidence`, input)).data,
  });
}

export interface ProviderDocumentEvidenceResult {
  /** Opaque public id of the durable DOCUMENT_EVIDENCE access request opened for review. */
  publicId?: string;
  requestType?: string;
  status?: string;
  nextActor?: string;
  nextStep?: string;
}

/**
 * Upload a supporting document as provider-claim evidence. The document is stored in the document
 * service and a durable DOCUMENT_EVIDENCE access request is opened for registry review.
 */
export function useUploadProviderDocument() {
  const qc = useQueryClient();
  return useMutation<ProviderDocumentEvidenceResult, ProviderClaimApiError, File>({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append("file", file);
      const env = await apiClient.postForm<Envelope<ProviderDocumentEvidenceResult>>(
        `${BASE}/evidence/document`,
        form,
      );
      return env.data;
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["provider-claim"] });
    },
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
/**
 * Turn participation on or off for the signed-in practitioner's own profile.
 *
 * Whose participation changes is decided server-side from the trust headers; the body carries
 * only the choice. Invalidates eligibility so the surface re-reads the new standing rather than
 * assuming the write succeeded.
 */
export function useSetProviderParticipation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (participating: boolean) =>
      (await apiClient.post<Envelope<{ participating: boolean; lifecycleStatus?: string }>>(
        `${BASE}/participation`,
        { participating },
      )).data,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: qk.eligibility() });
    },
  });
}

export function providerClaimErrorMessage(err: unknown, fallback: string): string {
  const e = err as ProviderClaimApiError | undefined;
  return e?.error?.message || fallback;
}

/** True when the BFF answered 501 FEATURE_PENDING for an optional sub-capability (never fake success). */
export function isFeaturePending(err: unknown): boolean {
  const e = err as ProviderClaimApiError | undefined;
  return e?.status === 501 || e?.error?.code === "FEATURE_PENDING";
}
