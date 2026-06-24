/**
 * Clinical Decision Support hooks.
 *
 * useClinicalCdsAlerts assembles a real patient context from the patient's own records
 * (active conditions, active prescriptions, latest vitals, documented allergies) and POSTs it
 * to the governed clinical rules engine (BFF /internal/v1/clinical/rules/evaluate ->
 * clinical-knowledge-platform-service). The returned alerts are deterministic rule output —
 * never fabricated. When the patient has no triggering data, the engine returns no alerts.
 *
 * useRecordClinicalOverride records a clinician override of a recommendation against its audit
 * trace, completing the governed loop (Nompilo never overrides clinical judgement; overrides
 * are auditable).
 */

import { useMemo } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useConditions } from "./useConditions";
import { usePrescriptions } from "./usePharmacy";
import { useVitals } from "./useVitals";
import { useAllergies } from "./useAllergies";

export interface CdsAlert {
  code: string;
  severity: string; // CRITICAL | HIGH | MEDIUM | LOW
  message: string;
  explanation?: string;
  interruptive?: boolean;
  override_allowed?: boolean;
}

interface RulesEvaluateResponse {
  alerts: CdsAlert[];
}

const INACTIVE_RX = new Set(["cancelled", "canceled", "completed", "expired", "stopped", "discontinued", "void"]);
const INACTIVE_CLINICAL = new Set(["resolved", "inactive", "remission", "entered-in-error", "cancelled"]);

/**
 * Real, patient-specific CDS alerts. Returns deterministic rule-engine output for the patient's
 * own records — never invented. Empty when nothing triggers.
 */
export function useClinicalCdsAlerts(patientId: string | undefined) {
  const pid = patientId ?? "";
  const conditions = useConditions(pid);
  const prescriptions = usePrescriptions(patientId ? { patientId } : undefined);
  const vitals = useVitals(pid);
  const allergies = useAllergies(pid);

  const sourcesSettled =
    !!patientId &&
    !conditions.isLoading &&
    !prescriptions.isLoading &&
    !vitals.isLoading &&
    !allergies.isLoading;

  const context = useMemo(() => {
    const diagnoses = (conditions.data?.data ?? [])
      .filter((c) => !INACTIVE_CLINICAL.has((c.attributes.clinicalStatus ?? "").toLowerCase()))
      .map((c) => `${c.attributes.conditionName ?? ""} ${c.attributes.icdCode ?? ""}`.trim())
      .filter(Boolean);

    const activeMedications = (prescriptions.data?.data ?? [])
      .filter((p) => !INACTIVE_RX.has((p.attributes.status ?? "").toLowerCase()))
      .flatMap((p) => p.attributes.items ?? [])
      .map((item) => ({ genericName: item.medication ?? "", displayName: item.medication ?? "" }))
      .filter((m) => m.genericName);

    // Latest vitals record (lists are returned newest-first).
    const latest = (vitals.data?.data ?? [])[0]?.attributes;
    const v = latest
      ? {
          systolicBp: latest.systolic,
          diastolicBp: latest.diastolic,
          heartRate: latest.heartRate,
          temperatureC: latest.temperature,
          respiratoryRate: latest.respiratoryRate,
        }
      : undefined;

    const allergyList = (allergies.data?.data ?? [])
      .filter((a) => (a.attributes.status ?? "active").toLowerCase() !== "inactive")
      .map((a) => ({ substance: a.attributes.allergen ?? "", severity: a.attributes.severity ?? "" }))
      .filter((a) => a.substance);

    return {
      diagnoses,
      activeMedications,
      ...(v ? { vitals: v } : {}),
      allergies: allergyList,
    };
  }, [conditions.data, prescriptions.data, vitals.data, allergies.data]);

  const query = useQuery({
    queryKey: ["clinical", "cds-alerts", patientId, context],
    queryFn: () =>
      apiClient.post<ApiResponse<RulesEvaluateResponse>>("/internal/v1/clinical/rules/evaluate", context),
    enabled: sourcesSettled,
  });

  return {
    alerts: query.data?.data?.alerts ?? [],
    isLoading: !sourcesSettled || query.isLoading,
    isError: query.isError,
  };
}

/** Record a clinician override of a recommendation against its audit trace. */
export function useRecordClinicalOverride() {
  return useMutation({
    mutationFn: (body: { recommendation_trace_id: string; override_reason: string }) =>
      apiClient.post<ApiResponse<{ status: string; override_id: string }>>(
        "/internal/v1/clinical/audit/overrides",
        body,
      ),
  });
}
