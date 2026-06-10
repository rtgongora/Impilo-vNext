/**
 * Shared hook for facility affiliations from BFF identity endpoint.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import type { FacilityAffiliation } from "@/lib/identity-context";

interface AffiliationsResponse {
  data: FacilityAffiliation[] | { attributes?: FacilityAffiliation[] };
}

function normalizeAffiliations(payload: AffiliationsResponse["data"]): FacilityAffiliation[] {
  if (Array.isArray(payload)) return payload;
  if (payload && "attributes" in payload && Array.isArray(payload.attributes)) {
    return payload.attributes;
  }
  return [];
}

export function useAffiliations() {
  const { user, isAuthenticated } = useAuthStore();

  return useQuery<FacilityAffiliation[]>({
    queryKey: ["identity-affiliations", user?.id],
    queryFn: async () => {
      const res = await apiClient.get<AffiliationsResponse>("/internal/v1/identity/affiliations");
      return normalizeAffiliations(res.data);
    },
    enabled: isAuthenticated && !!user?.id,
    staleTime: 5 * 60 * 1000,
    refetchOnWindowFocus: false,
  });
}
