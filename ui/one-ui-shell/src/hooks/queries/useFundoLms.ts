"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type GenericData<T = unknown> = { data: T };

export interface FundoSubjectRef {
  subjectType: string;
  subjectId: string;
}

export interface FundoMyLearningKpis {
  inProgress: number;
  required: number;
  overdue: number;
  cpdEligible: number;
}

export interface FundoLanguageOption {
  code: string;
  label: string;
  nativeLabel?: string | null;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function asNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim().length > 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function getObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

/**
 * Derives stable KPI counters from the v11 my-learning payload.
 * Supports either array-based payloads or explicit numeric aggregates.
 */
export function summarizeFundoMyLearning(payload: unknown): FundoMyLearningKpis {
  const root = getObject(payload);

  const inProgressArr = asArray(root.inProgress);
  const requiredArr = asArray(root.required);
  const overdueArr = asArray(root.overdue);
  const cpdArr = asArray(root.cpdEligibleCompletions);

  const inProgress =
    inProgressArr.length > 0 ? inProgressArr.length : (asNumber(root.inProgressCount) ?? 0);
  const required =
    requiredArr.length > 0 ? requiredArr.length : (asNumber(root.requiredCount) ?? overdueArr.length);
  const overdue =
    overdueArr.length > 0 ? overdueArr.length : (asNumber(root.overdueCount) ?? 0);
  const cpdEligible =
    cpdArr.length > 0 ? cpdArr.length : (asNumber(root.cpdEligibleCount) ?? 0);

  return { inProgress, required, overdue, cpdEligible };
}

function buildQuery(params: Record<string, string | number | boolean | undefined>) {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    qs.set(key, String(value));
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
}

export function useFundoMyLearning(subject?: FundoSubjectRef, enabled = true) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "my-learning", subject],
    enabled: enabled && Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get<GenericData<Record<string, unknown>>>(
        `/internal/v1/learning/v11/my-learning?subjectType=${encodeURIComponent(subject!.subjectType)}&subjectId=${encodeURIComponent(subject!.subjectId)}`,
      ),
  });
}

export function useFundoLanguageOptions() {
  return useQuery<GenericData<{ items: FundoLanguageOption[] }>>({
    queryKey: ["fundo", "metadata", "languages"],
    queryFn: () => apiClient.get("/internal/v1/learning/v11/metadata/languages"),
  });
}

export function useFundoEnrolments(subject?: FundoSubjectRef) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "enrolments", subject],
    enabled: Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/enrolments?subjectType=${encodeURIComponent(subject!.subjectType)}&subjectId=${encodeURIComponent(subject!.subjectId)}&limit=100`,
      ),
  });
}

export function useFundoEnrolment(enrolmentId?: string) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "enrolment", enrolmentId],
    enabled: Boolean(enrolmentId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/enrolments/${encodeURIComponent(enrolmentId!)}`),
  });
}

export function useFundoEnrolmentProgress(enrolmentId?: string) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "enrolment-progress", enrolmentId],
    enabled: Boolean(enrolmentId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/enrolments/${encodeURIComponent(enrolmentId!)}/progress`),
  });
}

export function useFundoPathways(status = "PUBLISHED") {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "pathways", status],
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/pathways?status=${encodeURIComponent(status)}&limit=100`,
      ),
  });
}

export function useFundoPathway(pathwayId?: string) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "pathway", pathwayId],
    enabled: Boolean(pathwayId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/pathways/${encodeURIComponent(pathwayId!)}`),
  });
}

export function useFundoCourseAssessments(courseId?: string) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "course-assessments", courseId],
    enabled: Boolean(courseId),
    queryFn: () =>
      apiClient.get(`/internal/v1/learning/v11/courses/${encodeURIComponent(courseId!)}/assessments`),
  });
}

export function useFundoAssessment(assessmentId?: string) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "assessment", assessmentId],
    enabled: Boolean(assessmentId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/assessments/${encodeURIComponent(assessmentId!)}`),
  });
}

export function useFundoAssessmentAttempts(assessmentId?: string, subject?: FundoSubjectRef) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "assessment-attempts", assessmentId, subject],
    enabled: Boolean(assessmentId && subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/assessments/${encodeURIComponent(assessmentId!)}/attempts?subjectType=${encodeURIComponent(subject!.subjectType)}&subjectId=${encodeURIComponent(subject!.subjectId)}`,
      ),
  });
}

export function useFundoAttempt(attemptId?: string) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "attempt", attemptId],
    enabled: Boolean(attemptId),
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/attempts/${encodeURIComponent(attemptId!)}`),
  });
}

export function useFundoPendingReviews(assessmentId?: string, limit = 50) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "pending-reviews", assessmentId, limit],
    enabled: Boolean(assessmentId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/assessments/${encodeURIComponent(assessmentId!)}/pending-reviews?limit=${limit}`,
      ),
  });
}

export function useFundoCertificates(subject?: FundoSubjectRef) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "certificates", subject],
    enabled: Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/certificates?subjectType=${encodeURIComponent(subject!.subjectType)}&subjectId=${encodeURIComponent(subject!.subjectId)}`,
      ),
  });
}

