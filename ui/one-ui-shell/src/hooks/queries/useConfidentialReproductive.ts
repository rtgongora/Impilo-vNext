"use client";

/**
 * W13-B — confidential reproductive-health reads over the BFF proxy
 * (`/internal/v1/confidential/reproductive/**` → pct-service ConfidentialReproductiveController).
 *
 * Contract rules this hook file exists to preserve:
 *   - empty list / null = withhold (indistinguishable from absence) — never render as proof of none;
 *   - 502 PCT_UNAVAILABLE = upstream outage — never render as an empty chart or "no records";
 *   - citizens use the `"me"` sentinel (Identity Contract §12); clinicians pass the patient's CPID.
 *
 * TOP procedures, losses and authorisations are clinician-primary surfaces — citizen hooks expose
 * contraception coverage only via {@link useContraceptionCoverage} with `"me"`.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/confidential/reproductive";

/** Sentinel the BFF resolves server-side from the caller's actor identity. */
export const REPRODUCTIVE_SELF = "me";

// ── Shared stamp fields (travel with every record on the confidential lane) ──

export interface ConfidentialStamp {
  sensitivity_class?: string | null;
  confidentiality_category?: string | null;
  confidentiality_basis?: string | null;
}

// ── Contraception coverage (computed, not bare episodes) ─────────────────────

export interface ContraceptionCoverageView extends ConfidentialStamp {
  contraceptive_episode_id: string;
  subject_cpid: string;
  method_code: string | null;
  method_class: string | null;
  status: string | null;
  started_on: string | null;
  expected_end_on: string | null;
  ended_on: string | null;
  discontinuation_reason: string | null;
  eligibility_category: string | null;
  eligibility_overridden: boolean | null;
  deferred_insertion_owed: boolean | null;
  coverage_status: string | null;
  days_remaining: number | null;
  last_administered_on: string | null;
  next_due_on: string | null;
  /** Carried verbatim — UNKNOWN with no reason must not render as a shrug. */
  coverage_unknown_why: string | null;
  content_version: string | null;
}

export interface ContraceptiveEpisodeView extends ConfidentialStamp {
  contraceptive_episode_id: string;
  subject_cpid: string;
  method_code: string | null;
  method_class: string | null;
  status: string | null;
  started_on: string | null;
  expected_end_on: string | null;
  ended_on: string | null;
  discontinuation_reason: string | null;
  eligibility_category: string | null;
  eligibility_overridden: boolean | null;
  deferred_insertion_owed: boolean | null;
}

// ── Pregnancy loss ───────────────────────────────────────────────────────────

export interface PregnancyLossView extends ConfidentialStamp {
  loss_record_id: string;
  mother_cpid: string;
  pregnancy_episode_id: string | null;
  loss_type: string | null;
  occurred_on: string | null;
}

// ── TOP authorisation & termination procedure ──────────────────────────────────

export interface TopAuthorisationView extends ConfidentialStamp {
  authorisation_id: string;
  subject_cpid: string;
  pregnancy_episode_id: string | null;
  status: string | null;
  legal_ground: string | null;
  gestation_weeks_at_request: number | null;
  gestational_limit_weeks: number | null;
  certifying_authority: string | null;
  certified_on: string | null;
  consent_given: boolean | null;
  consent_recorded_on: string | null;
  objection_referral_ref: string | null;
}

export interface TerminationProcedureView extends ConfidentialStamp {
  procedure_id: string;
  subject_cpid: string;
  method: string | null;
  performed_on: string | null;
}

/** The 502 shape when pct could not be reached — never an empty list. */
export interface ReproductiveUnavailableError {
  status: number;
  error?: { code?: string; message?: string; clinical_note?: string };
}

/**
 * True for PCT_UNAVAILABLE on a 502. Callers must check this before treating a thrown error as
 * "nothing to see" — an outage is a different clinical fact from an empty confidential lane.
 */
export function isReproductiveReadUnavailable(err: unknown): err is ReproductiveUnavailableError {
  const e = err as ReproductiveUnavailableError | null;
  return !!e && e.status === 502 && e.error?.code === "PCT_UNAVAILABLE";
}

