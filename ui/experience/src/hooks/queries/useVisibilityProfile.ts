"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

/** JSON body from BFF `GET /internal/v1/profile/visibility`. */
export type VisibilityProfileResponse = {
  visibilityTier?: string | null;
  piiAccess?: string | null;
  clinicalAccess?: string | null;
  aggregateOnly?: boolean | null;
  resourceSensitivityClass?: string | null;
  escalationGrantId?: string | null;
  exportPolicy?: string | null;
  drillDownAllowed?: boolean | null;
  suppressFields?: string[] | null;
  obligationsHeaderPresent?: boolean;
  source?: string;
  visibilityProfile?: unknown;
};

export function useVisibilityProfile() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ["bff", "visibility-profile"],
    queryFn: () => apiClient.get<VisibilityProfileResponse>("/internal/v1/profile/visibility"),
    enabled: isAuthenticated,
    staleTime: 60_000,
    retry: 1,
  });
}
