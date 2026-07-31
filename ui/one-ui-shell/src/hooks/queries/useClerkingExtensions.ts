/**
 * NEW_PCT clerking extensions — BFF /internal/v1/clerking/*
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

function listPath(resource: string, subjectCpid: string) {
  return `/internal/v1/clerking/${resource}?subject_cpid=${encodeURIComponent(subjectCpid)}`;
}

function useClerkingList(resource: string, subjectCpid: string) {
  return useQuery({
    queryKey: ["clerking", resource, subjectCpid],
    queryFn: () =>
      apiClient.get<ApiResponse<Record<string, unknown>[]>>(listPath(resource, subjectCpid)),
    enabled: !!subjectCpid,
  });
}

function useClerkingRecord(resource: string, subjectCpid: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(`/internal/v1/clerking/${resource}`, {
        subject_cpid: subjectCpid,
        ...body,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["clerking", resource, subjectCpid] });
    },
  });
}

export const usePresentingConcerns = (id: string) => useClerkingList("presenting-concerns", id);
export const useRecordPresentingConcern = (id: string) => useClerkingRecord("presenting-concerns", id);
export const useSystemsReviews = (id: string) => useClerkingList("systems-reviews", id);
export const useRecordSystemsReview = (id: string) => useClerkingRecord("systems-reviews", id);
export const usePriorIcuHistory = (id: string) => useClerkingList("prior-icu", id);
export const useRecordPriorIcu = (id: string) => useClerkingRecord("prior-icu", id);
export const useInfectionExposures = (id: string) => useClerkingList("infection-exposures", id);
export const useRecordInfectionExposure = (id: string) => useClerkingRecord("infection-exposures", id);
export const useCognitionMoodScreens = (id: string) => useClerkingList("cognition-mood", id);
export const useRecordCognitionMood = (id: string) => useClerkingRecord("cognition-mood", id);
export const useGeneticRisks = (id: string) => useClerkingList("genetic-risks", id);
export const useRecordGeneticRisk = (id: string) => useClerkingRecord("genetic-risks", id);
export const useSafeguardingDisclosures = (id: string) => useClerkingList("safeguarding", id);
export const useRecordSafeguarding = (id: string) => useClerkingRecord("safeguarding", id);
