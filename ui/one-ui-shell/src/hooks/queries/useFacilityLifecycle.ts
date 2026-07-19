/**
 * Facility lifecycle self-service (D-L4/D-L6/D-L8) — registration, verification,
 * trust-dimension transparency, and governance cases. Talks to the BFF
 * `FacilityLifecycleController` (`/api/v1/facility-lifecycle`), which binds every
 * submit to the session Health ID (X-Actor-ID) — the applicant is never taken
 * from the body — and gates registration/verification on a proven personal
 * identity (≥LOA2). Responses use the BFF `{ data, meta }` envelope.
 *
 * Facility CLAIM is a separate journey (see useFacilityClaim).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface Envelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

/** Error shape thrown by apiClient: `{ status, error: { code, message } }`. */
export interface FacilityLifecycleApiError {
  status?: number;
  error?: { code?: string; message?: string };
}

const BASE = "/api/v1/facility-lifecycle";

const qk = {
  trust: (facilityUuid: string) => ["facility-lifecycle", "trust", facilityUuid] as const,
  verification: (facilityUuid: string) => ["facility-lifecycle", "verification", facilityUuid] as const,
  registrationCases: (outcome: string) => ["facility-lifecycle", "reg-cases", outcome] as const,
  governanceCases: (facilityUuid: string, caseType: string) =>
    ["facility-lifecycle", "gov-cases", facilityUuid, caseType] as const,
};

// ── Registration ───────────────────────────────────────────────────────────

export interface FacilityRegistrationInput {
  name: string;
  facilityType?: string;
  province?: string;
  district?: string;
  ownershipCategory?: string;
  latitude?: number;
  longitude?: number;
  evidenceRef?: string;
  notes?: string;
}

export interface FacilityRegistrationResult {
  submitted: boolean;
  caseRef?: string;
  status?: string;
  note?: string;
}

export function useRegisterFacility() {
  return useMutation<FacilityRegistrationResult, FacilityLifecycleApiError, FacilityRegistrationInput>({
    mutationFn: async (input) =>
      (await apiClient.post<Envelope<FacilityRegistrationResult>>(`${BASE}/registrations`, input)).data,
  });
}

// ── Verification ─────────────────────────────────────────────────────────────

export interface FacilityVerificationCase {
  caseRef?: string;
  facilityUuid?: string;
  verificationType?: string;
  status?: string;
  gpsDistanceMeters?: number | null;
  decidedBy?: string | null;
  decidedAt?: string | null;
  createdAt?: string | null;
}

export interface OpenVerificationInput {
  facilityUuid: string;
  verificationType: string;
  evidenceRefs?: string[];
  claimedLatitude?: number;
  claimedLongitude?: number;
  rtcSessionRef?: string;
}

export function useFacilityVerificationCases(facilityUuid: string) {
  return useQuery<FacilityVerificationCase[]>({
    queryKey: qk.verification(facilityUuid),
    queryFn: async () =>
      (
        await apiClient.get<Envelope<{ cases: FacilityVerificationCase[] }>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/verification-cases`,
        )
      ).data.cases ?? [],
    enabled: !!facilityUuid,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

export function useOpenFacilityVerification() {
  const qc = useQueryClient();
  return useMutation<FacilityVerificationCase, FacilityLifecycleApiError, OpenVerificationInput>({
    mutationFn: async ({ facilityUuid, ...body }) =>
      (
        await apiClient.post<Envelope<FacilityVerificationCase>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/verification-cases`,
          body,
        )
      ).data,
    onSuccess: async (_res, input) => {
      await qc.invalidateQueries({ queryKey: qk.verification(input.facilityUuid) });
    },
  });
}

// ── Trust dimensions ─────────────────────────────────────────────────────────

export interface FacilityTrustDimension {
  dimension?: string;
  status?: string;
  evidenceRef?: string | null;
  source?: string | null;
  computedAt?: string | null;
}

export function useFacilityTrustDimensions(facilityUuid: string) {
  return useQuery<FacilityTrustDimension[]>({
    queryKey: qk.trust(facilityUuid),
    queryFn: async () =>
      (
        await apiClient.get<Envelope<{ dimensions: FacilityTrustDimension[] }>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/trust-dimensions`,
        )
      ).data.dimensions ?? [],
    enabled: !!facilityUuid,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}

export function useRecomputeFacilityTrust(facilityUuid: string) {
  const qc = useQueryClient();
  return useMutation<FacilityTrustDimension[], FacilityLifecycleApiError, void>({
    mutationFn: async () =>
      (
        await apiClient.post<Envelope<{ dimensions: FacilityTrustDimension[] }>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/trust-dimensions/recompute`,
        )
      ).data.dimensions ?? [],
    onSuccess: (dims) => {
      qc.setQueryData(qk.trust(facilityUuid), dims);
    },
  });
}

// ── Completeness dimensions + data-gap tasks (V036 / HPA enrichment) ─────────

export interface FacilityCompletenessDim {
  dimension?: string;
  status?: string;
  completeness_pct?: number | null;
}

export interface FacilityDataGapTask {
  id?: number;
  task_type?: string;
  dimension?: string | null;
  required_information?: string;
  why_required?: string | null;
  responsible_actor?: string;
  accepted_evidence?: string | null;
  priority?: string;
  status?: string;
}

