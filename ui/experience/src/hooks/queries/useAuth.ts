/**
 * Experience UI — Auth Query Hooks
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface AuthTokenResource {
  id: string;
  type: "auth_token";
  attributes: {
    token: string;
    expiresAt: string;
    user: {
      id: string;
      email: string;
      displayName: string;
      roles: string[];
      actorType: string;
    };
    [key: string]: unknown;
  };
}

interface LoginPayload {
  email: string;
  password: string;
}

type LoginResponse = ApiResponse<AuthTokenResource>;
type LogoutResponse = ApiResponse<{ id: string; type: "logout"; attributes: Record<string, unknown> }>;

export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation<LoginResponse, unknown, LoginPayload>({
    mutationFn: (payload: LoginPayload) =>
      apiClient.post<LoginResponse>("/internal/v1/auth/login", payload),
    onSuccess: () => {
      queryClient.clear();
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation<LogoutResponse, unknown, void>({
    mutationFn: () =>
      apiClient.post<LogoutResponse>("/internal/v1/auth/logout"),
    onSuccess: () => {
      queryClient.clear();
    },
  });
}
