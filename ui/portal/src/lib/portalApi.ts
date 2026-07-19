/**
 * Portal — Trust-Aware API Client
 *
 * Citizen-facing fetch wrapper that injects the mandatory trust headers from the
 * portal session. Every request carries actor type CITIZEN and purpose SELF_SERVICE,
 * ensuring Envoy ext_authz -> TSHEPO can evaluate every request before it reaches
 * the VITO portal endpoints.
 *
 * Header contract must match: shared-ui/lib/contracts.ts <-> TrustHeaders.java
 */

const PORTAL_BASE = "/api/v1/portal";

interface PortalSession {
  accessToken: string;
  tenantId: string;
  actorId: string;
}

let session: PortalSession | null = null;

export function setPortalSession(s: PortalSession) {
  session = s;
}

export function clearPortalSession() {
  session = null;
}

export function getPortalSession(): PortalSession | null {
  return session;
}

async function portalFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(session
      ? {
          "x-tenant-id": session.tenantId,
          "x-actor-id": session.actorId,
          "x-actor-type": "CITIZEN",
          "x-purpose-of-use": "SELF_SERVICE",
          "x-correlation-id": crypto.randomUUID(),
          "x-access-mode": "EXTERNAL",
          Authorization: `Bearer ${session.accessToken}`,
        }
      : {}),
  };

  const res = await fetch(path, {
    ...options,
    headers: { ...headers, ...((options.headers as Record<string, string>) || {}) },
  });

  if (!res.ok) {
    throw new Error(`Portal API error: ${res.status}`);
  }

  const json = await res.json();
  return json.data ?? json;
}

export const portalApi = {
  /** Request a new or replacement Impilo ID */
  requestId: (body: {
    type: string;
    givenName?: string;
    familyName?: string;
    dateOfBirth?: string;
    sex?: string;
  }) =>
    portalFetch<{ status: string; message: string; referenceId?: number }>(
      `${PORTAL_BASE}/id/request`,
      {
        method: "POST",
        body: JSON.stringify(body),
      },
    ),

  /** Start ID recovery flow — requires step-up token */
  startRecovery: (impiloId: string, stepUpToken: string) =>
    portalFetch<{ status: string; message: string }>(
      `${PORTAL_BASE}/id/recovery/start`,
      {
        method: "POST",
        body: JSON.stringify({ impiloId }),
        headers: { "x-step-up-token": stepUpToken },
      },
    ),

  /** Verify recovery with proof — requires step-up token */
  verifyRecovery: (proof: Record<string, string>, stepUpToken: string) =>
    portalFetch<{ status: string; message: string }>(
      `${PORTAL_BASE}/id/recovery/verify`,
      {
        method: "POST",
        body: JSON.stringify(proof),
        headers: { "x-step-up-token": stepUpToken },
      },
    ),

  /** Get current citizen profile */
  getMe: () =>
    portalFetch<{
      registered: boolean;
      // No raw healthId: the internal Health ID (UUID) is never surfaced to the browser.
      // The citizen's patient-facing identifier is impiloId; presentation uses the signed QR.
      status?: string;
      impiloId?: string;
    }>(`${PORTAL_BASE}/me`),

  /** Get signed Health ID QR token */
  getHealthIdQr: () => portalFetch<{ qr: string }>(`${PORTAL_BASE}/health-id/qr`),

  /** Create a delegated pickup — requires step-up token */
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
    portalFetch<{
      pickupId: number;
      pickupToken: string;
      otp: string;
      expiresAt: string;
      message: string;
    }>(`${PORTAL_BASE}/delegated-pickup/create`, {
      method: "POST",
      body: JSON.stringify(body),
      headers: { "x-step-up-token": stepUpToken },
    }),

  /** Redeem a delegated pickup with token + OTP */
  redeemPickup: (pickupToken: string, otp: string) =>
    portalFetch<{ status: string; message: string }>(
      `${PORTAL_BASE}/delegated-pickup/redeem`,
      {
        method: "POST",
        body: JSON.stringify({ pickupToken, otp }),
      },
    ),
};
