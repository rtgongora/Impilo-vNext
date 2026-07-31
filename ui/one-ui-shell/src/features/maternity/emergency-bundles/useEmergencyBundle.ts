import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type EmergencyBundleKind = "pph" | "eclampsia";
export type EmergencyBundleStepStatus = "DONE" | "DUE" | "OVERDUE" | "NOT_YET_DUE";
export type EmergencyBundleStatus = "ACTIVE" | "CLOSABLE" | "LAPSED_UNRESOLVED";

export interface EmergencyBundleStep {
  code: string;
  name: string;
  status: EmergencyBundleStepStatus;
  mandatory: boolean;
  action: string | null;
}

export interface EmergencyBundleAssessment {
  status: EmergencyBundleStatus;
  steps: EmergencyBundleStep[];
  outstanding_mandatory: string[];
  may_close: boolean;
  note: string;
}

export interface EmergencyBundleAssessInput {
  completedSteps: string[];
  minutesSinceTrigger: number;
  minutesSinceLastObservation: number;
  controlConfirmed?: boolean | null;
  clinicianConfirmedClose?: boolean;
}

interface AssessResponse {
  data: EmergencyBundleAssessment;
  meta?: Record<string, unknown>;
}

function assessPath(kind: EmergencyBundleKind): string {
  return kind === "pph"
    ? "/internal/v1/clinical/maternal/emergency-bundles/pph/assess"
    : "/internal/v1/clinical/maternal/emergency-bundles/eclampsia/assess";
}

export function useEmergencyBundleAssess(kind: EmergencyBundleKind) {
  return useMutation({
    mutationFn: async (input: EmergencyBundleAssessInput) => {
      const body: Record<string, unknown> = {
        completedSteps: input.completedSteps,
        minutesSinceTrigger: input.minutesSinceTrigger,
        minutesSinceLastObservation: input.minutesSinceLastObservation,
      };
      if (input.controlConfirmed != null) {
        body.controlConfirmed = input.controlConfirmed;
      }
      if (input.clinicianConfirmedClose != null) {
        body.clinicianConfirmedClose = input.clinicianConfirmedClose;
      }
      const response = await apiClient.post<AssessResponse>(assessPath(kind), body);
      return response.data;
    },
  });
}

export function isEmergencyBundleUnavailable(error: unknown): boolean {
  if (typeof error !== "object" || error === null) return false;
  const err = error as { status?: number; error?: string };
  return (
    err.status === 502 ||
    err.error === "pph_bundle_unavailable" ||
    err.error === "eclampsia_bundle_unavailable"
  );
}
