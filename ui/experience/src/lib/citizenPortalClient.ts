/**
 * Citizen portal API — same contract as ui/portal `portalApi` (VITO `/v1/portal/*` via gateway).
 * Uses browser-relative `/api/v1/portal/...` with Next rewrites to the API gateway (see next.config.mjs).
 * Requires signed-in user with actor type CITIZEN and a valid in-memory bearer token.
 * A legacy sessionStorage token fallback remains only for compatibility during transition.
 */

import { useAuthStore } from "@/hooks/useAuthStore";

const PREFIX = '/api/v1/portal';

function readSessionHeaders(): Record<string, string> {
  if (typeof window === 'undefined') {
    return { 'Content-Type': 'application/json' };
  }
  const token = useAuthStore.getState().token ?? sessionStorage.getItem('exp:auth_token');
  const rawUser = sessionStorage.getItem('exp:auth_user');
  let actorId = '';
  if (rawUser) {
    try {
      actorId = (JSON.parse(rawUser) as { id?: string }).id ?? '';
    } catch {
      /* ignore */
    }
  }
  const tenant = sessionStorage.getItem('exp:tenant_id') ?? 'tenant-moh-zw';
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'x-tenant-id': tenant,
    'x-actor-id': actorId,
    'x-actor-type': 'CITIZEN',
    'x-purpose-of-use': 'SELF_SERVICE',
    'x-correlation-id': crypto.randomUUID(),
    'x-access-mode': 'EXTERNAL',
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

export async function citizenPortalFetch<T>(suffix: string, options: RequestInit = {}): Promise<T> {
  const path = suffix.startsWith('/') ? `${PREFIX}${suffix}` : `${PREFIX}/${suffix}`;
  const res = await fetch(path, {
    ...options,
    headers: { ...readSessionHeaders(), ...(options.headers as Record<string, string>) },
  });
  if (!res.ok) {
    throw new Error(`Portal API error: ${res.status}`);
  }
  const json = await res.json();
  return (json.data ?? json) as T;
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
