import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type MvumoEvaluateData = {
  allowed?: boolean;
  requiresReview?: boolean;
  useGenericWording?: boolean;
  rationale?: string;
  patientRef?: string;
  proposedChannel?: string;
  normalizedChannel?: string;
  lifecycleState?: string;
  bundleVersion?: number;
};

/**
 * Pre-send / pre-page gate against Mvumo communication preferences (via BFF → mvumo-service).
 */
export function useMvumoCommunicationEvaluate() {
  return useMutation({
    mutationFn: async (input: { patientCpid: string; proposedChannel: string; messageKind?: string; emergencyOverride?: boolean }) => {
      const patientRef = input.patientCpid.startsWith("Patient/") ? input.patientCpid : `Patient/${input.patientCpid}`;
      return apiClient.post<ApiResponse<MvumoEvaluateData>>(`/internal/v1/mvumo/communication-preferences/evaluate`, {
        patientRef,
        proposedChannel: input.proposedChannel,
        messageKind: input.messageKind ?? "GENERAL",
        emergencyOverride: input.emergencyOverride === true,
      });
    },
  });
}
