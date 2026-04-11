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
