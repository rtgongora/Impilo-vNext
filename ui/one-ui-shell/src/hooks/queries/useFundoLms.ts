"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type GenericData<T = unknown> = { data: T };

export interface FundoSubjectRef {
  subjectType: string;
  subjectId: string;
}

export interface FundoLanguageOption {
  code: string;
  label: string;
  nativeLabel?: string | null;
}

export type FundoReportPath =
  | "cohort-completions"
  | "course-completions"
  | "overdue-learning"
  | "assessment-performance";

function buildQuery(params: Record<string, string | number | boolean | undefined>) {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    qs.set(key, String(value));
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
}

export function useFundoMyLearning(subject?: FundoSubjectRef) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "my-learning", subject],
    enabled: Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
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

export function useFundoProgress(subject?: FundoSubjectRef) {
  return useQuery<GenericData<Record<string, unknown>>>({
    queryKey: ["fundo", "progress", subject],
    enabled: Boolean(subject?.subjectType && subject?.subjectId),
    queryFn: () =>
      apiClient.get(
        `/internal/v1/learning/v11/progress?subjectType=${encodeURIComponent(subject!.subjectType)}&subjectId=${encodeURIComponent(subject!.subjectId)}`,
      ),
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

export function useFundoReport(path: FundoReportPath) {
  return useFundoReportFiltered(path, {});
}

export function useFundoReportFiltered(
  path: FundoReportPath,
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

export function useCancelFundoEnrolment() {
  return useMutation({
    mutationFn: ({ enrolmentId, reason }: { enrolmentId: string; reason?: string }) =>
      apiClient.post(`/internal/v1/learning/v11/enrolments/${encodeURIComponent(enrolmentId)}/cancel`, { reason }),
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

export function useCreateFundoCourse() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/catalog", body),
  });
}

export function useUpdateFundoCourse() {
  return useMutation({
    mutationFn: ({ courseId, body }: { courseId: string; body: Record<string, unknown> }) =>
      apiClient.put(`/internal/v1/learning/v11/catalog/${encodeURIComponent(courseId)}`, body),
  });
}

export function useCreateFundoModule() {
  return useMutation({
    mutationFn: ({ courseId, body }: { courseId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/courses/${encodeURIComponent(courseId)}/modules`, body),
  });
}

export function useUpdateFundoModule() {
  return useMutation({
    mutationFn: ({ moduleId, body }: { moduleId: string; body: Record<string, unknown> }) =>
      apiClient.put(`/internal/v1/learning/v11/modules/${encodeURIComponent(moduleId)}`, body),
  });
}

export function useCreateFundoLesson() {
  return useMutation({
    mutationFn: ({ moduleId, body }: { moduleId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/modules/${encodeURIComponent(moduleId)}/lessons`, body),
  });
}

export function useUpdateFundoLesson() {
  return useMutation({
    mutationFn: ({ lessonId, body }: { lessonId: string; body: Record<string, unknown> }) =>
      apiClient.put(`/internal/v1/learning/v11/lessons/${encodeURIComponent(lessonId)}`, body),
  });
}

export function useCreateFundoPathway() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/pathways", body),
  });
}

export function useUpdateFundoPathway() {
  return useMutation({
    mutationFn: ({ pathwayId, body }: { pathwayId: string; body: Record<string, unknown> }) =>
      apiClient.put(`/internal/v1/learning/v11/pathways/${encodeURIComponent(pathwayId)}`, body),
  });
}

export function useAddFundoPathwayItem() {
  return useMutation({
    mutationFn: ({ pathwayId, body }: { pathwayId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/pathways/${encodeURIComponent(pathwayId)}/items`, body),
  });
}

export function useCreateFundoAssessment() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/learning/v11/assessments", body),
  });
}

export function useUpdateFundoAssessment() {
  return useMutation({
    mutationFn: ({ assessmentId, body }: { assessmentId: string; body: Record<string, unknown> }) =>
      apiClient.put(`/internal/v1/learning/v11/assessments/${encodeURIComponent(assessmentId)}`, body),
  });
}

export function useAddFundoQuestion() {
  return useMutation({
    mutationFn: ({ assessmentId, body }: { assessmentId: string; body: Record<string, unknown> }) =>
      apiClient.post(`/internal/v1/learning/v11/assessments/${encodeURIComponent(assessmentId)}/questions`, body),
  });
}

export function useUpdateFundoQuestion() {
  return useMutation({
    mutationFn: ({
      assessmentId,
      questionId,
      body,
    }: {
      assessmentId: string;
      questionId: string;
      body: Record<string, unknown>;
    }) =>
      apiClient.put(
        `/internal/v1/learning/v11/assessments/${encodeURIComponent(assessmentId)}/questions/${encodeURIComponent(questionId)}`,
        body,
      ),
  });
}
