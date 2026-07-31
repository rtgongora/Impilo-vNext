"use client";

/**
 * W12 — the citizen confidential-maternity surface over the BFF proxy
 * (`/internal/v1/confidential/maternity/**` → pct-service / clinical-knowledge-platform-service).
 *
 * See docs/clinical/rmnp/citizen-pregnancy-smbp-mobile-contract.md §4 and §6 for the behaviours
 * this hook file exists to preserve:
 *   - too few readings is INSUFFICIENT_DATA, never CONTROLLED;
 *   - `dangerSignPresent` is omitted (never coerced to false) when she was not asked;
 *   - a 502 READINGS_UNAVAILABLE / CKP_UNAVAILABLE must never render as INSUFFICIENT_DATA or a
 *     controlled verdict — callers must check `isSmbpUnavailableError` before reading `.data`;
 *   - 409 on booking is a reconciliation outcome (she already has an open pregnancy), not a
 *     failure to mask — callers branch on `error.status`, never swallow it as a generic error;
 *   - 200 on booking is a successful offline replay, not a duplicate.
 *
 * The browser never holds a CPID (Identity Contract §12) — every call below uses the BFF's `"me"`
 * sentinel so the server resolves the citizen's own subject from her actor identity; a caller
 * that already has a real CPID (e.g. a future clinician-facing reuse of these hooks) may pass one.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/confidential/maternity";

/** The sentinel the BFF resolves server-side from the caller's actor identity. */
const SELF = "me";

// ── SMBP verdict (mirrors SmbpEscalationController's Result verbatim) ──────

export type SmbpVerdict = "URGENT" | "ELEVATED_REFER" | "CONTROLLED" | "INSUFFICIENT_DATA";

export interface SmbpAlertView {
  code: string;
  name: string;
  severity: string;
  interruptive: boolean;
  referralRequired: boolean;
  message: string;
  explanation: string | null;
  requiredAction: string | null;
  monitoringInstruction: string | null;
  triggeringFindings: string[] | null;
  sourceRefs: string[] | null;
}

export interface SmbpSummaryView {
  readingCount: number;
  meanCount: number;
  meanSystolic: number | null;
  meanDiastolic: number | null;
  maxSystolic: number | null;
  maxDiastolic: number | null;
  anySevere: boolean;
  elevatedMean: boolean;
  sufficient: boolean;
}

export interface SmbpVerdictData {
  verdict: SmbpVerdict;
  message: string;
  note: string;
  summary: SmbpSummaryView;
  alerts: SmbpAlertView[];
  contentVersion: string | null;
  approvalStatus: string | null;
}

export interface SmbpVerdictMeta {
  request_id: string;
  correlation_id: string;
  plan_id: string;
  raw_reading_count: number;
  paired_reading_count: number;
}

export interface SmbpVerdictResponse {
  data: SmbpVerdictData;
  meta: SmbpVerdictMeta;
}

/** The 502 shape for a series that could not be read or assessed — never a clinical verdict. */
export interface SmbpUnavailableError {
  status: number;
  error?: { code?: string; message?: string; clinical_note?: string };
}

/**
 * True for the two upstream-outage codes the contract forbids rendering as INSUFFICIENT_DATA or
 * CONTROLLED. Callers must check this before treating a thrown error as "no verdict available".
 */
export function isSmbpUnavailableError(err: unknown): err is SmbpUnavailableError {
  const e = err as SmbpUnavailableError | null;
  return (
    !!e &&
    e.status === 502 &&
    (e.error?.code === "READINGS_UNAVAILABLE" || e.error?.code === "CKP_UNAVAILABLE")
  );
}

// ── Pregnancy episode (mirrors ConfidentialReproductiveController's pregnancyView /
//    ConfidentialReproductiveIntakeController's openPregnancy response) ────

export type DatingMethod = "LMP" | "ULTRASOUND" | "ASSISTED_CONCEPTION" | "CLINICAL_ESTIMATE";

export interface DatingBasisRequest {
  method: DatingMethod;
  observedOn?: string;
  lastMenstrualPeriod?: string;
  lastMenstrualPeriodCertain?: boolean;
  measuredGestationalAgeDays?: number;
  conceptionDate?: string;
  embryoAgeDaysAtTransfer?: number;
  sourceRef?: string;
}

export interface OpenPregnancyEpisodeRequest {
  /** Omit for a citizen booking her own pregnancy — the BFF resolves it from her actor identity. */
  subjectCpid?: string;
  facilityId?: string;
  journeyId?: string;
  encounterId?: string;
  datingBases: DatingBasisRequest[];
  recordedOn?: string;
  confirmedOn?: string;
  confirmationBasis?: string;
  plurality?: number;
  conceptionMode?: string;
  clientOfflineId?: string;
  capturedAt?: string;
  recordedBy?: string;
}

