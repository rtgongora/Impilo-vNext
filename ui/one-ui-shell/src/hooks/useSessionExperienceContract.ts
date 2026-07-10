/**
 * Fetches and caches the BFF Session Experience Contract.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkAssignments } from "@/hooks/queries/useWorkAssignments";
import type { SessionExperienceContract } from "@/lib/trust";
import { resolveSessionExperienceContract, type SessionExperienceInput } from "@/lib/trust";

interface SessionExperienceResource {
  id: string;
  type: "session-experience";
  attributes: SessionExperienceContract;
}

type SessionExperienceResponse = ApiResponse<SessionExperienceResource>;

export function useSessionExperienceContract() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const hasFacility = useFacilityStore((s) => s.hasFacility);
  const { data: workAssignments = [] } = useWorkAssignments();

  const query = useQuery<SessionExperienceContract>({
    queryKey: ["session-experience", user?.id, user?.providerId, hasFacility, workAssignments.length],
    queryFn: async () => {
      try {
        const res = await apiClient.get<SessionExperienceResponse>("/internal/v1/session/experience");
        return res.data.attributes;
      } catch {
        // Fallback: local resolver when BFF unavailable (dev/tests)
        return resolveLocalSessionContract(user, hasFacility, workAssignments);
      }
    },
    enabled: isAuthenticated && !!user,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    // The key includes facility/assignment state; keep the last contract while
    // the new one loads so route guards never see a transient undefined.
    placeholderData: (previous) => previous,
  });

  return {
    contract: query.data,
    isLoading: query.isLoading,
    isError: query.isError,
  };
}

function resolveLocalSessionContract(
  user: ReturnType<typeof useAuthStore.getState>["user"],
  hasFacility: boolean,
  workAssignments: SessionExperienceInput["workAssignments"] = [],
): SessionExperienceContract {
  const providerId = user?.providerId ?? user?.linkedIds?.providerId;
  const providerStatus = user?.providerActivated
    ? "active"
    : providerId
      ? "verified"
      : undefined;

  const input: SessionExperienceInput = {
    authenticated: !!user,
    loginMethod: (user?.loginMethod as SessionExperienceInput["loginMethod"]) ?? "unknown",
    healthId: user?.id,
    providerWorkerId: user?.providerId,
    linkedProviderWorkerId: user?.linkedIds?.providerId,
    professionalTruth: {
      providerWorkerId: providerId,
      linkedHealthId: user?.id,
      providerWorkerStatus: providerStatus,
    },
    workAssignments,
    hasSelectedFacility: hasFacility,
    facilityModeActive: hasFacility,
  };
  return resolveSessionExperienceContract(input);
}
