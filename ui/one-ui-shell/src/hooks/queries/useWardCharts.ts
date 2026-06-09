/**
 * Ward chart entries — sovereign inpatient-service via BFF.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export function useWardChartActivity(patientId: string) {
  return useQuery({
    queryKey: ["ward-charts", "activity", patientId],
    enabled: !!patientId,
    queryFn: async () => {
      const res = await apiClient.get<{ data: Record<string, string> }>(
        `/internal/v1/ward-charts/activity?patientId=${encodeURIComponent(patientId)}`,
      );
      return res.data ?? {};
    },
  });
}

export function useWardChartEntries(patientId: string, chartType: string) {
  return useQuery({
    queryKey: ["ward-charts", chartType, patientId],
    enabled: !!patientId && !!chartType,
    queryFn: async () => {
      const res = await apiClient.get<{ data: Array<Record<string, unknown>> }>(
        `/internal/v1/ward-charts/${encodeURIComponent(chartType)}/entries?patientId=${encodeURIComponent(patientId)}`,
      );
      return res.data ?? [];
    },
  });
}

export function useRecordWardChartEntry(patientId: string, chartType: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (parameters: Record<string, unknown>) =>
      apiClient.post<ApiResponse<{ id: string }>>(
        `/internal/v1/ward-charts/${encodeURIComponent(chartType)}/entries`,
        { patientId, parameters },
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ward-charts", chartType, patientId] });
      queryClient.invalidateQueries({ queryKey: ["ward-charts", "activity", patientId] });
    },
  });
}