export function useFacilityCompletenessDimensions(facilityUuid: string) {
  return useQuery<FacilityCompletenessDim[]>({
    queryKey: ["facility-lifecycle", "completeness", facilityUuid],
    queryFn: async () =>
      (
        await apiClient.get<Envelope<{ dimensions: FacilityCompletenessDim[] }>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/completeness`,
        )
      ).data.dimensions ?? [],
    enabled: !!facilityUuid,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

export function useFacilityDataGaps(facilityUuid: string) {
  return useQuery<FacilityDataGapTask[]>({
    queryKey: ["facility-lifecycle", "data-gaps", facilityUuid],
    queryFn: async () =>
      (
        await apiClient.get<Envelope<{ tasks: FacilityDataGapTask[] }>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/data-gaps`,
        )
      ).data.tasks ?? [],
    enabled: !!facilityUuid,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

export interface ResolveDataGapInput {
  facilityUuid: string;
  taskId: number;
  status: string;
  note?: string;
}

export function useResolveDataGap() {
  const qc = useQueryClient();
  return useMutation<{ id?: string; status?: string }, FacilityLifecycleApiError, ResolveDataGapInput>({
    mutationFn: async ({ facilityUuid, taskId, status, note }) =>
      (
        await apiClient.post<Envelope<{ id?: string; status?: string }>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/data-gaps/${taskId}/resolve`,
          { status, note },
        )
      ).data,
    onSuccess: async (_r, input) => {
      await qc.invalidateQueries({ queryKey: ["facility-lifecycle", "data-gaps", input.facilityUuid] });
      await qc.invalidateQueries({ queryKey: ["facility-lifecycle", "completeness", input.facilityUuid] });
    },
  });
}

// ── Governance ───────────────────────────────────────────────────────────────

export type FacilityGovernanceAction = "high-risk-update" | "transfer" | "duplicate-report";

export interface FacilityGovernanceResult {
  caseRef?: string;
  caseType?: string;
  facilityUuid?: string;
  status?: string;
  createdAt?: string;
}

export interface OpenGovernanceInput {
  facilityUuid: string;
  action: FacilityGovernanceAction;
  payload?: Record<string, unknown>;
}

export function useOpenFacilityGovernanceCase() {
  return useMutation<FacilityGovernanceResult, FacilityLifecycleApiError, OpenGovernanceInput>({
    mutationFn: async ({ facilityUuid, action, payload }) =>
      (
        await apiClient.post<Envelope<FacilityGovernanceResult>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/governance/${action}`,
          payload ?? {},
        )
      ).data,
  });
}

// ── Steward review (queue reads + decisions) ─────────────────────────────────

export interface FacilityRegistrationCase {
  caseRef?: string;
  submittedName?: string;
  applicantHealthId?: string;
  outcome?: string;
  matchedFacilityUuid?: string;
  provisionalFacilityUuid?: string;
  createdAt?: string;
}

/** Steward: facility-registration review queue by outcome (steward-gated upstream). */
export function useFacilityRegistrationCases(outcome: string, enabled = true) {
  return useQuery<FacilityRegistrationCase[]>({
    queryKey: qk.registrationCases(outcome),
    queryFn: async () =>
      (
        await apiClient.get<Envelope<{ cases: FacilityRegistrationCase[] }>>(
          `${BASE}/registrations?outcome=${encodeURIComponent(outcome)}`,
        )
      ).data.cases ?? [],
    enabled,
    staleTime: 15_000,
    refetchOnWindowFocus: false,
  });
}

export interface FacilityGovernanceCase {
  caseRef?: string;
  caseType?: string;
  facilityUuid?: string;
  status?: string;
  createdAt?: string;
}

/** Steward: governance cases for a facility by type (steward-gated upstream). */
export function useFacilityGovernanceCases(facilityUuid: string, caseType: string, enabled = true) {
  return useQuery<FacilityGovernanceCase[]>({
    queryKey: qk.governanceCases(facilityUuid, caseType),
    queryFn: async () =>
      (
        await apiClient.get<Envelope<{ cases: FacilityGovernanceCase[] }>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/governance/cases?caseType=${encodeURIComponent(caseType)}`,
        )
      ).data.cases ?? [],
    enabled: !!facilityUuid && enabled,
    staleTime: 15_000,
    refetchOnWindowFocus: false,
  });
}

export interface DecisionInput {
  facilityUuid: string;
  caseId: string;
  decision: string;
  note?: string;
}

/** Steward: record a verification-case decision. */
export function useDecideFacilityVerification() {
  const qc = useQueryClient();
  return useMutation<FacilityVerificationCase, FacilityLifecycleApiError, DecisionInput>({
    mutationFn: async ({ facilityUuid, caseId, decision, note }) =>
      (
        await apiClient.post<Envelope<FacilityVerificationCase>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/verification-cases/${encodeURIComponent(caseId)}/decision`,
          { decision, note },
        )
      ).data,
    onSuccess: async (_res, input) => {
      await qc.invalidateQueries({ queryKey: qk.verification(input.facilityUuid) });
    },
  });
}

/** Steward: record a governance-case decision. */
export function useDecideFacilityGovernance() {
  const qc = useQueryClient();
  return useMutation<FacilityGovernanceCase, FacilityLifecycleApiError, DecisionInput>({
    mutationFn: async ({ facilityUuid, caseId, decision, note }) =>
      (
        await apiClient.post<Envelope<FacilityGovernanceCase>>(
          `${BASE}/${encodeURIComponent(facilityUuid)}/governance/cases/${encodeURIComponent(caseId)}/decision`,
          { decision, note },
        )
      ).data,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["facility-lifecycle", "gov-cases"] });
    },
  });
}

/** Human-readable message from a BFF error (honest copy travels in error.message). */
export function facilityLifecycleErrorMessage(err: unknown, fallback: string): string {
  const e = err as FacilityLifecycleApiError | undefined;
  return e?.error?.message || fallback;
}
