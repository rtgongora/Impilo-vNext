/**
 * Guidance & Knowledge Hooks — Health OS §12 (Knowledge), §13 (Conversational)
 *
 * Wraps the GuidanceController BFF endpoints for the intelligent experience layer.
 * Covers: conversational ask, federated search, reminders, education content.
 */

import { useQuery, useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Conversational (§13) ────────────────────────────────────────────

export interface GuidanceResponse {
  response: string;
  confidence: number;
  sources: Array<{ title: string; url?: string; type: string }>;
  followUpPrompts: string[];
  personalized: boolean;
}

export function useAskGuidance() {
  return useMutation({
    mutationFn: (body: { question: string; personalized: boolean; context?: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<GuidanceResponse>>("/internal/v1/guidance/ask", body),
  });
}

/** Governed EDLIZ-aligned assistant (structured citations, rules, traces). */
export interface ClinicalAskResponse {
  answer_summary: string;
  support_mode: string;
  source_citations: Array<Record<string, unknown>>;
  warnings: string[];
  trace_id?: string;
  question_classification?: string;
  disclaimer?: string;
  [key: string]: unknown;
}

export function useAskEdlizClinical() {
  return useMutation({
    mutationFn: (body: {
      question: string;
      citizen_mode?: boolean;
      role?: string;
      patient_context?: Record<string, unknown>;
      encounter_id?: string;
    }) => apiClient.post<ApiResponse<ClinicalAskResponse>>("/internal/v1/clinical/assistant/ask", body),
  });
}

// ── Context-aware interpretation (CDS keystone) ─────────────────────

/** A reference interval used for an interpretation, with provenance. */
export interface InterpretationReferenceRange {
  low: number | null;
  high: number | null;
  critical_low: number | null;
  critical_high: number | null;
  unit: string | null;
  source: string | null; // LAB_DELIVERED | OBSERVATION_DEFINITION
  artifact_id: string | null;
  version: string | null;
  canonical_url: string | null;
}

/** One interpreted observation as returned by the CKP interpretation engine. */
export interface InterpretedObservationDto {
  loinc_code: string | null;
  local_code: string | null;
  display_name: string | null;
  category: string | null;
  value: number | null;
  unit: string | null;
  /** NORMAL | LOW | HIGH | CRITICAL_LOW | CRITICAL_HIGH | NO_REFERENCE_RANGE | UNIT_MISMATCH | DATA_QUALITY */
  interpretation: string;
  fhir_interpretation_code: string | null;
  abnormal: boolean;
  critical: boolean;
  reference_range: InterpretationReferenceRange | null;
  delta: number | null;
  trend: string | null; // RISING | FALLING | STABLE
  note: string | null;
  advisory: boolean;
}

export interface InterpretationResult {
  interpreted_observations: InterpretedObservationDto[];
  alerts: Array<Record<string, unknown>>;
  ranges_used: Array<Record<string, unknown>>;
  trace_id?: string;
  support_mode?: string;
  advisory?: boolean;
}

/** A single observation to interpret (value + UCUM unit + identifying code). */
export interface InterpretObservationInput {
  loincCode?: string;
  localCode?: string;
  displayName?: string;
  category?: string; // VITAL | LAB | DERIVED
  value: number;
  unit: string;
  priorValue?: number;
  priorUnit?: string;
  deliveredLow?: number;
  deliveredHigh?: number;
  deliveredUnit?: string;
}

export interface InterpretClinicalRequest {
  patient_id?: string;
  encounter_id?: string;
  context: Record<string, unknown>;
  observations: InterpretObservationInput[];
}

/**
 * Context-aware interpretation of vitals/labs against patient-appropriate reference intervals
 * (auditable, advisory). Backed by CKP POST /internal/v1/clinical/interpretation/evaluate.
 */
export function useInterpretClinical() {
  return useMutation({
    mutationFn: (body: InterpretClinicalRequest) =>
      apiClient.post<ApiResponse<InterpretationResult>>("/internal/v1/clinical/interpretation/evaluate", body),
  });
}

/** BFF → clinical platform: counts of pdf/* vs all sections (admin-equivalent JWT roles). */
export interface ClinicalSourceIngestionSummary {
  document_id: string;
  pdf_derived_section_count: number;
  total_section_count: number;
}

export function useClinicalSourceIngestionSummary(documentId: string | undefined) {
  return useQuery({
    queryKey: ["clinical", "source", "ingestion-summary", documentId],
    queryFn: () =>
      apiClient.get<ApiResponse<ClinicalSourceIngestionSummary>>(
        `/internal/v1/clinical/source/documents/${documentId}/ingestion-summary`
      ),
    enabled: Boolean(documentId),
  });
}

export function useClinicalDefaultEdlizDocumentId() {
  return useQuery({
    queryKey: ["clinical", "source", "edliz-default-document-id"],
    queryFn: () =>
      apiClient.get<ApiResponse<{ document_id: string; note: string }>>(
        "/internal/v1/clinical/source/edliz-default-document-id"
      ),
  });
}

/** Trigger PDF → source_sections indexing for a source_documents row. */
export function useIngestClinicalPdf() {
  return useMutation({
    mutationFn: (args: {
      documentId: string;
      body?: { pdf_path?: string; replace_pdf_sections?: boolean; verify_sha256?: boolean };
    }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/clinical/source/documents/${args.documentId}/ingest-pdf`,
        args.body ?? {}
      ),
  });
}

/** Prescribing decision support (optional `record_trace` for audit). */
export function useClinicalPrescribingEvaluate() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/clinical/prescribing/evaluate", body),
  });
}

export function useClinicalPathways() {
  return useQuery({
    queryKey: ["clinical", "pathways"],
    queryFn: () =>
      apiClient.get<ApiResponse<Array<Record<string, unknown>>>>("/internal/v1/clinical/pathways"),
  });
}

export function useStartClinicalPathwaySession() {
  return useMutation({
    mutationFn: (body: { pathway_id: string; patient_id?: string; encounter_id?: string }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>("/internal/v1/clinical/pathways/sessions", body),
  });
}

export function useAdvanceClinicalPathwaySession() {
  return useMutation({
    mutationFn: (args: { sessionId: string; answers: Record<string, unknown> }) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/clinical/pathways/sessions/${args.sessionId}/advance`,
        { answers: args.answers }
      ),
  });
}

// ── Reminders & Prompts ─────────────────────────────────────────────

export interface Reminder {
  id: string;
  type: "MEDICATION" | "SCREENING" | "FOLLOW_UP" | "CAMPAIGN" | "WELLNESS";
  title: string;
  description: string;
  dueDate?: string;
  priority: "LOW" | "MEDIUM" | "HIGH";
  dismissed: boolean;
}

export function useReminders() {
  return useQuery({
    queryKey: ["guidance", "reminders"],
    queryFn: () => apiClient.get<ApiResponse<Reminder[]>>("/internal/v1/guidance/reminders"),
  });
}

// ── Health Education (§12) ──────────────────────────────────────────

export interface EducationArticle {
  id: string;
  title: string;
  summary: string;
  category: string;
  relevanceScore: number;
  source: string;
  url?: string;
}

export function useEducation() {
  return useQuery({
    queryKey: ["guidance", "education"],
    queryFn: () => apiClient.get<ApiResponse<EducationArticle[]>>("/internal/v1/guidance/education"),
  });
}

// ── Federated Search (§12) ──────────────────────────────────────────

export interface SearchResult {
  id: string;
  title: string;
  snippet: string;
  domain: string;
  type: string;
  score: number;
  url?: string;
}

export function useSearch(query: string, domain = "all", page = 0, size = 20) {
  return useQuery({
    queryKey: ["guidance", "search", query, domain, page],
    queryFn: () => apiClient.get<ApiResponse<SearchResult[]>>(
      `/internal/v1/guidance/search?q=${encodeURIComponent(query)}&domain=${domain}&page=${page}&size=${size}`
    ),
    enabled: query.length >= 2,
  });
}

// ── Consent Status ──────────────────────────────────────────────────

export function useGuidanceConsent() {
  return useQuery({
    queryKey: ["guidance", "consent"],
    queryFn: () => apiClient.get<{ data: { guidanceConsent: boolean } }>("/internal/v1/guidance/consent-status"),
  });
}
