/**
 * Pregnancy episode booking — Citizen mobile (RMNP W12).
 *
 * Backend: experience-bff ConfidentialMaternityController
 *   POST /internal/v1/confidential/maternity/pregnancy-episodes                (camelCase body)
 *   GET  /internal/v1/confidential/maternity/pregnancy-episodes/{cpid}/current
 *   GET  /internal/v1/confidential/maternity/pregnancy-episodes/{cpid}
 *
 * Status semantics (see docs/clinical/rmnp/citizen-pregnancy-smbp-mobile-contract.md and
 * ConfidentialReproductiveIntakeController#openPregnancy):
 *   - 201 created and 200 replayed-offline-packet are BOTH success — `bookPregnancy` resolves for
 *     either, and callers read `.replayed` to tell them apart for messaging only, never to decide
 *     whether the booking "worked".
 *   - 409 (`PREGNANCY_ALREADY_BOOKED`) means she already has an ongoing pregnancy to reconcile
 *     against — thrown as {@link PregnancyConflictError}, never swallowed into a generic failure.
 *   - 422 (`PREGNANCY_UNDATABLE`) means no usable dating basis was supplied — thrown as
 *     {@link PregnancyUndatableError}. Never retried unchanged.
 *
 * `clientOfflineId` is generated once per booking attempt (by the caller, via
 * {@link newClientOfflineId}) and must be resent unchanged on retry — that is what lets a
 * duplicate submission (flaky connectivity, a resubmit before she saw a response) come back as a
 * 200 replay of the same episode instead of a second booking.
 *
 * The browser/app never holds a raw CPID (Identity Contract §12) for this lane — every call below
 * uses the BFF's `"me"` sentinel so the server resolves her subject from `X-Actor-ID`.
 */

import { apiClient, ApiError } from "@impilo/mobile-api-client";
import { generateId } from "@impilo/mobile-trust";

const BASE = "/internal/v1/confidential/maternity";

/** The sentinel the BFF resolves server-side from the caller's actor identity. */
const SELF = "me";

export type DatingMethod = "LMP" | "ULTRASOUND" | "ASSISTED_CONCEPTION" | "CLINICAL_ESTIMATE";

export interface DatingBasisInput {
  method: DatingMethod;
  observedOn?: string;
  lastMenstrualPeriod?: string;
  lastMenstrualPeriodCertain?: boolean;
  measuredGestationalAgeDays?: number;
  conceptionDate?: string;
  embryoAgeDaysAtTransfer?: number;
  sourceRef?: string;
}

export interface BookPregnancyInput {
  datingBases: DatingBasisInput[];
  facilityId?: string;
  clientOfflineId?: string;
}

export interface PregnancyEpisode {
  pregnancyEpisodeId: string;
  subjectCpid: string;
  status: string;
  pregnancyStartDate: string | null;
  estimatedDeliveryDate: string | null;
  datingMethod: string | null;
  datingConfidence: string | null;
  datingPlusMinusDays: number | null;
  plurality: number | null;
  /** NOT_ASSESSED is not a negative — render exactly as recorded, never as "low risk". */
  riskStatus: string | null;
  outcome: string | null;
  endedOn: string | null;
  clientOfflineId?: string | null;
  /** True when this 200/201 is a replayed offline packet, not a fresh booking. */
  replayed?: boolean;
}

/** The 409 conflict — she already has an open pregnancy to reconcile against. */
export class PregnancyConflictError extends Error {
  public readonly existingPregnancyEpisodeId?: string;
  public readonly reconciliationTask?: string;

  constructor(message: string, existingPregnancyEpisodeId?: string, reconciliationTask?: string) {
    super(message);
    this.name = "PregnancyConflictError";
    this.existingPregnancyEpisodeId = existingPregnancyEpisodeId;
    this.reconciliationTask = reconciliationTask;
  }
}

/** The 422 — no LMP, no scan, nothing to date the pregnancy from. */
export class PregnancyUndatableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "PregnancyUndatableError";
  }
}