export const confidentialReproductiveKeys = {
  contraception: (subjectCpid: string, asOf?: string) =>
    ["confidential-reproductive", "contraception", subjectCpid, asOf ?? null] as const,
  contraceptionHistory: (subjectCpid: string) =>
    ["confidential-reproductive", "contraception-history", subjectCpid] as const,
  losses: (motherCpid: string) => ["confidential-reproductive", "losses", motherCpid] as const,
  topAuthorisations: (subjectCpid: string) =>
    ["confidential-reproductive", "top-authorisations", subjectCpid] as const,
  terminations: (subjectCpid: string) =>
    ["confidential-reproductive", "terminations", subjectCpid] as const,
};

function encodeCpid(cpid: string): string {
  return encodeURIComponent(cpid);
}

/** Current contraceptive methods with computed coverage. Citizens may pass `"me"`. */
export function useContraceptionCoverage(
  subjectCpid: string = REPRODUCTIVE_SELF,
  options?: { asOf?: string; enabled?: boolean },
) {
  const enabled = options?.enabled ?? true;
  return useQuery<ContraceptionCoverageView[]>({
    queryKey: confidentialReproductiveKeys.contraception(subjectCpid, options?.asOf),
    enabled: enabled && !!subjectCpid,
    queryFn: () => {
      const params = options?.asOf ? `?asOf=${encodeURIComponent(options.asOf)}` : "";
      return apiClient
        .get<{ data: ContraceptionCoverageView[] | null }>(
          `${BASE}/contraception/${encodeCpid(subjectCpid)}${params}`,
        )
        .then((res) => res.data ?? []);
    },
    retry: (failureCount, error) => !isReproductiveReadUnavailable(error) && failureCount < 2,
  });
}

/** Full contraceptive method history — clinician surfaces. */
export function useContraceptionHistory(subjectCpid: string, enabled = true) {
  return useQuery<ContraceptiveEpisodeView[]>({
    queryKey: confidentialReproductiveKeys.contraceptionHistory(subjectCpid),
    enabled: enabled && !!subjectCpid,
    queryFn: () =>
      apiClient
        .get<{ data: ContraceptiveEpisodeView[] | null }>(
          `${BASE}/contraception/${encodeCpid(subjectCpid)}/history`,
        )
        .then((res) => res.data ?? []),
    retry: (failureCount, error) => !isReproductiveReadUnavailable(error) && failureCount < 2,
  });
}

/** Pregnancy losses for a mother — clinician-primary. */
export function usePregnancyLosses(motherCpid: string, enabled = true) {
  return useQuery<PregnancyLossView[]>({
    queryKey: confidentialReproductiveKeys.losses(motherCpid),
    enabled: enabled && !!motherCpid,
    queryFn: () =>
      apiClient
        .get<{ data: PregnancyLossView[] | null }>(`${BASE}/losses/${encodeCpid(motherCpid)}`)
        .then((res) => res.data ?? []),
    retry: (failureCount, error) => !isReproductiveReadUnavailable(error) && failureCount < 2,
  });
}

/** Termination authorisations including declined requests — clinician-primary. */
export function useTopAuthorisations(subjectCpid: string, enabled = true) {
  return useQuery<TopAuthorisationView[]>({
    queryKey: confidentialReproductiveKeys.topAuthorisations(subjectCpid),
    enabled: enabled && !!subjectCpid,
    queryFn: () =>
      apiClient
        .get<{ data: TopAuthorisationView[] | null }>(
          `${BASE}/top-authorisations/${encodeCpid(subjectCpid)}`,
        )
        .then((res) => res.data ?? []),
    retry: (failureCount, error) => !isReproductiveReadUnavailable(error) && failureCount < 2,
  });
}

/** Termination procedures — clinician-primary. */
export function useTerminations(subjectCpid: string, enabled = true) {
  return useQuery<TerminationProcedureView[]>({
    queryKey: confidentialReproductiveKeys.terminations(subjectCpid),
    enabled: enabled && !!subjectCpid,
    queryFn: () =>
      apiClient
        .get<{ data: TerminationProcedureView[] | null }>(
          `${BASE}/terminations/${encodeCpid(subjectCpid)}`,
        )
        .then((res) => res.data ?? []),
    retry: (failureCount, error) => !isReproductiveReadUnavailable(error) && failureCount < 2,
  });
}
