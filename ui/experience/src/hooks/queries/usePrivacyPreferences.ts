/**
 * Experience UI — Privacy Display Preferences Hooks
 *
 * TanStack Query hooks for the per-user privacy display preferences
 * served by PrivacyPreferencesController (GET/PUT /internal/v1/settings/privacy).
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface PrivacyPreferenceResource {
  id: string;
  type: "privacy_preference";
  attributes: {
    defaultLevel: "FULL" | "PARTIAL" | "MINIMAL";
    autoLockMinutes: number;
    screenShareMode: boolean;
  };
}

/** Fetch the current user's privacy display preferences. */
export function usePrivacyPreferences() {
  return useQuery<ApiResponse<PrivacyPreferenceResource>>({
    queryKey: ["privacy-preferences"],
    queryFn: () =>
      apiClient.get<ApiResponse<PrivacyPreferenceResource>>(
        "/internal/v1/settings/privacy"
      ),
  });
}

interface UpdatePrivacyPreferencesPayload {
  defaultLevel?: "FULL" | "PARTIAL" | "MINIMAL";
  autoLockMinutes?: number;
  screenShareMode?: boolean;
}

/** Update the current user's privacy display preferences. */
export function useUpdatePrivacyPreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdatePrivacyPreferencesPayload) =>
      apiClient.put("/internal/v1/settings/privacy", payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["privacy-preferences"] });
    },
  });
}