export function isPregnancyConflict(err: unknown): err is PregnancyConflictError {
  return err instanceof PregnancyConflictError;
}

export function isPregnancyUndatable(err: unknown): err is PregnancyUndatableError {
  return err instanceof PregnancyUndatableError;
}

/** A fresh offline-attempt id — generate once per booking attempt and resend unchanged on retry. */
export function newClientOfflineId(): string {
  return generateId();
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

function normalizeEpisode(raw: unknown): PregnancyEpisode | null {
  const r = asRecord(raw);
  if (!r) return null;
  const id = r.pregnancy_episode_id;
  if (typeof id !== "string") return null;
  return {
    pregnancyEpisodeId: id,
    subjectCpid: typeof r.subject_cpid === "string" ? r.subject_cpid : "",
    status: typeof r.status === "string" ? r.status : "UNKNOWN",
    pregnancyStartDate: typeof r.pregnancy_start_date === "string" ? r.pregnancy_start_date : null,
    estimatedDeliveryDate:
      typeof r.estimated_delivery_date === "string" ? r.estimated_delivery_date : null,
    datingMethod: typeof r.dating_method === "string" ? r.dating_method : null,
    datingConfidence: typeof r.dating_confidence === "string" ? r.dating_confidence : null,
    datingPlusMinusDays: typeof r.dating_plus_minus_days === "number" ? r.dating_plus_minus_days : null,
    plurality: typeof r.plurality === "number" ? r.plurality : null,
    riskStatus: typeof r.risk_status === "string" ? r.risk_status : null,
    outcome: typeof r.outcome === "string" ? r.outcome : null,
    endedOn: typeof r.ended_on === "string" ? r.ended_on : null,
    clientOfflineId: typeof r.client_offline_id === "string" ? r.client_offline_id : null,
    replayed: r.replayed === true,
  };
}

/**
 * Books a pregnancy for the caller herself.
 *
 * Resolves on 201 (created) and 200 (replayed offline packet) alike — both are success. Rejects
 * with {@link PregnancyConflictError} on 409 and {@link PregnancyUndatableError} on 422; any other
 * rejection is a plain {@link ApiError}/network failure that the caller should treat as failed.
 */
export async function bookPregnancy(input: BookPregnancyInput): Promise<PregnancyEpisode> {
  const body = {
    datingBases: input.datingBases,
    facilityId: input.facilityId,
    clientOfflineId: input.clientOfflineId,
  };

  try {
    const response = await apiClient.post<{ data?: unknown }>(`${BASE}/pregnancy-episodes`, body);
    const episode = normalizeEpisode(response.data?.data);
    if (!episode) {
      throw new Error("The booking response could not be read.");
    }
    return episode;
  } catch (err) {
    if (err instanceof ApiError && err.status === 409) {
      const details = err.details ?? {};
      throw new PregnancyConflictError(
        err.message,
        typeof details.existing_pregnancy_episode_id === "string"
          ? details.existing_pregnancy_episode_id
          : undefined,
        typeof details.reconciliation_task === "string" ? details.reconciliation_task : undefined,
      );
    }
    if (err instanceof ApiError && err.status === 422) {
      throw new PregnancyUndatableError(err.message);
    }
    throw err;
  }
}

/** Her current pregnancy, or null when there isn't one (or it is not visible to her). */
export async function fetchCurrentPregnancy(): Promise<PregnancyEpisode | null> {
  const response = await apiClient.get<{ data?: unknown }>(
    `${BASE}/pregnancy-episodes/${SELF}/current`,
  );
  return normalizeEpisode(response.data?.data);
}

/** Her obstetric history, ongoing and ended. */
export async function fetchPregnancyHistory(): Promise<PregnancyEpisode[]> {
  const response = await apiClient.get<{ data?: unknown }>(`${BASE}/pregnancy-episodes/${SELF}`);
  const raw = Array.isArray(response.data?.data) ? response.data?.data : [];
  return (raw ?? []).map(normalizeEpisode).filter((e): e is PregnancyEpisode => e !== null);
}
