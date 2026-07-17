/**
 * Identity assurance — BFF /internal/v1/identity/assurance/*
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type AssuranceUpgradeTier = "BASIC" | "TEMPORARY" | "FULL";

const TIER_TO_LOA: Record<AssuranceUpgradeTier, { targetLevel: string; method: string }> = {
  BASIC: { targetLevel: "LOA1", method: "SELF_ASSERTED" },
  TEMPORARY: { targetLevel: "LOA2", method: "SUPERVISED_REMOTE_PROOFING" },
  FULL: { targetLevel: "LOA3", method: "IN_PERSON_VERIFICATION" },
};

// NOTE: `useAssuranceStatus` was consolidated into `@/hooks/queries/useIdentity` — that
// typed `AssuranceStatus` (permissions / upgrade pathways / nextBestStep) is the single
// implementation used across the shell (banner, status chip, explainers). Import it from
// there; do not reintroduce a duplicate here.

export function useRequestAssuranceUpgrade() {
  return useMutation({
    mutationFn: (tier: AssuranceUpgradeTier) => {
      const mapping = TIER_TO_LOA[tier];
      return apiClient.post<ApiResponse<Record<string, unknown>>>(
        "/internal/v1/identity/assurance/upgrade/request",
        mapping,
      );
    },
  });
}

type RegistrationReadinessData = {
  type: string;
  attributes: { ready: boolean; code?: string; message?: string };
};

export function useRegistrationReadiness() {
  return useQuery({
    queryKey: ["auth", "register", "readiness"],
    queryFn: () =>
      apiClient.get<ApiResponse<RegistrationReadinessData>>("/internal/v1/auth/register/readiness"),
    staleTime: 60_000,
    retry: 1,
  });
}
