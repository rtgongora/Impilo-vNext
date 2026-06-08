/**
 * Provider break-glass request hook — routes through Experience BFF trust surface.
 */

import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface BreakGlassRequestInput {
  reason: string;
  resourceType?: string;
  resourceId?: string;
  patientId?: string;
}

export interface BreakGlassRequestResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export function useRequestBreakGlass() {
  return useMutation({
    mutationFn: async (input: BreakGlassRequestInput) => {
      return apiClient.post<ApiResponse<BreakGlassRequestResource>>(
        "/internal/v1/trust/break-glass",
        {
          reason: input.reason,
          resource_type: input.resourceType ?? "PATIENT_RECORD",
          resource_id: input.resourceId ?? input.patientId ?? null,
          patient_id: input.patientId ?? null,
        },
      );
    },
  });
}
