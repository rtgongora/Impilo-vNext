/**
 * Provider work-eligibility summary (D-P6, PJ15/PJ17).
 *
 * Source: BFF GET /internal/v1/work-context/eligibility — an OWNER-ONLY
 * post-login summary of whether the session actor can work right now, and if
 * not, the remediation. Authentication and authorisation are separate: a
 * suspended/lapsed provider signs in successfully, this endpoint reports
 * workEligible=false with a clear next step, and My Life / My Professional stay
 * reachable regardless.
 *
 * Degrades honestly: when the call fails, the hook returns an unresolved
 * summary rather than throwing — the panel shows "status unavailable" instead
 * of an error, and never fabricates eligibility.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

export interface WorkEligibility {
  linked: boolean;
  workEligible: boolean;
  lifecycleStatus: string | null;
  licenceStatus: string | null;
  remediation: string | null;
  resolved: boolean;
}

interface WorkEligibilityResource {
  id: string;
  type: "work-eligibility";
  attributes: Omit<WorkEligibility, "resolved">;
}

type WorkEligibilityResponse = ApiResponse<WorkEligibilityResource>;

const UNRESOLVED: WorkEligibility = {
  linked: false,
  workEligible: false,
  lifecycleStatus: null,
  licenceStatus: null,
  remediation: null,
  resolved: false,
};

export function useWorkEligibility() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const query = useQuery<WorkEligibility>({
    queryKey: ["work-eligibility", user?.id],
    queryFn: async () => {
      try {
        const res = await apiClient.get<WorkEligibilityResponse>(
          "/internal/v1/work-context/eligibility",
        );
        return { ...res.data.attributes, resolved: true };
      } catch {
        return UNRESOLVED;
      }
    },
    enabled: isAuthenticated && !!user,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });

  return {
    eligibility: query.data ?? UNRESOLVED,
    isLoading: query.isLoading,
    isError: query.isError,
  };
}
