/**
 * Experience UI — Imaging / PACS (Clinical Plane) Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type ImagingStudiesResponse = ApiResponse<unknown>;
type ImagingStudyResponse = ApiResponse<unknown>;
type ImagingSeriesResponse = ApiResponse<unknown>;

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

export function useImagingStudy(studyId?: string | number | null) {
  return useQuery<ImagingStudyResponse>({
    queryKey: ["imaging-study", studyId],
    queryFn: () =>
      apiClient.get<ImagingStudyResponse>(`/internal/v1/imaging/studies/${studyId}`),
    enabled: !!studyId,
  });
}

export function useImagingSeries(studyId?: string | number | null) {
  return useQuery<ImagingSeriesResponse>({
    queryKey: ["imaging-series", studyId],
    queryFn: () =>
      apiClient.get<ImagingSeriesResponse>(`/internal/v1/imaging/studies/${studyId}/series`),
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
      apiClient.post<ImagingStudyResponse>(
        `/internal/v1/imaging/studies/${studyId}/correlate`,
        body,
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ["imaging-studies"] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-study", variables.studyId] });
      void queryClient.invalidateQueries({ queryKey: ["imaging-series", variables.studyId] });
    },
  });
}
