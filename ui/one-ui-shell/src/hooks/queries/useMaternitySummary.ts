/**
 * Maternity summary — web hook.
 *
 * Backend: experience-bff `GET /internal/v1/maternity/summary?patientId=`, a pure proxy to PCT's
 * `MaternityController#summary` (see `MaternityService.summary`). All aggregation lives in PCT; this
 * hook adds nothing.
 *
 * The one rule that matters: a 502 (`error: "maternity_summary_unavailable"`) means the summary could
 * not be read — it is never rendered as "no maternity record" or an empty chart. See
 * `MaternitySummaryController.java`'s own doc comment: an empty 200 here would be a fabricated
 * finding, so the BFF fails loud instead, and this hook must not paper back over that failure.
 */
"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface MaternitySummaryProgress {
  status: string;
  latest_dilation_cm: number | null;
  expected_dilation_cm: number | null;
  hours_behind_alert_line: number | null;
  outstanding_observations: string[];
  observations: string[];
  recommended_action: string | null;
  content_version: string;
  content_source: string;
}

/** Exactly `MaternityService#summary`'s field set. */
export interface MaternitySummary {
  patient_id: string;
  partograph_active: boolean;
  partograph_session_id?: string;
  progress?: MaternitySummaryProgress;
  ctg_active: boolean;
  ctg_session_id?: string;
  observation_count: number;
  last_observed_at: string | null;
}

export interface MaternitySummaryMeta {
  request_id?: string;
  correlation_id?: string;
}

export interface MaternitySummaryResponse {
  data: MaternitySummary;
  meta: MaternitySummaryMeta;
}

/** The BFF's 502 refusal — flat `{ error, message, meta }`, distinct from the `{ data, meta }` shape. */
export interface MaternitySummaryRefusal {
  status: number;
  error?: string;
  message?: string;
  meta?: MaternitySummaryMeta;
}

export function isMaternitySummaryRefusal(err: unknown): err is MaternitySummaryRefusal {
  return typeof err === "object" && err !== null && "status" in err;
}

/** True for `maternity_summary_unavailable` — the record could not be read, not "no record exists". */
export function isMaternitySummaryUnavailable(err: unknown): boolean {
  return (
    isMaternitySummaryRefusal(err) &&
    (err.status === 502 || err.error === "maternity_summary_unavailable")
  );
}

export function useMaternitySummary(patientId?: string, encounterId?: string) {
  return useQuery({
    queryKey: ["maternity-summary", patientId, encounterId],
    queryFn: () => {
      const qs = new URLSearchParams({ patientId: patientId ?? "" });
      if (encounterId) qs.set("encounterId", encounterId);
      return apiClient.get<MaternitySummaryResponse>(`/internal/v1/maternity/summary?${qs.toString()}`);
    },
    enabled: !!patientId,
    staleTime: 5_000,
  });
}
