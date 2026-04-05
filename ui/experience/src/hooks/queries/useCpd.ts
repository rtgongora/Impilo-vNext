/**
 * Experience UI — CPD (Continuing Professional Development) Query Hook
 *
 * Fetches CPD summary from VARAPI via BFF proxy.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface CpdSummary {
  currentCycle?: {
    id: number;
    startDate: string;
    endDate: string;
    requiredPoints: number;
    earnedPoints: number;
    status: string;
  };
  recentEvents?: Array<{
    id: number;
    eventType: string;
    title: string;
    pointsAwarded: number;
    eventDate: string;
    verified: boolean;
  }>;
  [key: string]: unknown;
}

type CpdResponse = ApiResponse<CpdSummary>;

export function useProviderCpd(providerId: string | undefined) {
  return useQuery<CpdResponse>({
    queryKey: ["provider-cpd", providerId],
    queryFn: () =>
      apiClient.get<CpdResponse>(
        `/internal/v1/registry/providers/${providerId}/cpd`
      ),
    enabled: !!providerId,
  });
}