export function useFundoCertificate(certificateId?: string) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "certificate", certificateId],
    enabled: Boolean(certificateId),
    queryFn: () =>
      apiClient.get(`/internal/v1/learning/v11/certificates/${encodeURIComponent(certificateId!)}`),
  });
}

export function useFundoCpdEvidence(subject?: FundoSubjectRef) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "cpd", subject],
    enabled: Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/cpd/evidence?subjectType=${encodeURIComponent(subject!.subjectType)}&subjectId=${encodeURIComponent(subject!.subjectId)}`,
      ),
  });
}

export function useFundoReportsOverview() {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "reports", "overview"],
    queryFn: () => apiClient.get("/internal/v1/learning/v11/reports/overview"),
  });
}

export function useFundoReport(path: "cohort-completions" | "course-completions" | "overdue-learning" | "assessment-performance") {
  return useFundoReportFiltered(path, {});
}

export function useFundoReportFiltered(
  path: "cohort-completions" | "course-completions" | "overdue-learning" | "assessment-performance",
  filters: Record<string, string | number | undefined>,
) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "report", path, filters],
    queryFn: () => apiClient.get(`/internal/v1/learning/v11/reports/${path}${buildQuery(filters)}`),
  });
}

export function useFundoLearningRecord(subject?: FundoSubjectRef) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "learning-record", subject],
    enabled: Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/subjects/${encodeURIComponent(subject!.subjectType)}/${encodeURIComponent(subject!.subjectId)}/record`,
      ),
  });
}

export function useCreateFundoEnrolment() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/learning/v11/enrolments", body),
  });
}

export function useStartFundoEnrolment() {
  return useMutation({
    mutationFn: (enrolmentId: string) =>
      apiClient.post(`/internal/v1/learning/v11/enrolments/${encodeURIComponent(enrolmentId)}/start`, {}),
  });
}

export function useOpenFundoLesson() {
  return useMutation({
    mutationFn: ({ lessonId, enrolmentId }: { lessonId: string; enrolmentId: string }) =>
      apiClient.post(`/internal/v1/learning/v11/lessons/${encodeURIComponent(lessonId)}/open`, { enrolmentId }),
  });
}

export function useRecordFundoProgress() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/progress", body),
  });
}

export function useSubmitFundoAttempt() {
  return useMutation({
    mutationFn: ({ assessmentId, body }: { assessmentId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/assessments/${encodeURIComponent(assessmentId)}/attempts`, body),
  });
}

export function useManualReviewFundoAttempt() {
  return useMutation({
    mutationFn: ({ attemptId, body }: { attemptId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/attempts/${encodeURIComponent(attemptId)}/manual-review`, body),
  });
}

export function useIssueFundoCertificate() {
  return useMutation({
    mutationFn: (enrolmentId: string) =>
      apiClient.post(`/internal/v1/learning/v11/enrolments/${encodeURIComponent(enrolmentId)}/certificate`, {}),
  });
}

export type CertificateValidityState =
  | "ACTIVE"
  | "EXPIRING_SOON"
  | "EXPIRED"
  | "REVOKED"
  | "NO_EXPIRY";

export interface CertificateValidity {
  state: CertificateValidityState;
  daysRemaining: number | null;
  label: string;
  needsRenewal: boolean;
}

/**
 * Derive a certificate's renewal/refresher/expiry status from its raw fields.
 * Mirrors the backend lifecycle sweep semantics (refresher lead window defaults
 * to 30 days). Pure + exported so it is unit-testable.
 */
export function summarizeCertificateValidity(
  cert: Record<string, unknown>,
  now: Date = new Date(),
  refresherLeadDays = 30,
): CertificateValidity {
  const status = String(cert.status ?? "").toUpperCase();
  if (status === "REVOKED") {
    return { state: "REVOKED", daysRemaining: null, label: "Revoked", needsRenewal: false };
  }
  const validUntilRaw = cert.validUntil;
  if (!validUntilRaw) {
    if (status === "EXPIRED") {
      return { state: "EXPIRED", daysRemaining: null, label: "Expired", needsRenewal: true };
    }
    return { state: "NO_EXPIRY", daysRemaining: null, label: "No expiry", needsRenewal: false };
  }
  const validUntil = new Date(String(validUntilRaw));
  if (Number.isNaN(validUntil.getTime())) {
    return { state: "NO_EXPIRY", daysRemaining: null, label: "No expiry", needsRenewal: false };
  }
  const msPerDay = 24 * 60 * 60 * 1000;
  const daysRemaining = Math.ceil((validUntil.getTime() - now.getTime()) / msPerDay);
  if (status === "EXPIRED" || daysRemaining < 0) {
    return { state: "EXPIRED", daysRemaining, label: "Expired", needsRenewal: true };
  }
  if (daysRemaining <= refresherLeadDays) {
    return {
      state: "EXPIRING_SOON",
      daysRemaining,
      label: `Expires in ${daysRemaining} day${daysRemaining === 1 ? "" : "s"}`,
      needsRenewal: true,
    };
  }
  return { state: "ACTIVE", daysRemaining, label: "Active", needsRenewal: false };
}
