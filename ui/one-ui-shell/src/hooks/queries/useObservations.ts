/**
 * Ward chart entries — NOT the clinical observation registry.
 *
 * GET  /internal/v1/observations?patientId=
 * POST /internal/v1/observations — parameters sent as JSON string for BFF jsonb insert.
 *
 * **Read this before using this hook for anything clinical.** The endpoint is named
 * `/observations` but inpatient-service's handler delegates straight to `recordChartEntry`, so
 * this is `inpatient.clinical_chart_entry` — the same store behind `obs-chart`, `fluid-balance`,
 * `drug-chart` and the rest. Its payload is an opaque jsonb bag: no code, no units, no
 * data-absent reason, no continuum anchor, and no path to the shared record.
 *
 * The coded clinical observation — one code, one value *or* a stated reason there is none,
 * journey-anchored and bound for BUTANO — is `pct.pct_observations` in pct-service, and it is a
 * different store with a different purpose. Do not record a coded clinical fact through this hook
 * and do not read one from it; a value written here is invisible to the SHR and to every engine
 * that addresses observations by code.
 *
 * Ruling and the outstanding derivation gap:
 * `docs/clinical/paediatric-domain-pack/observations-system-of-record-ruling.md`.
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