export interface PregnancyEpisodeView {
  pregnancy_episode_id: string;
  subject_cpid: string;
  status: string;
  confirmation_basis?: string | null;
  confirmed_on?: string | null;
  pregnancy_start_date: string | null;
  estimated_delivery_date: string | null;
  dating_method: string | null;
  dating_confidence: string | null;
  dating_plus_minus_days?: number | null;
  dating_revision_count?: number | null;
  plurality?: number | null;
  plurality_basis?: string | null;
  /** NOT_ASSESSED is not a negative — render exactly as recorded, never as "low risk". */
  risk_status?: string | null;
  intended_pregnancy?: boolean | null;
  planned_place_of_birth?: string | null;
  outcome?: string | null;
  ended_on?: string | null;
  sensitivity_class?: string | null;
  confidentiality_category?: string | null;
  confidentiality_basis?: string | null;
  /** Present only on the booking write response. */
  client_offline_id?: string | null;
  /** True when this 200/201 is a replayed offline packet, not a fresh booking. */
  replayed?: boolean;
}

/** The 409 conflict body — she already has an open pregnancy to reconcile against. */
export interface PregnancyConflictError {
  status: 409;
  data?: {
    code?: string;
    message?: string;
    existing_pregnancy_episode_id?: string;
    reconciliation_task?: string;
  };
  meta?: Record<string, unknown>;
}

/** The 422 undatable body — no LMP, no scan, nothing to date the pregnancy from. */
export interface PregnancyUndatableError {
  status: 422;
  data?: { code?: string; message?: string };
  meta?: Record<string, unknown>;
}

export function isPregnancyConflict(err: unknown): err is PregnancyConflictError {
  return !!err && typeof err === "object" && (err as { status?: number }).status === 409;
}

export function isPregnancyUndatable(err: unknown): err is PregnancyUndatableError {
  return !!err && typeof err === "object" && (err as { status?: number }).status === 422;
}

export const confidentialMaternityKeys = {
  smbpVerdict: (planId: string, dangerSignPresent?: boolean) =>
    ["confidential-maternity", "smbp-verdict", planId, dangerSignPresent ?? null] as const,
  currentPregnancy: (subjectCpid: string) =>
    ["confidential-maternity", "pregnancy-current", subjectCpid] as const,
  pregnancyHistory: (subjectCpid: string) =>
    ["confidential-maternity", "pregnancy-history", subjectCpid] as const,
};

/**
 * The SMBP series verdict for a monitoring plan.
 *
 * `dangerSignPresent` is `boolean | undefined` on purpose — omit it (never pass `false`) when she
 * has not been asked. The engine reads an absent value as UNKNOWN; a coerced `false` would assert
 * on her behalf that she has no danger signs, the single most dangerous default on this pathway.
 */
export function useSmbpVerdict(planId: string | undefined, dangerSignPresent?: boolean) {
  return useQuery<SmbpVerdictResponse>({
    queryKey: confidentialMaternityKeys.smbpVerdict(planId ?? "unknown", dangerSignPresent),
    enabled: !!planId,
    queryFn: () => {
      const params = new URLSearchParams({ planId: planId! });
      if (dangerSignPresent !== undefined) {
        params.set("dangerSignPresent", String(dangerSignPresent));
      }
      return apiClient.get<SmbpVerdictResponse>(`${BASE}/smbp/verdict?${params.toString()}`);
    },
    // A 502 READINGS_UNAVAILABLE/CKP_UNAVAILABLE is a real outage, not a query to keep retrying
    // against a series that will not resolve by itself.
    retry: (failureCount, error) => !isSmbpUnavailableError(error) && failureCount < 2,
  });
}

/** Her current pregnancy, or null when there isn't one (or it is not visible to her). */
export function useCurrentPregnancyEpisode(subjectCpid: string = SELF, enabled = true) {
  return useQuery<PregnancyEpisodeView | null>({
    queryKey: confidentialMaternityKeys.currentPregnancy(subjectCpid),
    enabled,
    queryFn: () =>
      apiClient
        .get<{ data: PregnancyEpisodeView | null }>(
          `${BASE}/pregnancy-episodes/${encodeURIComponent(subjectCpid)}/current`,
        )
        .then((res) => res.data ?? null),
  });
}

/** Her obstetric history, ongoing and ended. */
export function usePregnancyEpisodes(subjectCpid: string = SELF, enabled = true) {
  return useQuery<PregnancyEpisodeView[]>({
    queryKey: confidentialMaternityKeys.pregnancyHistory(subjectCpid),
    enabled,
    queryFn: () =>
      apiClient
        .get<{ data: PregnancyEpisodeView[] | null }>(
          `${BASE}/pregnancy-episodes/${encodeURIComponent(subjectCpid)}`,
        )
        .then((res) => res.data ?? []),
  });
}

/**
 * Book a pregnancy. Resolves to the booked/replayed episode on 201/200 — both are success; check
 * `.replayed` to tell them apart for messaging, never to decide whether it "worked".
 *
 * A 409 (already has an open pregnancy) or 422 (undatable) rejects the mutation promise with the
 * raw `{status, data, meta}` thrown by the api client — callers must branch on `error.status`
 * (see {@link isPregnancyConflict}, {@link isPregnancyUndatable}) rather than showing a generic
 * failure banner, and must never retry a 422 unchanged.
 */
export function useOpenPregnancyEpisode() {
  const queryClient = useQueryClient();
  return useMutation<PregnancyEpisodeView, unknown, OpenPregnancyEpisodeRequest>({
    mutationFn: (body) =>
      apiClient
        .post<{ data: PregnancyEpisodeView }>(`${BASE}/pregnancy-episodes`, body)
        .then((res) => res.data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["confidential-maternity"] });
    },
  });
}
