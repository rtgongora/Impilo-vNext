import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type MecRecommendation =
  | "USE"
  | "USE_WITH_FOLLOW_UP"
  | "SPECIALIST_JUDGEMENT_REQUIRED"
  | "DO_NOT_USE"
  | "CANNOT_DETERMINE";

export interface MecMethodEligibility {
  method_code: string;
  method_name: string;
  use_phase: string;
  category: number | null;
  recommendation: MecRecommendation;
  driving_conditions: Array<{
    condition_code: string;
    condition_name: string;
    category: number;
    clarification?: string | null;
  }>;
  unresolved_conditions: string[];
  missing_inputs: string[];
  provisional: boolean;
  rationale?: string | null;
}

export interface MecAssessResponse {
  methods: MecMethodEligibility[];
  conditions_not_assessed: string[];
  conditions_out_of_scope: string[];
  content_problems: string[];
  content_version?: string | null;
  approval_status?: string | null;
  note?: string | null;
}

export function useMecAssess() {
  return useMutation({
    mutationFn: (body: {
      usePhase?: "INITIATION" | "CONTINUATION";
      facts: Record<string, unknown>;
      methodFilter?: string[];
    }) =>
      apiClient.post<ApiResponse<MecAssessResponse>>(
        "/internal/v1/clinical/contraception/mec/assess",
        { usePhase: body.usePhase ?? "INITIATION", facts: body.facts, methodFilter: body.methodFilter },
      ),
  });
}
