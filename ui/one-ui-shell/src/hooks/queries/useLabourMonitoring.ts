/**
 * Labour monitoring / partograph slice.
 * GET  /internal/v1/labour-monitoring?patientId=&encounterId=
 * POST /internal/v1/labour-monitoring
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface LabourMonitoringRow {
  id?: string;
  patient_id?: string;
  encounter_id?: string | null;
  phase?: string;
  recorded_at?: string;
  recorded_by?: string | null;
  fetal_heart_rate_bpm?: number | null;
  contraction_frequency_10min?: number | null;
  contraction_duration_sec?: number | null;
  cervical_dilation_cm?: number | null;
  fetal_descent_fifths?: number | null;
  maternal_pulse_bpm?: number | null;
  systolic_bp?: number | null;
  diastolic_bp?: number | null;
  temperature_c?: number | null;
  liquor?: string | null;
  moulding?: string | null;
  caput?: string | null;
  oxytocin_rate_miu_min?: number | null;
  urine_volume_ml?: number | null;
  urine_protein?: string | null;
  urine_acetone?: string | null;
  maternal_condition?: string | null;
  notes?: string | null;
  derived_stage?: string | null;
  alert_flags?: string[];
}

export interface RecordLabourMonitoringPayload {
  patientId: string;
  encounterId?: string | null;
  phase?: string;
  recordedAt?: string | null;
  recordedBy?: string | null;
  fetalHeartRateBpm?: number | null;
  contractionFrequency10Min?: number | null;
  contractionDurationSec?: number | null;
  cervicalDilationCm?: number | null;
  fetalDescentFifths?: number | null;
  maternalPulseBpm?: number | null;
  systolicBp?: number | null;
  diastolicBp?: number | null;
  temperatureC?: number | null;
  liquor?: string | null;
  moulding?: string | null;
  caput?: string | null;
  oxytocinRateMiuMin?: number | null;
  urineVolumeMl?: number | null;
  urineProtein?: string | null;
  urineAcetone?: string | null;
  maternalCondition?: string | null;
  notes?: string | null;
}

type LabourMonitoringResponse = ApiResponse<LabourMonitoringRow[]>;

export function useLabourMonitoring(patientId: string | undefined, encounterId?: string | null) {
  return useQuery<LabourMonitoringResponse>({
    queryKey: ["labour-monitoring", patientId, encounterId ?? null],
    queryFn: () => {
      const query = new URLSearchParams({ patientId: patientId! });
      if (encounterId) {
        query.set("encounterId", encounterId);
      }
      return apiClient.get<LabourMonitoringResponse>(`/internal/v1/labour-monitoring?${query.toString()}`);
    },
    enabled: Boolean(patientId),
    staleTime: 25_000,
  });
}

export function useRecordLabourMonitoring() {
  const queryClient = useQueryClient();
  return useMutation<ApiResponse<LabourMonitoringRow>, unknown, RecordLabourMonitoringPayload>({
    mutationFn: (payload) =>
      apiClient.post<ApiResponse<LabourMonitoringRow>>("/internal/v1/labour-monitoring", {
        patientId: payload.patientId,
        encounterId: payload.encounterId ?? null,
        phase: payload.phase ?? "ACTIVE_LABOUR",
        recordedAt: payload.recordedAt ?? null,
        recordedBy: payload.recordedBy ?? null,
        fetalHeartRateBpm: payload.fetalHeartRateBpm ?? null,
        contractionFrequency10Min: payload.contractionFrequency10Min ?? null,
        contractionDurationSec: payload.contractionDurationSec ?? null,
        cervicalDilationCm: payload.cervicalDilationCm ?? null,
        fetalDescentFifths: payload.fetalDescentFifths ?? null,
        maternalPulseBpm: payload.maternalPulseBpm ?? null,
        systolicBp: payload.systolicBp ?? null,
        diastolicBp: payload.diastolicBp ?? null,
        temperatureC: payload.temperatureC ?? null,
        liquor: payload.liquor ?? null,
        moulding: payload.moulding ?? null,
        caput: payload.caput ?? null,
        oxytocinRateMiuMin: payload.oxytocinRateMiuMin ?? null,
        urineVolumeMl: payload.urineVolumeMl ?? null,
        urineProtein: payload.urineProtein ?? null,
        urineAcetone: payload.urineAcetone ?? null,
        maternalCondition: payload.maternalCondition ?? null,
        notes: payload.notes ?? null,
      }),
    onSuccess: (_, payload) => {
      queryClient.invalidateQueries({
        queryKey: ["labour-monitoring", payload.patientId, payload.encounterId ?? null],
      });
      queryClient.invalidateQueries({
        queryKey: ["labour-monitoring", payload.patientId],
        exact: false,
      });
    },
  });
}
