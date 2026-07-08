/**
 * Facility administrator self-service claim journey (IATG Wave 2, WS-E) — Health OS §5–§6.
 *
 * Talks to the BFF `FacilityClaimController` (`/api/v1/facility-claim`), which binds every claim to
 * the signed-in person's Health ID (X-Actor-ID) — the subject key is the canonical facility UUID,
 * NOT a Health ID. Responses use the BFF `{ data, meta }` envelope; hooks unwrap `data`.
 *
 * Doctrine: masked at the edge (facility codes / evidence refs return masked). All three lanes are
 * live — appointment-letter, supporting-document upload, and organisation-invitation accept — and
 * any downstream verdict is surfaced verbatim, never faked.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface Envelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

export interface FacilityClaimPath {
  path: "APPOINTMENT_LETTER" | "DOCUMENT" | "ORG_INVITATION" | string;
  available: boolean;
}

export interface FacilityClaimEligibility {
  facilityUuid: string;
  allowedOnPlatform: boolean;
  claimable: boolean;
  alreadyAdministered: boolean;
  activeAdministratorCount: number;
  paths?: FacilityClaimPath[];
}

export interface FacilityClaimPreview {
  facilityUuid: string;
  /** Masked (first4***) — the BFF never returns the raw facility code here. */
  facilityCode?: string;
  name?: string;
  facilityType?: string;
  province?: string;
  district?: string;
  allowedOnPlatform: boolean;
  claimable: boolean;
}

export interface FacilityAppointResult {
  submitted: boolean;
  appointmentId?: string;
  facilityUuid?: string;
  /** Masked Health ID of the appointee (never raw). */
  personHealthId?: string;
  role?: string;
  approvalState?: string;
  note?: string;
}

export interface FacilityApproveResult {
  appointmentId?: string;
  facilityUuid?: string;
  personHealthId?: string;
  approvalState?: string;
  affiliationRecorded?: boolean;
  affiliationNote?: string;
}

export interface FacilityAppointInput {
  facilityUuid: string;
  consent: true;
  role?: string;
  evidenceRef?: string;
}

export interface FacilityApproveInput {
  appointmentId: string;
  organizationId?: string;
}

export interface FacilityEvidenceUploadResult {
  /** Opaque document object id, supplied as the claim evidence reference on appoint. */
  evidenceRef?: string;
  filename?: string;
  mimeType?: string;
  scanStatus?: string;
  note?: string;
}

export interface OrgInvitationAcceptResult {
  accepted: boolean;
  invitationId?: string;
  organizationId?: string;
  role?: string;
  affiliationId?: string;
  status?: string;
  note?: string;
}

/** Error shape thrown by apiClient: `{ status, error: { code, message } }`. */
export interface FacilityClaimApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

const BASE = "/api/v1/facility-claim";

const qk = {
  eligibility: (facilityUuid: string) => ["facility-claim", "eligibility", facilityUuid] as const,
};

export function useFacilityClaimEligibility(facilityUuid: string) {
  return useQuery<FacilityClaimEligibility>({
    queryKey: qk.eligibility(facilityUuid),
    queryFn: async () =>
      (
        await apiClient.get<Envelope<FacilityClaimEligibility>>(
          `${BASE}/eligibility?facilityUuid=${encodeURIComponent(facilityUuid)}`,
        )
      ).data,
    enabled: !!facilityUuid,
    staleTime: 60 * 1000,
    refetchOnWindowFocus: false,
  });
}

/** Masked facility summary shown before an administrator commits to a claim (confirm-before-commit). */
export function usePreviewFacilityClaim() {
  return useMutation<FacilityClaimPreview, FacilityClaimApiError, string>({
    mutationFn: async (facilityUuid: string) =>
      (await apiClient.post<Envelope<FacilityClaimPreview>>(`${BASE}/preview`, { facilityUuid })).data,
  });
}

/** Submit a facility-administrator claim onto the session Health ID (explicit consent required). */
export function useAppointFacilityClaim() {
  const qc = useQueryClient();
  return useMutation<FacilityAppointResult, FacilityClaimApiError, FacilityAppointInput>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<FacilityAppointResult>>(`${BASE}/appoint`, input)).data,
    onSuccess: async (_res, input) => {
      await qc.invalidateQueries({ queryKey: qk.eligibility(input.facilityUuid) });
    },
  });
}

/** Approve a PENDING appointment → ACTIVE (records the administering affiliation when known). */
export function useApproveFacilityAppointment() {
  const qc = useQueryClient();
  return useMutation<FacilityApproveResult, FacilityClaimApiError, FacilityApproveInput>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<FacilityApproveResult>>(`${BASE}/approve`, input)).data,
    onSuccess: async (res) => {
      if (res.facilityUuid) {
        await qc.invalidateQueries({ queryKey: qk.eligibility(res.facilityUuid) });
      }
    },
  });
}

/**
 * Upload a supporting document as facility-claim evidence. Returns an opaque evidence reference
 * (document object id) to attach to the appoint call. The document is stored in the document service.
 */
export function useUploadFacilityEvidence() {
  return useMutation<FacilityEvidenceUploadResult, FacilityClaimApiError, File>({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append("file", file);
      const env = await apiClient.postForm<Envelope<FacilityEvidenceUploadResult>>(`${BASE}/evidence`, form);
      return env.data;
    },
  });
}

/**
 * Accept an organisation invitation to administer a facility by its single-use token.
 * The accepting person is always the session Health ID (set server-side, never from the client).
 */
export function useAcceptOrgInvitation() {
  const qc = useQueryClient();
  return useMutation<OrgInvitationAcceptResult, FacilityClaimApiError, string>({
    mutationFn: async (token: string) =>
      (await apiClient.post<Envelope<OrgInvitationAcceptResult>>(`${BASE}/org-invitation`, { token })).data,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["facility-claim"] });
    },
  });
}

/** Human-readable message from a BFF error (honest copy travels in error.message). */
export function facilityClaimErrorMessage(err: unknown, fallback: string): string {
  const e = err as FacilityClaimApiError | undefined;
  return e?.error?.message || fallback;
}

/** True when the BFF answered 501 FEATURE_PENDING for an optional sub-capability (never fake success). */
export function isFeaturePending(err: unknown): boolean {
  const e = err as FacilityClaimApiError | undefined;
  return e?.status === 501 || e?.error?.code === "FEATURE_PENDING";
}
