/**
 * mental-health-service, through the BFF at /internal/v1/mental-health/**.
 *
 * W13 shipped referral intake and acceptance. Everything below acceptance — the assessment, the
 * risk formulation that concludes it, the safety plan, the involuntary episode, restraint events
 * and their review, admission requests and follow-up — was built and contracted with no caller.
 * These hooks are that caller.
 *
 * **No query here substitutes `[]` for a failure.** Every list returns whatever the BFF sent and
 * lets `isError` stand, because on this surface the difference matters more than most: "no
 * restraint events recorded" and "we could not read the restraint events" are the same picture to a
 * clinician who is shown an empty list, and only one of them means the patient was not restrained.
 *
 * mental-health-service has no built image and no deployment in the preview estate, so today these
 * reads fail rather than return data. The pages built on them say which it is.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/mental-health";

function unwrap<T>(res: unknown): T {
  const body = res as { data?: T } | T;
  return body && typeof body === "object" && "data" in (body as object)
    ? (body as { data: T }).data
    : (body as T);
}

// ── Vocabularies, mirrored from mental-health-service's V001 CHECK constraints ────────────────
// Labels for a picker. The service decides what it will accept; these exist so a clinician is
// offered the values it accepts rather than discovering them from a rejection.

export const ASSESSMENT_TYPES = ["INITIAL", "REVIEW", "DISCHARGE"] as const;
export const CAPACITY_OUTCOMES = ["HAS_CAPACITY", "LACKS_CAPACITY", "FLUCTUATING"] as const;
export const RISK_LEVELS = ["LOW", "MODERATE", "HIGH"] as const;
export const FOLLOWUP_TYPES = ["CLINIC_REVIEW", "HOME_VISIT", "PHONE_CHECK", "COMMUNITY_TEAM"] as const;
export const RESTRAINT_TYPES = ["PHYSICAL", "MECHANICAL", "CHEMICAL", "SECLUSION"] as const;

export type RiskLevel = (typeof RISK_LEVELS)[number];
export type RestraintType = (typeof RESTRAINT_TYPES)[number];

// ── Row shapes (snake_case, as the service serves them) ───────────────────────────────────────

export type MentalHealthReferral = Record<string, unknown> & {
  id?: string;
  episode_id?: string;
  handover_id?: string;
  subject_cpid?: string | null;
  status?: "PENDING" | "ACCEPTED" | "DECLINED";
  request_reason?: string | null;
  requested_by?: string;
  requested_at?: string | null;
  reviewed_by?: string | null;
  reviewed_at?: string | null;
  decline_reason?: string | null;
};

export type MentalHealthAssessment = Record<string, unknown> & {
  id?: string;
  referral_id?: string;
  assessment_type?: string | null;
  mental_state_exam?: string | null;
  capacity_assessed?: boolean;
  capacity_outcome?: string | null;
  assessed_by?: string;
  assessed_at?: string | null;
  notes?: string | null;
};

export type MentalHealthRiskFormulation = Record<string, unknown> & {
  id?: string;
  assessment_id?: string;
  risk_to_self?: string;
  risk_to_others?: string;
  risk_summary?: string;
  formulated_by?: string;
  formulated_at?: string | null;
};

export type MentalHealthSafetyPlan = Record<string, unknown> & {
  id?: string;
  referral_id?: string;
  warning_signs?: string | null;
  coping_strategies?: string | null;
  support_contacts?: string | null;
  means_restriction_notes?: string | null;
  created_by?: string;
  created_at?: string | null;
};

export type MentalHealthFollowup = Record<string, unknown> & {
  id?: string;
  referral_id?: string;
  followup_type?: string | null;
  scheduled_at?: string | null;
  scheduled_by?: string;
  completed_at?: string | null;
  outcome_notes?: string | null;
};

export type MentalHealthInvoluntaryEpisode = Record<string, unknown> & {
  id?: string;
  referral_id?: string;
  legal_basis?: string;
  authorised_by?: string;
  authorised_at?: string | null;
  review_due_at?: string | null;
  status?: "OPEN" | "CLOSED";
  ended_at?: string | null;
  ended_reason?: string | null;
};

export type MentalHealthRestraintEvent = Record<string, unknown> & {
  id?: string;
  referral_id?: string;
  restraint_type?: string;
  reason?: string;
  de_escalation_attempted?: string;
  initiated_by?: string;
  initiated_at?: string | null;
  ended_at?: string | null;
  reviewed_by?: string | null;
  reviewed_at?: string | null;
  review_outcome?: string | null;
};

export type MentalHealthAdmissionRequest = Record<string, unknown> & {
  id?: string;
  referral_id?: string;
  reason?: string;
  requested_by?: string;
  requested_at?: string | null;
  target_facility_id?: string | null;
  status?: "RAISED" | "ACKNOWLEDGED";
  pct_admission_id?: string | null;
};

// ── Referral ─────────────────────────────────────────────────────────────────────────────────

export function useMentalHealthReferral(referralId?: string) {
  return useQuery({
    queryKey: ["mental-health-referral", referralId],
    queryFn: async () =>
      unwrap<MentalHealthReferral>(await apiClient.get(`${BASE}/referrals/${referralId}`)),
    enabled: !!referralId,
    staleTime: 10_000,
  });
}

// ── Assessment and risk formulation ──────────────────────────────────────────────────────────

export function useMentalHealthAssessments(referralId?: string) {
  return useQuery({
    queryKey: ["mental-health-assessments", referralId],
    queryFn: async () =>
      unwrap<MentalHealthAssessment[]>(await apiClient.get(`${BASE}/referrals/${referralId}/assessments`)),
    enabled: !!referralId,
    staleTime: 10_000,
  });
}

export function useRecordMentalHealthAssessment(referralId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      assessmentType?: string;
      mentalStateExam?: string;
      capacityAssessed?: boolean;
      capacityOutcome?: string;
      assessedBy?: string;
      notes?: string;
    }) => apiClient.post(`${BASE}/referrals/${referralId}/assessments`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["mental-health-assessments", referralId] });
    },
  });
}

/** Risk is the conclusion of one assessment, so it is keyed on the assessment, not the referral. */
export function useMentalHealthRiskFormulations(assessmentId?: string) {
  return useQuery({
    queryKey: ["mental-health-risk-formulations", assessmentId],
    queryFn: async () =>
      unwrap<MentalHealthRiskFormulation[]>(
        await apiClient.get(`${BASE}/assessments/${assessmentId}/risk-formulation`),
      ),
    enabled: !!assessmentId,
    staleTime: 10_000,
  });
}

