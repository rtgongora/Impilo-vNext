/**
 * Experience UI — Imaging / PACS (Clinical Plane) Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type ImagingStudiesResponse = ApiResponse<unknown>;
type ImagingStudyResponse = ApiResponse<unknown>;
type ImagingSeriesResponse = ApiResponse<unknown>;

function chartPatientQuery(chartPatientCpid?: string | null): string {
  if (!chartPatientCpid) return "";
  const searchParams = new URLSearchParams();
  searchParams.set("chart_patient_cpid", chartPatientCpid);
  return `?${searchParams.toString()}`;
}

export function useImagingStudies(patientCpid?: string) {
  return useQuery<ImagingStudiesResponse>({
    queryKey: ["imaging-studies", patientCpid ?? null],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (patientCpid) searchParams.set("patient_cpid", patientCpid);
      const qs = searchParams.toString();
      const path = `/internal/v1/imaging/studies${qs ? `?${qs}` : ""}`;
      return apiClient.get<ImagingStudiesResponse>(path);
    },
  });
}

export function useImagingStudy(studyId?: string | number | null, chartPatientCpid?: string | null) {
  return useQuery<ImagingStudyResponse>({
    queryKey: ["imaging-study", studyId, chartPatientCpid ?? null],
    queryFn: () =>
      apiClient.get<ImagingStudyResponse>(
        `/internal/v1/imaging/studies/${studyId}${chartPatientQuery(chartPatientCpid)}`,
      ),
    enabled: !!studyId,
  });
}

export function useImagingSeries(studyId?: string | number | null, chartPatientCpid?: string | null) {
  return useQuery<ImagingSeriesResponse>({
    queryKey: ["imaging-series", studyId, chartPatientCpid ?? null],
    queryFn: () =>
      apiClient.get<ImagingSeriesResponse>(
        `/internal/v1/imaging/studies/${studyId}/series${chartPatientQuery(chartPatientCpid)}`,
      ),
    enabled: !!studyId,
  });
}

export interface CorrelateStudyPayload {
  oros_order_id?: string;
  orosOrderId?: string;
  [key: string]: unknown;
}

export function useCorrelateStudy() {
  const queryClient = useQueryClient();

  return useMutation<ImagingStudyResponse, unknown, { studyId: string | number; body?: CorrelateStudyPayload }>({
    mutationFn: ({ studyId, body }) =>
      apiClient.post<ImagingStudyResponse>(`/internal/v1/imaging/studies/${studyId}/correlate`, body),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["imaging-studies"] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-study", variables.studyId] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-series", variables.studyId] });
    },
  });
}

export function useSyncImagingHierarchy(chartPatientCpid?: string | null) {
  const queryClient = useQueryClient();
  return useMutation<ImagingStudyResponse, unknown, { studyId: string | number }>({
    mutationFn: ({ studyId }) =>
      apiClient.post<ImagingStudyResponse>(
        `/internal/v1/imaging/studies/${studyId}/sync-hierarchy${chartPatientQuery(chartPatientCpid)}`,
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["imaging-study", variables.studyId] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-series", variables.studyId] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-studies"] });
    },
  });
}

export function useLaunchImagingViewer(chartPatientCpid?: string | null) {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { studyId: string | number; body?: Record<string, unknown> }>({
    mutationFn: ({ studyId, body }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/imaging/studies/${studyId}/viewer-sessions${chartPatientQuery(chartPatientCpid)}`,
        body ?? {},
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["imaging-study", variables.studyId] });
    },
  });
}

export function useImagingViewerLaunchContext(
  studyId?: string | number | null,
  options?: { chartPatientCpid?: string | null; viewerType?: string | null },
) {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["imaging-viewer-launch-context", studyId, options?.chartPatientCpid ?? null, options?.viewerType ?? null],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (options?.chartPatientCpid) searchParams.set("chart_patient_cpid", options.chartPatientCpid);
      if (options?.viewerType) searchParams.set("viewerType", options.viewerType);
      const qs = searchParams.toString();
      const path = `/internal/v1/imaging/studies/${studyId}/viewer-launch-context${qs ? `?${qs}` : ""}`;
      return apiClient.get<ApiResponse<unknown>>(path);
    },
    enabled: !!studyId,
  });
}

export function useImagingOpsStatus() {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["imaging-ops-status"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/imaging/ops/status"),
    staleTime: 15_000,
  });
}

export function useImagingOpsUnmatchedStudies() {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["imaging-ops-unmatched"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/imaging/ops/unmatched-studies"),
    staleTime: 15_000,
  });
}

export function useImagingOpsFailedCorrelations() {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["imaging-ops-failed-correlations"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/imaging/ops/failed-correlations"),
    staleTime: 15_000,
  });
}

export function useImagingOpsFailedWritebacks() {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["imaging-ops-failed-writebacks"],
    queryFn: () => apiClient.get<ApiResponse<unknown>>("/internal/v1/imaging/ops/failed-writebacks"),
    staleTime: 15_000,
  });
}

export function useRetryImagingWriteback() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, { outboxId: number }>({
    mutationFn: ({ outboxId }) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/imaging/ops/failed-writebacks/${outboxId}/retry`, {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["imaging-ops-status"] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-ops-failed-writebacks"] });
    },
  });
}

export function useRetryAllImagingWritebacks() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown>({
    mutationFn: () => apiClient.post<ApiResponse<unknown>>("/internal/v1/imaging/ops/failed-writebacks/retry-all", {}),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["imaging-ops-status"] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-ops-failed-writebacks"] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-ops-failed-correlations"] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-ops-unmatched"] });
    },
  });
}

export interface ImagingAnnotationPayload {
  annotationType?: string;
  note?: string;
  seriesId?: number | null;
  instanceId?: number | null;
  measurement?: Record<string, unknown>;
  body?: Record<string, unknown>;
}

export function useImagingAnnotations(
  studyId?: string | number | null,
  chartPatientCpid?: string | null,
) {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["imaging-annotations", studyId, chartPatientCpid ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/imaging/studies/${studyId}/annotations${chartPatientQuery(chartPatientCpid)}`,
      ),
    enabled: !!studyId,
  });
}

export function useCreateImagingAnnotation(chartPatientCpid?: string | null) {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<unknown>,
    unknown,
    { studyId: string | number; body: ImagingAnnotationPayload }
  >({
    mutationFn: ({ studyId, body }) =>
      apiClient.post<ApiResponse<unknown>>(
        `/internal/v1/imaging/studies/${studyId}/annotations${chartPatientQuery(chartPatientCpid)}`,
        body,
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({
        queryKey: ["imaging-annotations", variables.studyId, chartPatientCpid ?? null],
      });
    },
  });
}
