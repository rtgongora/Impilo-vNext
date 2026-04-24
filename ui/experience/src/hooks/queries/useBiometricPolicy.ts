import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type BiometricPolicyEvaluateInput = {
  subjectType: string;
  workflowType: string;
  contextType: string;
  modality?: string | null;
  biometricIntent: string;
  resourceSensitivity?: string | null;
};

export type BiometricPolicyEvaluation = {
  policyOutcome: string;
  enrollmentAllowed: boolean;
  verificationAllowed: boolean;
  identificationAllowed: boolean;
  dedupSupportAllowed: boolean;
  fallbackAllowed: boolean;
  matchedRuleId: number | null;
  reasons: string[];
};

/**
 * Evaluates Tshepo biometric policy via the Experience BFF (no sovereign URLs in the browser).
 */
export function useEvaluateBiometricPolicy() {
  return useMutation({
    mutationFn: async (input: BiometricPolicyEvaluateInput) => {
      return apiClient.post<BiometricPolicyEvaluation>(
        "/internal/v1/trust/biometric-policy/evaluate",
        input,
      );
    },
  });
}
