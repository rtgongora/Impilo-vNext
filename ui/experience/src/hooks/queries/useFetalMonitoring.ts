/**
 * CTG / fetal monitoring (Experience BFF).
 * POST   /internal/v1/maternity/ctg/sessions
 * GET    /internal/v1/maternity/ctg/sessions/active?patientId=&encounterId=
 * GET    /internal/v1/maternity/ctg/sessions/{sessionId}
 * POST   /internal/v1/maternity/ctg/sessions/{sessionId}/chunks
 * GET    /internal/v1/maternity/ctg/sessions/{sessionId}/chunks?channel=&from=&to=
 * POST   /internal/v1/maternity/ctg/sessions/{sessionId}/annotations
 *
 * Session payloads do not embed full trace samples; poll GET …/chunks for plotting.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** Polling intervals — HTTP only; not a websocket stream. */
export const CTG_SESSION_POLL_MS = 8_000;
export const CTG_CHUNKS_POLL_MS = 5_000;

export interface CtgTraceChunk {
  id?: string;
  session_id?: string;
  channel?: string;
  started_at?: string;
  ended_at?: string;
  sample_rate_hz?: number | string | null;
  sample_count?: number | null;
  duration_seconds?: number | string | null;
  unit?: string | null;
  samples?: unknown[];
  samples_json?: unknown;
  captured_by?: string | null;
  device_id?: string | null;
  notes?: string | null;
  alert_flags?: string[];
  min_value?: unknown;
  max_value?: unknown;
  avg_value?: unknown;
}

export interface CtgAnnotation {
  id?: string;
  session_id?: string;
  recorded_at?: string;
  category?: string;
  channel?: string | null;
  sample_offset_sec?: number | string | null;
  value?: string | null;
  severity?: string | null;
  notes?: string | null;
  recorded_by?: string | null;
}

export interface CtgSessionSummary {
  chunk_count?: number;
  annotation_count?: number;
  active_channels?: string[];
  latest_chunk_started_at?: string | null;
  latest_annotation_at?: string | null;
  chunk_summaries?: Array<{
    id?: string;
    channel?: string;
    started_at?: string;
    ended_at?: string;
    sample_count?: number;
    sample_rate_hz?: number | string;
    alert_flags?: string[];
  }>;
}

export interface CtgSessionResource {
  id?: string;
  patient_id?: string;
  encounter_id?: string | null;
  status?: string;
  started_at?: string;
  started_by?: string | null;
  device_id?: string | null;
  monitoring_mode?: string | null;
  baseline_fhr_bpm?: number | null;
  baseline_maternal_hr_bpm?: number | null;
  summary_notes?: string | null;
  annotations?: CtgAnnotation[];
  summary?: CtgSessionSummary;
}

type ActiveCtgResponse = ApiResponse<CtgSessionResource | null>;

export function useActiveCtgSession(patientId: string | undefined, encounterId: string | null | undefined) {
  const enc = encounterId && encounterId.length > 0 ? encounterId : null;
  return useQuery<ActiveCtgResponse>({
    queryKey: ["ctg-active", patientId, enc],
    queryFn: () => {
      const q = new URLSearchParams({ patientId: patientId! });
      if (enc) q.set("encounterId", enc);
      return apiClient.get<ActiveCtgResponse>(`/internal/v1/maternity/ctg/sessions/active?${q.toString()}`);
    },
    enabled: Boolean(patientId),
    staleTime: 0,
    refetchInterval: CTG_SESSION_POLL_MS,
  });
}

export function useCtgTraceChunks(
  sessionId: string | undefined,
  options?: { channel?: string | null; from?: string | null; to?: string | null; enabled?: boolean },
) {
  const ch = options?.channel && options.channel.length > 0 ? options.channel : null;
  const enabled = Boolean(sessionId) && (options?.enabled !== false);
  return useQuery<ApiResponse<CtgTraceChunk[]>>({
    queryKey: ["ctg-chunks", sessionId, ch ?? "all", options?.from ?? "", options?.to ?? ""],
    queryFn: () => {
      const q = new URLSearchParams();
      if (ch) q.set("channel", ch);
      if (options?.from) q.set("from", options.from);
      if (options?.to) q.set("to", options.to);
      const qs = q.toString();
      return apiClient.get<ApiResponse<CtgTraceChunk[]>>(
        `/internal/v1/maternity/ctg/sessions/${sessionId}/chunks${qs ? `?${qs}` : ""}`,
      );
    },
    enabled,
    staleTime: 0,
    refetchInterval: CTG_CHUNKS_POLL_MS,
  });
}