export function useRecordMentalHealthRiskFormulation(assessmentId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      riskToSelf: string;
      riskToOthers: string;
      riskSummary: string;
      formulatedBy?: string;
    }) => apiClient.post(`${BASE}/assessments/${assessmentId}/risk-formulation`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["mental-health-risk-formulations", assessmentId] });
    },
  });
}

// ── Safety plan ──────────────────────────────────────────────────────────────────────────────

export function useMentalHealthSafetyPlans(referralId?: string) {
  return useQuery({
    queryKey: ["mental-health-safety-plans", referralId],
    queryFn: async () =>
      unwrap<MentalHealthSafetyPlan[]>(await apiClient.get(`${BASE}/referrals/${referralId}/safety-plans`)),
    enabled: !!referralId,
    staleTime: 10_000,
  });
}

export function useRecordMentalHealthSafetyPlan(referralId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      warningSigns?: string;
      copingStrategies?: string;
      supportContacts?: string;
      meansRestrictionNotes?: string;
      createdBy?: string;
    }) => apiClient.post(`${BASE}/referrals/${referralId}/safety-plans`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["mental-health-safety-plans", referralId] });
    },
  });
}

// ── Follow-up ────────────────────────────────────────────────────────────────────────────────

export function useMentalHealthFollowups(referralId?: string) {
  return useQuery({
    queryKey: ["mental-health-followups", referralId],
    queryFn: async () =>
      unwrap<MentalHealthFollowup[]>(await apiClient.get(`${BASE}/referrals/${referralId}/followups`)),
    enabled: !!referralId,
    staleTime: 10_000,
  });
}

export function useMentalHealthFollowupActions(referralId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["mental-health-followups", referralId] });
  };
  return {
    schedule: useMutation({
      mutationFn: (body: { scheduledAt: string; followupType?: string; scheduledBy?: string }) =>
        apiClient.post(`${BASE}/referrals/${referralId}/followups`, body),
      onSuccess: invalidate,
    }),
    complete: useMutation({
      mutationFn: ({ followupId, outcomeNotes }: { followupId: string; outcomeNotes?: string }) =>
        apiClient.post(`${BASE}/followups/${followupId}/complete`, { outcomeNotes }),
      onSuccess: invalidate,
    }),
  };
}

