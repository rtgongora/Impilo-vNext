/**
 * Respectful Maternity Care Service — Citizen client-voice (RMNP W12).
 *
 * Backend: experience-bff RespectfulMaternityCareController
 *   GET  /internal/v1/maternity/respectful-care/instrument -> { data: Instrument, meta }
 *        502 (instrument_unavailable) when CKP cannot serve the measure set.
 *   POST /internal/v1/maternity/respectful-care/feedback   -> { data: FeedbackResult, meta }
 *
 * The instrument (prompts, scale captions, which item is reverse-scored) is governed content
 * served by the clinical-knowledge-platform-service and proxied unchanged through the BFF. This
 * service NEVER hardcodes a copy of the questions — an outage surfaces as `fetchInstrument`
 * rejecting, and the screen must show an honest "unavailable" state rather than any local
 * fallback (see docs/frontend/GAP_CLOSURE_RULES.md).
 *
 * Answers are submitted RAW (1-5, exactly as the woman gave them). Reverse-scoring is CKP's job,
 * never this client's.
 *
 * Note on envelope shape: unlike most experience-bff endpoints, this controller returns a plain
 * `{ data, meta }` object with no `success` boolean, so `@impilo/mobile-api-client`'s ApiEnvelope
 * auto-unwrap does not fire — `response.data` here is the *whole* `{ data, meta }` body, and the
 * actual payload is one level deeper at `response.data.data`.
 */

import { apiClient } from "@impilo/mobile-api-client";

const INSTRUMENT_PATH = "/internal/v1/maternity/respectful-care/instrument";
const FEEDBACK_PATH = "/internal/v1/maternity/respectful-care/feedback";

export interface RespectfulCareMeasure {
  measure: string;
  prompt: string;
  mistreatmentCategory?: string;
  reverseScored?: boolean;
  note?: string;
}

export interface RespectfulCareScale {
  type: string;
  low: number;
  high: number;
  lowMeans: string;
  highMeans: string;
  note?: string;
}

export interface RespectfulCareInstrument {
  instrumentId: string;
  name: string;
  appliesTo?: string;
  ratingDomain?: string;
  approvalStatus?: string;
  adaptationAuthority?: string;
  scale: RespectfulCareScale;
  measures: RespectfulCareMeasure[];
  reportingRules?: string[];
  anonymousByDefault?: boolean;
  regulationFirewall?: string;
}

export interface RespectfulCareNarrativeResult {
  filed: boolean;
  caseReference?: string;
  claimCode?: string;
  routedAs?: string;
  message?: string;
}

export interface RespectfulCareScoresResult {
  stored: boolean;
  reason?: string;
  message?: string;
  ratingId?: string;
  verifiedInteraction?: boolean;
  measuresStored?: number;
  notAsked?: string[];
  unknownCodes?: string[];
  regulationFirewall?: string;
}

export interface RespectfulCareFeedbackResult {
  narrative?: RespectfulCareNarrativeResult;
  scores?: RespectfulCareScoresResult;
  anonymous: boolean;
  narrativeLinkedToRating: boolean;
  linkageNote?: string;
}

export interface SubmitRespectfulCareInput {
  facilityId?: string;
  encounterRef?: string;
  providerPublicId?: string;
  reportingPeriod?: string;
  /** Measure code -> raw 1-5 answer, exactly as given. Never reverse-scored client-side. */
  answers?: Record<string, number>;
  narrative?: string;
  /** Explicit opt-in to being identifiable. Absent/false means anonymous (the default). */
  identifyMe?: boolean;
}

/** Thrown when the server responded but the payload was not a usable instrument or result — an
 * honest signal to the caller rather than a silently empty object. */
