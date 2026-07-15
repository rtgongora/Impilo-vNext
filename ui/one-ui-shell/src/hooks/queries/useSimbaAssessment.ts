/**
 * Experience UI — SIMBA wellness assessment + risk hooks (Part-1 core completion).
 *
 * Served by the Simba sovereign (port 8125) via the experience-bff, which reverse-proxies
 * /internal/v1/wellness/** transparently. Endpoints:
 *   POST   /internal/v1/wellness/assessments           start/resume a draft
 *   GET    /internal/v1/wellness/assessments/{id}       header + responses
 *   GET    /internal/v1/wellness/assessments?person_cpid=
 *   PATCH  /internal/v1/wellness/assessments/{id}/step  save a wizard step
 *   POST   /internal/v1/wellness/assessments/{id}/complete  score + next-actions
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type AssessmentSection = "LIFESTYLE" | "MEASUREMENT" | "RISK_QUESTION";

export interface AssessmentResponseInput {
  section: AssessmentSection;
  question_code: string;
  value_text?: string | null;
  value_numeric?: number | null;
  unit?: string | null;
}

export interface AssessmentHeader {
  assessmentId: string;
  personCpid: string;
  templateCode: string;
  status: "DRAFT" | "IN_PROGRESS" | "COMPLETED" | "ABANDONED";
  currentStep?: string | null;
  consentCaptured: boolean;
  riskBand?: string | null;
  riskScore?: number | null;
}

export interface CompletionResult {
  data: AssessmentHeader;
  risk_band: string;
  care_linkage_routed: boolean;
  next_actions: string[];
}

/** Human band label + whether the band warrants a care/escalation emphasis in the UI. */
export function summarizeBand(band?: string | null): {
  label: string;
  tone: "positive" | "info" | "attention" | "urgent";
  escalated: boolean;
} {
  switch (band) {
    case "LOW":
      return { label: "Low risk", tone: "positive", escalated: false };
    case "MODERATE":
      return { label: "Moderate risk", tone: "info", escalated: false };
    case "HIGH":
      return { label: "High risk", tone: "attention", escalated: true };
    case "NEEDS_CLINICAL_REVIEW":
      return { label: "Needs clinical review", tone: "attention", escalated: true };
    case "URGENT_RED_FLAG":
      return { label: "Urgent", tone: "urgent", escalated: true };
    default:
      return { label: band ?? "Not assessed", tone: "info", escalated: false };
  }
}

export function useAssessment(assessmentId?: string | null) {
  return useQuery<ApiResponse<AssessmentHeader> & { responses?: unknown }>({
    queryKey: ["wellness-assessment", assessmentId ?? null],
    queryFn: () =>
      apiClient.get(`/internal/v1/wellness/assessments/${assessmentId}`),
    enabled: !!assessmentId,
  });
}

export function useAssessmentsForPerson(cpid?: string | null) {
  return useQuery<ApiResponse<AssessmentHeader[]>>({
    queryKey: ["wellness-assessments", cpid ?? null],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/wellness/assessments?person_cpid=${encodeURIComponent(cpid ?? "")}`,
      ),
    enabled: !!cpid,
  });
}

export function useStartAssessment() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<AssessmentHeader>,
    Error,
    { person_cpid: string; template_code?: string }
  >({
    mutationFn: (body) =>
      apiClient.post("/internal/v1/wellness/assessments", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["wellness-assessments"] });
    },
  });
}

export function useSaveAssessmentStep() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<AssessmentHeader>,
    Error,
    {
      assessmentId: string;
      step?: string;
      consent_captured?: boolean;
      responses?: AssessmentResponseInput[];
    }
  >({
    mutationFn: ({ assessmentId, ...body }) =>
      apiClient.patch(
        `/internal/v1/wellness/assessments/${assessmentId}/step`,
        body,
      ),
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({
        queryKey: ["wellness-assessment", vars.assessmentId],
      });
    },
  });
}

export function useCompleteAssessment() {
  const qc = useQueryClient();
  return useMutation<CompletionResult, Error, { assessmentId: string }>({
    mutationFn: ({ assessmentId }) =>
      apiClient.post(
        `/internal/v1/wellness/assessments/${assessmentId}/complete`,
        {},
      ),
    onSuccess: (_res, vars) => {
      void qc.invalidateQueries({
        queryKey: ["wellness-assessment", vars.assessmentId],
      });
      void qc.invalidateQueries({ queryKey: ["wellness-assessments"] });
      void qc.invalidateQueries({ queryKey: ["wellness-home-overview"] });
      void qc.invalidateQueries({ queryKey: ["wellness-timeline"] });
    },
  });
}
