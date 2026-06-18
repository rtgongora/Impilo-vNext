import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useEncounterImagingLinks(encounterId?: string | null) {
  return useQuery<ApiResponse<unknown>>({
    queryKey: ["encounter-imaging-links", encounterId ?? null],
    queryFn: () => apiClient.get<ApiResponse<unknown>>(`/internal/v1/encounters/${encounterId}/imaging-links`),
    enabled: !!encounterId,
  });
}

export function useCreateEncounterImagingLink(encounterId: string) {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<unknown>, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/encounters/${encounterId}/imaging-links`, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["encounter-imaging-links", encounterId] });
    },
  });
}
