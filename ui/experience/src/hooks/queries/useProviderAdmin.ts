/**
 * Provider admin mutations — create/update via BFF admin routes.
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { ProviderResource } from "@/hooks/queries/useRegistry";

export type ProviderSex = "MALE" | "FEMALE" | "OTHER";

export interface ProviderAdminPayload {
  givenName: string;
  familyName: string;
  profession: string;
  cadre: string;
  dateOfBirth: string;
  sex: ProviderSex;
  councilCode: string;
  registrationNumber: string;
}

type ProviderResponse = ApiResponse<ProviderResource>;

export function useCreateProvider() {
  const queryClient = useQueryClient();
  return useMutation<ProviderResponse, unknown, ProviderAdminPayload>({
    mutationFn: (body) =>
      apiClient.post<ProviderResponse>("/internal/v1/admin/providers", body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["providers"] });
    },
  });
}

export function useUpdateProvider(providerId: string) {
  const queryClient = useQueryClient();
  return useMutation<ProviderResponse, unknown, ProviderAdminPayload>({
    mutationFn: (body) =>
      apiClient.put<ProviderResponse>(
        `/internal/v1/admin/providers/${providerId}`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["providers"] });
      void queryClient.invalidateQueries({ queryKey: ["providers", providerId] });
    },
  });
}

/** Governed Varapi create: Vito anchor must exist for {@code impiloHealthId}. */
export type ProviderFromHealthIdPayload = ProviderAdminPayload & {
  impiloHealthId: string;
};

export function useCreateProviderFromHealthId() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<Record<string, unknown>>,
    unknown,
    ProviderFromHealthIdPayload
  >({
    mutationFn: (body) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        "/internal/v1/registry-intake/providers/from-health-id",
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["providers"] });
    },
  });
}
