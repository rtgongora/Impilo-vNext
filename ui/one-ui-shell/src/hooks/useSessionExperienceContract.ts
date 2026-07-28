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
    // Deliberately NOT keyed on workAssignments.length (Phase F10): useWorkAssignments() is a
    // separate query, and keying on its result serialised this fetch behind it — the contract
    // request couldn't even fire until assignments had already resolved, adding real latency to
    // the critical /auth/resolving path for no benefit (the BFF response doesn't depend on the
    // client's locally-cached assignment count; only the dev/offline local-resolver fallback
    // below reads workAssignments, and it reads whatever value is in scope when it runs).
    queryKey: ["session-experience", user?.id, user?.providerId, hasFacility],
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
    // The key includes facility state; keep the last contract while
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
