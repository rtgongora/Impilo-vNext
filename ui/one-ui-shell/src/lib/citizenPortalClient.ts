/**
 * Citizen portal API — same contract as ui/portal `portalApi` (VITO `/v1/portal/*` via gateway).
 * Uses the BFF bridge so the browser presents only its opaque HttpOnly session.
 * Bearer tokens and server-derived actor headers never enter browser JavaScript.
 */

import { apiClient, type ApiResponse } from "@/lib/api-client";

const PREFIX = '/internal/v1/vito/portal';

export async function citizenPortalFetch<T>(suffix: string, options: RequestInit = {}): Promise<T> {
  const path = suffix.startsWith('/') ? `${PREFIX}${suffix}` : `${PREFIX}/${suffix}`;
  const method = (options.method ?? 'GET').toUpperCase();
  const body = typeof options.body === 'string' ? JSON.parse(options.body) : options.body;
  const extraHeaders = (options.headers as Record<string, string> | undefined) ?? {};
  const response = method === 'GET'
    ? await apiClient.get<ApiResponse<T> | T>(path, { extraHeaders })
    : await apiClient.post<ApiResponse<T> | T>(path, body, { extraHeaders });
  return ('data' in (response as object)
    ? (response as ApiResponse<T>).data
    : response) as T;
}

/** For tests: which URL suffix would be requested (no leading PREFIX). */
export function citizenPortalPathForTests(suffix: string): string {
  return suffix.startsWith('/') ? `${PREFIX}${suffix}` : `${PREFIX}/${suffix}`;
}

export const citizenPortalApi = {
  requestId: (body: {
    type: string;
    givenName?: string;
    familyName?: string;
    dateOfBirth?: string;
    sex?: string;
  }) =>
    citizenPortalFetch<{ status: string; message: string; referenceId?: number }>('/id/request', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  startRecovery: (impiloId: string, stepUpToken: string) =>
    citizenPortalFetch<{ status: string; message: string }>('/id/recovery/start', {
      method: 'POST',
      body: JSON.stringify({ impiloId }),
      headers: { 'x-step-up-token': stepUpToken },
    }),

  verifyRecovery: (proof: Record<string, string>, stepUpToken: string) =>
    citizenPortalFetch<{ status: string; message: string }>('/id/recovery/verify', {
      method: 'POST',
      body: JSON.stringify(proof),
      headers: { 'x-step-up-token': stepUpToken },
    }),

  getMe: () =>
    citizenPortalFetch<{
      registered: boolean;
      healthId?: string;
      status?: string;
      impiloId?: string;
    }>('/me'),

  getHealthIdQr: () => citizenPortalFetch<{ qr: string }>('/health-id/qr'),

  createPickup: (
    body: {
      issuanceRequestId: number;
      delegateName: string;
      delegateContact: string;
      delegateIdRef?: string;
      facilityId: string;
      expiryHours?: number;
    },
    stepUpToken: string,
  ) =>
    citizenPortalFetch<{
      pickupId: number;
      pickupToken: string;
      otp: string;
      expiresAt: string;
      message: string;
    }>('/delegated-pickup/create', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'x-step-up-token': stepUpToken },
    }),

  redeemPickup: (pickupToken: string, otp: string) =>
    citizenPortalFetch<{ status: string; message: string }>('/delegated-pickup/redeem', {
      method: 'POST',
      body: JSON.stringify({ pickupToken, otp }),
    }),
};
