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

// ── L1 native passkey (WebAuthn passwordless) ──────────────────────────────
// The WebAuthn ceremony is Keycloak-hosted: initiate returns the authorize URL
// (the browser is redirected there), and the callback exchanges the returned
// auth code for the SAME session shape as password login. Flag-gated in the BFF —
// when disabled, initiate rejects with status 501 / code PASSKEY_NOT_ENABLED so
// the UI keeps an honest "not enabled" state.

export interface PasskeyInitiateResource {
  id?: string;
  type: "passkey-initiate";
  attributes: {
    authorizeUrl: string;
    state: string;
    nonce: string;
    codeVerifier: string;
    register: boolean;
  };
}

type PasskeyInitiateResponse = ApiResponse<PasskeyInitiateResource>;

interface PasskeyInitiatePayload {
  register?: boolean;
  email?: string;
}

interface PasskeyCallbackPayload {
  code: string;
  codeVerifier?: string;
  redirectUri?: string;
}

/** Start the passkey ceremony — returns the Keycloak authorize URL + PKCE material. */
export function usePasskeyInitiate() {
  return useMutation<PasskeyInitiateResponse, unknown, PasskeyInitiatePayload | void>({
    mutationFn: (payload) =>
      apiClient.post<PasskeyInitiateResponse>("/internal/v1/auth/passkey/initiate", payload ?? {}),
  });
}

/** Complete the passkey ceremony — exchanges the auth code for a real session. */
export function usePasskeyCallback() {
  const queryClient = useQueryClient();

  return useMutation<LoginResponse, unknown, PasskeyCallbackPayload>({
    mutationFn: (payload: PasskeyCallbackPayload) =>
      apiClient.post<LoginResponse>("/internal/v1/auth/passkey/callback", payload),
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
