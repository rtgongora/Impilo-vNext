/**
 * Observation chart entries — CareEmergencyInpatientController.
 * GET  /internal/v1/observations?patientId=
 * POST /internal/v1/observations — parameters sent as JSON string for BFF jsonb insert.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type ObservationRow = Record<string, unknown> & {
  id?: string;
  chart_type?: string;
  parameters?: unknown;
  recorded_at?: string;
  recorded_by?: string | null;
};

type ObsListResponse = { data: ObservationRow[] };

export function useObservations(patientId: string | undefined) {
  return useQuery<ObsListResponse>({
    queryKey: ["observations", patientId],
    queryFn: () =>
      apiClient.get<ObsListResponse>(`/internal/v1/observations?patientId=${encodeURIComponent(patientId!)}`),
    enabled: Boolean(patientId),
    staleTime: 25_000,
  });
}

export function useRecordObservation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      patientId: string;
      encounterId?: string | null;
      chartType: string;
      /** JSON string (valid JSON) for BFF `parameters` column. */
      parametersJson: string;
      recordedBy: string;
    }) =>
      apiClient.post<{ data: { id: string } }>("/internal/v1/observations", {
        patientId: body.patientId,
        encounterId: body.encounterId ?? null,
        chartType: body.chartType,
        parameters: body.parametersJson,
        recordedBy: body.recordedBy,
      }),
    onSuccess: (_, v) => {
      queryClient.invalidateQueries({ queryKey: ["observations", v.patientId] });
    },
  });
}