// ── Involuntary episode ──────────────────────────────────────────────────────────────────────

export function useMentalHealthInvoluntaryEpisodes(referralId?: string) {
  return useQuery({
    queryKey: ["mental-health-involuntary-episodes", referralId],
    queryFn: async () =>
      unwrap<MentalHealthInvoluntaryEpisode[]>(
        await apiClient.get(`${BASE}/referrals/${referralId}/involuntary-episodes`),
      ),
    enabled: !!referralId,
    staleTime: 5_000,
  });
}

export function useMentalHealthInvoluntaryEpisodeActions(referralId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["mental-health-involuntary-episodes", referralId] });
  };
  return {
    open: useMutation({
      mutationFn: (body: { legalBasis: string; authorisedBy?: string; reviewDueAt?: string }) =>
        apiClient.post(`${BASE}/referrals/${referralId}/involuntary-episodes`, body),
      onSuccess: invalidate,
    }),
    close: useMutation({
      mutationFn: ({ episodeId, endedReason }: { episodeId: string; endedReason: string }) =>
        apiClient.post(`${BASE}/involuntary-episodes/${episodeId}/close`, { endedReason }),
      onSuccess: invalidate,
    }),
  };
}

// ── Restraint ────────────────────────────────────────────────────────────────────────────────

export function useMentalHealthRestraintEvents(referralId?: string) {
  return useQuery({
    queryKey: ["mental-health-restraint-events", referralId],
    queryFn: async () =>
      unwrap<MentalHealthRestraintEvent[]>(
        await apiClient.get(`${BASE}/referrals/${referralId}/restraint-events`),
      ),
    enabled: !!referralId,
    staleTime: 5_000,
  });
}

/** The facility-wide backlog: restraint that happened and has not yet been reviewed. */
export function useUnreviewedRestraintEvents() {
  return useQuery({
    queryKey: ["mental-health-restraint-events", "unreviewed"],
    queryFn: async () =>
      unwrap<MentalHealthRestraintEvent[]>(await apiClient.get(`${BASE}/restraint-events/unreviewed`)),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useMentalHealthRestraintActions(referralId?: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["mental-health-restraint-events"] });
  };
  return {
    record: useMutation({
      mutationFn: (body: {
        restraintType: string;
        reason: string;
        deEscalationAttempted: string;
        initiatedBy?: string;
      }) => apiClient.post(`${BASE}/referrals/${referralId}/restraint-events`, body),
      onSuccess: invalidate,
    }),
    end: useMutation({
      mutationFn: (eventId: string) => apiClient.post(`${BASE}/restraint-events/${eventId}/end`, {}),
      onSuccess: invalidate,
    }),
    review: useMutation({
      mutationFn: ({
        eventId,
        reviewOutcome,
        reviewedBy,
      }: {
        eventId: string;
        reviewOutcome: string;
        reviewedBy?: string;
      }) => apiClient.post(`${BASE}/restraint-events/${eventId}/review`, { reviewOutcome, reviewedBy }),
      onSuccess: invalidate,
    }),
  };
}

// ── Admission requests ───────────────────────────────────────────────────────────────────────

export function useMentalHealthAdmissionRequests(referralId?: string) {
  return useQuery({
    queryKey: ["mental-health-admission-requests", referralId],
    queryFn: async () =>
      unwrap<MentalHealthAdmissionRequest[]>(
        await apiClient.get(`${BASE}/referrals/${referralId}/admission-requests`),
      ),
    enabled: !!referralId,
    staleTime: 10_000,
  });
}

export function useMentalHealthAdmissionRequestActions(referralId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["mental-health-admission-requests", referralId] });
  };
  return {
    raise: useMutation({
      mutationFn: (body: { reason: string; targetFacilityId?: string; requestedBy?: string }) =>
        apiClient.post(`${BASE}/referrals/${referralId}/admission-requests`, body),
      onSuccess: invalidate,
    }),
    acknowledge: useMutation({
      mutationFn: ({ requestId, pctAdmissionId }: { requestId: string; pctAdmissionId: string }) =>
        apiClient.post(`${BASE}/admission-requests/${requestId}/acknowledge`, { pctAdmissionId }),
      onSuccess: invalidate,
    }),
  };
}