export interface CreateCtgSessionPayload {
  patientId: string;
  encounterId?: string | null;
  startedAt?: string | null;
  startedBy?: string | null;
  deviceId?: string | null;
  monitoringMode?: string | null;
  baselineFhrBpm?: number | null;
  baselineMaternalHrBpm?: number | null;
  summaryNotes?: string | null;
}

export interface AddCtgChunkPayload {
  sessionId: string;
  patientId: string;
  channel: string;
  startedAt?: string | null;
  sampleRateHz: number;
  unit?: string | null;
  samples: number[];
  capturedBy?: string | null;
  deviceId?: string | null;
  notes?: string | null;
}

export interface AddCtgAnnotationPayload {
  sessionId: string;
  patientId: string;
  recordedAt?: string | null;
  category: string;
  channel?: string | null;
  sampleOffsetSec?: number | null;
  value?: string | null;
  severity?: string | null;
  notes?: string | null;
  recordedBy?: string | null;
}

function invalidateCtg(qc: ReturnType<typeof useQueryClient>, patientId: string, sessionId?: string) {
  qc.invalidateQueries({ queryKey: ["ctg-active", patientId], exact: false });
  if (sessionId) {
    qc.invalidateQueries({ queryKey: ["ctg-chunks", sessionId], exact: false });
  }
}

export function useCreateCtgSession() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<CtgSessionResource>, unknown, CreateCtgSessionPayload>({
    mutationFn: (p) =>
      apiClient.post<ApiResponse<CtgSessionResource>>("/internal/v1/maternity/ctg/sessions", {
        patientId: p.patientId,
        encounterId: p.encounterId ?? null,
        startedAt: p.startedAt ?? null,
        startedBy: p.startedBy ?? null,
        deviceId: p.deviceId ?? null,
        monitoringMode: p.monitoringMode ?? "EXTERNAL_CTG",
        baselineFhrBpm: p.baselineFhrBpm ?? null,
        baselineMaternalHrBpm: p.baselineMaternalHrBpm ?? null,
        summaryNotes: p.summaryNotes ?? null,
      }),
    onSuccess: (_, p) => invalidateCtg(qc, p.patientId),
  });
}

export function useAddCtgTraceChunk() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<CtgTraceChunk>, unknown, AddCtgChunkPayload>({
    mutationFn: (p) =>
      apiClient.post<ApiResponse<CtgTraceChunk>>(`/internal/v1/maternity/ctg/sessions/${p.sessionId}/chunks`, {
        channel: p.channel,
        startedAt: p.startedAt ?? null,
        sampleRateHz: p.sampleRateHz,
        unit: p.unit ?? null,
        samples: p.samples,
        capturedBy: p.capturedBy ?? null,
        deviceId: p.deviceId ?? null,
        notes: p.notes ?? null,
      }),
    onSuccess: (_, p) => invalidateCtg(qc, p.patientId, p.sessionId),
  });
}

export function useAddCtgAnnotation() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<CtgAnnotation>, unknown, AddCtgAnnotationPayload>({
    mutationFn: (p) =>
      apiClient.post<ApiResponse<CtgAnnotation>>(`/internal/v1/maternity/ctg/sessions/${p.sessionId}/annotations`, {
        recordedAt: p.recordedAt ?? null,
        category: p.category,
        channel: p.channel ?? null,
        sampleOffsetSec: p.sampleOffsetSec ?? null,
        value: p.value ?? null,
        severity: p.severity ?? null,
        notes: p.notes ?? null,
        recordedBy: p.recordedBy ?? null,
      }),
    onSuccess: (_, p) => invalidateCtg(qc, p.patientId, p.sessionId),
  });
}
