/**
 * Maternity partograph session API (Experience BFF).
 * POST   /internal/v1/maternity/partograph/sessions
 * GET    /internal/v1/maternity/partograph/sessions/active?patientId=&encounterId=
 * GET    /internal/v1/maternity/partograph/sessions/{sessionId}
 * POST   /internal/v1/maternity/partograph/sessions/{sessionId}/points
 * PATCH  /internal/v1/maternity/partograph/sessions/{sessionId}/close
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface PartographPoint {
  id?: string;
  session_id?: string;
  patient_id?: string;
  encounter_id?: string | null;
  observed_at?: string;
  recorded_by?: string | null;
  fetal_heart_rate_bpm?: number | null;
  contraction_frequency_10min?: number | null;
  contraction_duration_sec?: number | null;
  cervical_dilation_cm?: number | string | null;
  fetal_descent_fifths?: number | null;
  maternal_pulse_bpm?: number | null;
  systolic_bp?: number | null;
  diastolic_bp?: number | null;
  temperature_c?: number | string | null;
  liquor?: string | null;
  moulding?: string | null;
  caput?: string | null;
  oxytocin_rate_miu_min?: number | string | null;
  urine_volume_ml?: number | null;
  urine_protein?: string | null;
  urine_acetone?: string | null;
  maternal_condition?: string | null;
  notes?: string | null;
  derived_stage?: string | null;
  alert_flags?: string[];
}

export interface PartographSessionSummary {
  point_count?: number;
  latest_observed_at?: string | null;
  latest_alert_flags?: string[];
  latest_cervical_dilation_cm?: number | string | null;
  latest_fetal_heart_rate_bpm?: number | null;
}

export interface PartographSessionResource {
  id?: string;
  patient_id?: string;
  encounter_id?: string | null;
  labour_phase?: string;
  status?: string;
  started_at?: string;
  started_by?: string | null;
  closed_at?: string | null;
  closed_by?: string | null;
  outcome?: string | null;
  summary_notes?: string | null;
  points?: PartographPoint[];
  summary?: PartographSessionSummary;
}

type ActiveSessionResponse = ApiResponse<PartographSessionResource | null>;

export function useActivePartographSession(
  patientId: string | undefined,
  encounterId: string | null | undefined,
) {
  const enc = encounterId && encounterId.length > 0 ? encounterId : null;
  return useQuery<ActiveSessionResponse>({
    queryKey: ["partograph-active", patientId, enc],
    queryFn: () => {
      const q = new URLSearchParams({ patientId: patientId! });
      if (enc) q.set("encounterId", enc);
      return apiClient.get<ActiveSessionResponse>(
        `/internal/v1/maternity/partograph/sessions/active?${q.toString()}`,
      );
    },
    enabled: Boolean(patientId),
    staleTime: 15_000,
  });
}

export interface CreatePartographSessionPayload {
  patientId: string;
  encounterId?: string | null;
  labourPhase?: string;
  startedAt?: string | null;
  startedBy?: string | null;
  outcome?: string | null;
  summaryNotes?: string | null;
}

export interface RecordPartographPointPayload {
  sessionId: string;
  observedAt?: string | null;
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

export interface ClosePartographSessionPayload {
  sessionId: string;
  closedAt?: string | null;
  closedBy?: string | null;
  outcome?: string | null;
  summaryNotes?: string | null;
}

function invalidatePartographQueries(qc: ReturnType<typeof useQueryClient>, patientId: string) {
  qc.invalidateQueries({ queryKey: ["partograph-active", patientId], exact: false });
}

export function useCreatePartographSession() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<PartographSessionResource>, unknown, CreatePartographSessionPayload>({
    mutationFn: (p) =>
      apiClient.post<ApiResponse<PartographSessionResource>>(
        "/internal/v1/maternity/partograph/sessions",
        {
          patientId: p.patientId,
          encounterId: p.encounterId ?? null,
          labourPhase: p.labourPhase ?? "ACTIVE_LABOUR",
          startedAt: p.startedAt ?? null,
          startedBy: p.startedBy ?? null,
          outcome: p.outcome ?? null,
          summaryNotes: p.summaryNotes ?? null,
        },
      ),
    onSuccess: (_, p) => invalidatePartographQueries(qc, p.patientId),
  });
}

export function useRecordPartographPoint() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<PartographPoint>, unknown, RecordPartographPointPayload & { patientId: string }>({
    mutationFn: (p) =>
      apiClient.post<ApiResponse<PartographPoint>>(
        `/internal/v1/maternity/partograph/sessions/${p.sessionId}/points`,
        {
          observedAt: p.observedAt ?? null,
          recordedBy: p.recordedBy ?? null,
          fetalHeartRateBpm: p.fetalHeartRateBpm ?? null,
          contractionFrequency10Min: p.contractionFrequency10Min ?? null,
          contractionDurationSec: p.contractionDurationSec ?? null,
          cervicalDilationCm: p.cervicalDilationCm ?? null,
          fetalDescentFifths: p.fetalDescentFifths ?? null,
          maternalPulseBpm: p.maternalPulseBpm ?? null,
          systolicBp: p.systolicBp ?? null,
          diastolicBp: p.diastolicBp ?? null,
          temperatureC: p.temperatureC ?? null,
          liquor: p.liquor ?? null,
          moulding: p.moulding ?? null,
          caput: p.caput ?? null,
          oxytocinRateMiuMin: p.oxytocinRateMiuMin ?? null,
          urineVolumeMl: p.urineVolumeMl ?? null,
          urineProtein: p.urineProtein ?? null,
          urineAcetone: p.urineAcetone ?? null,
          maternalCondition: p.maternalCondition ?? null,
          notes: p.notes ?? null,
        },
      ),
    onSuccess: (_, p) => invalidatePartographQueries(qc, p.patientId),
  });
}

export function useClosePartographSession() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<PartographSessionResource>, unknown, ClosePartographSessionPayload & { patientId: string }>({
    mutationFn: (p) =>
      apiClient.patch<ApiResponse<PartographSessionResource>>(
        `/internal/v1/maternity/partograph/sessions/${p.sessionId}/close`,
        {
          closedAt: p.closedAt ?? null,
          closedBy: p.closedBy ?? null,
          outcome: p.outcome ?? null,
          summaryNotes: p.summaryNotes ?? null,
        },
      ),
    onSuccess: (_, p) => invalidatePartographQueries(qc, p.patientId),
  });
}