export class RespectfulCareUnavailableError extends Error {
  constructor(message = "The respectful maternity care instrument could not be retrieved.") {
    super(message);
    this.name = "RespectfulCareUnavailableError";
  }
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

function asStringArray(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined;
  return value.filter((v): v is string => typeof v === "string");
}

function normalizeMeasure(raw: unknown): RespectfulCareMeasure | null {
  const r = asRecord(raw);
  if (!r) return null;
  const measure = typeof r.measure === "string" ? r.measure : null;
  const prompt = typeof r.prompt === "string" ? r.prompt : null;
  if (!measure || !prompt) return null;
  return {
    measure,
    prompt,
    mistreatmentCategory:
      typeof r.mistreatment_category === "string" ? r.mistreatment_category : undefined,
    reverseScored: typeof r.reverse_scored === "boolean" ? r.reverse_scored : undefined,
    note: typeof r.note === "string" ? r.note : undefined,
  };
}

function normalizeScale(raw: unknown): RespectfulCareScale | null {
  const r = asRecord(raw);
  if (!r) return null;
  const low = typeof r.low === "number" ? r.low : Number(r.low);
  const high = typeof r.high === "number" ? r.high : Number(r.high);
  if (!Number.isFinite(low) || !Number.isFinite(high)) return null;
  return {
    type: typeof r.type === "string" ? r.type : "LIKERT_5",
    low,
    high,
    lowMeans: typeof r.low_means === "string" ? r.low_means : "never",
    highMeans: typeof r.high_means === "string" ? r.high_means : "always",
    note: typeof r.note === "string" ? r.note : undefined,
  };
}

function normalizeInstrument(raw: unknown): RespectfulCareInstrument | null {
  const r = asRecord(raw);
  if (!r) return null;
  const scale = normalizeScale(r.scale);
  const measuresRaw = Array.isArray(r.measures) ? r.measures : [];
  const measures = measuresRaw
    .map(normalizeMeasure)
    .filter((m): m is RespectfulCareMeasure => m !== null);
  if (!scale || measures.length === 0) return null;
  return {
    instrumentId: typeof r.instrument_id === "string" ? r.instrument_id : "RESPECTFUL_MATERNITY_CARE",
    name: typeof r.name === "string" ? r.name : "Respectful maternity care experience",
    appliesTo: typeof r.applies_to === "string" ? r.applies_to : undefined,
    ratingDomain: typeof r.rating_domain === "string" ? r.rating_domain : undefined,
    approvalStatus: typeof r.approval_status === "string" ? r.approval_status : undefined,
    adaptationAuthority: typeof r.adaptation_authority === "string" ? r.adaptation_authority : undefined,
    scale,
    measures,
    reportingRules: asStringArray(r.reporting_rules),
    anonymousByDefault: typeof r.anonymous_by_default === "boolean" ? r.anonymous_by_default : undefined,
    regulationFirewall: typeof r.regulation_firewall === "string" ? r.regulation_firewall : undefined,
  };
}

function normalizeNarrative(raw: unknown): RespectfulCareNarrativeResult | undefined {
  const r = asRecord(raw);
  if (!r) return undefined;
  return {
    filed: r.filed === true,
    caseReference: typeof r.case_reference === "string" ? r.case_reference : undefined,
    claimCode: typeof r.claim_code === "string" ? r.claim_code : undefined,
    routedAs: typeof r.routed_as === "string" ? r.routed_as : undefined,
    message: typeof r.message === "string" ? r.message : undefined,
  };
}

function normalizeScores(raw: unknown): RespectfulCareScoresResult | undefined {
  const r = asRecord(raw);
  if (!r) return undefined;
  return {
    stored: r.stored === true,
    reason: typeof r.reason === "string" ? r.reason : undefined,
    message: typeof r.message === "string" ? r.message : undefined,
    ratingId: typeof r.rating_id === "string" ? r.rating_id : undefined,
    verifiedInteraction: typeof r.verified_interaction === "boolean" ? r.verified_interaction : undefined,
    measuresStored: typeof r.measures_stored === "number" ? r.measures_stored : undefined,
    notAsked: asStringArray(r.not_asked),
    unknownCodes: asStringArray(r.unknown_codes),
    regulationFirewall: typeof r.regulation_firewall === "string" ? r.regulation_firewall : undefined,
  };
}

function normalizeFeedbackResult(raw: unknown): RespectfulCareFeedbackResult | null {
  const r = asRecord(raw);
  if (!r) return null;
  return {
    narrative: normalizeNarrative(r.narrative),
    scores: normalizeScores(r.scores),
    anonymous: r.anonymous !== false,
    narrativeLinkedToRating: r.narrative_linked_to_rating === true,
    linkageNote: typeof r.linkage_note === "string" ? r.linkage_note : undefined,
  };
}

/** Fetches the governed measure set. Rejects with {@link RespectfulCareUnavailableError} rather
 * than resolving with a guessed/partial instrument — never render a hardcoded fallback set. */
export async function fetchInstrument(): Promise<RespectfulCareInstrument> {
  const response = await apiClient.get<{ data?: unknown }>(INSTRUMENT_PATH);
  const normalized = normalizeInstrument(response.data?.data);
  if (!normalized) {
    throw new RespectfulCareUnavailableError();
  }
  return normalized;
}

export async function submitRespectfulCareFeedback(
  input: SubmitRespectfulCareInput,
): Promise<RespectfulCareFeedbackResult> {
  const response = await apiClient.post<{ data?: unknown }>(FEEDBACK_PATH, input);
  const normalized = normalizeFeedbackResult(response.data?.data);
  if (!normalized) {
    throw new RespectfulCareUnavailableError("The feedback submission response could not be read.");
  }
  return normalized;
}
