"use client";

/**
 * Respectful maternity care (RMNP W12) — citizen instrument + feedback.
 *
 * Backend contract (do not guess, and never fall back to a hardcoded copy of the questions —
 * the scoring polarity of the reverse-scored item is governed content, not something a client is
 * allowed to cache indefinitely or reconstruct locally):
 *
 *   GET  /internal/v1/maternity/respectful-care/instrument -> { data: Instrument, meta }
 *        502 (instrument_unavailable) when CKP cannot serve the measure set.
 *   POST /internal/v1/maternity/respectful-care/feedback   -> { data: FeedbackResult, meta }
 *        body (camelCase): { facilityId?, encounterRef?, providerPublicId?, reportingPeriod?,
 *          answers?: Record<measureCode, 1-5>, narrative?, identifyMe? }
 *
 * Answers are submitted RAW (1-5, exactly as given). Reverse-scoring is CKP's job, never this
 * client's — see docs/frontend/GAP_CLOSURE_RULES.md and the BFF controller's own doc comment.
 */

import { useCallback, useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";

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

interface InstrumentEnvelope {
  data?: unknown;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

function asStringArray(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const out = value.filter((v): v is string => typeof v === "string");
  return out.length > 0 ? out : [];
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

/** Never invents questions: returns null (instrument unavailable) unless the server gave us a
 * real scale and at least one real measure. A client that renders zero questions as though the
 * instrument loaded would hide the outage from the woman filling it in. */
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

export function errMessage(e: unknown, fallback: string): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: unknown; message?: string; status?: number };
    // BFF shape for this route on failure: { error: "instrument_unavailable", message: "...", meta }
    if (typeof obj.error === "string" && typeof obj.message === "string") return obj.message;
    // Generic v1.2 nested error envelope: { error: { code, message } }
    if (obj.error && typeof obj.error === "object") {
      const nested = obj.error as { message?: string };
      if (nested.message) return nested.message;
    }
    if (typeof obj.message === "string") return obj.message;
    if (obj.status) return `${fallback} (HTTP ${obj.status}).`;
  }
  return fallback;
}

export interface UseRespectfulMaternityCareResult {
  instrument: RespectfulCareInstrument | null;
  loading: boolean;
  instrumentUnavailable: boolean;
  refetch: () => void;
  submitting: boolean;
  submitError: string | null;
  result: RespectfulCareFeedbackResult | null;
  submit: (input: SubmitRespectfulCareInput) => Promise<RespectfulCareFeedbackResult | null>;
}

export function useRespectfulMaternityCare(): UseRespectfulMaternityCareResult {
  const [instrument, setInstrument] = useState<RespectfulCareInstrument | null>(null);
  const [loading, setLoading] = useState(true);
  const [instrumentUnavailable, setInstrumentUnavailable] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<RespectfulCareFeedbackResult | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setInstrumentUnavailable(false);
      try {
        const res = await apiClient.get<InstrumentEnvelope>(
          "/internal/v1/maternity/respectful-care/instrument",
        );
        const normalized = normalizeInstrument(res?.data);
        if (cancelled) return;
        if (normalized) {
          setInstrument(normalized);
        } else {
          setInstrument(null);
          setInstrumentUnavailable(true);
        }
      } catch {
        // Do not fall back to a local copy of the questions — the reverse-scoring polarity is
        // governed content, and a stale copy would store an answer backwards without anyone
        // noticing. An outage means an honest "unavailable" state, not a guess.
        if (!cancelled) {
          setInstrument(null);
          setInstrumentUnavailable(true);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  const refetch = useCallback(() => setReloadToken((n) => n + 1), []);

  const submit = useCallback(async (input: SubmitRespectfulCareInput) => {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const res = await apiClient.post<InstrumentEnvelope>(
        "/internal/v1/maternity/respectful-care/feedback",
        input,
      );
      const normalized = normalizeFeedbackResult(res?.data);
      setResult(normalized);
      return normalized;
    } catch (e) {
      setSubmitError(errMessage(e, "Could not submit your feedback"));
      return null;
    } finally {
      setSubmitting(false);
    }
  }, []);

  return {
    instrument,
    loading,
    instrumentUnavailable,
    refetch,
    submitting,
    submitError,
    result,
    submit,
  };
}
