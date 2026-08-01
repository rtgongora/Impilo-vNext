/** Experience UI — opaque BFF session mutations. */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type LogoutResponse = ApiResponse<{
  id: string;
  type: "logout";
  attributes: Record<string, unknown>;
}>;

export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation<LogoutResponse, unknown, void>({
    mutationFn: () => apiClient.post<LogoutResponse>("/internal/v1/auth/oidc/logout"),
    onSuccess: () => queryClient.clear(),
  });
}
