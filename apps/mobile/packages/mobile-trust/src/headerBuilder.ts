/**
 * Impilo vNext — Trust Header Builder
 *
 * Constructs the full trust header map from a SessionContext.
 * Validates that all hard-required headers are present.
 */

import { TRUST_HEADERS, HARD_REQUIRED_HEADERS, COMMAND_METHODS } from "./headers";
import type { SessionContext } from "./types";

export class MissingHeaderError extends Error {
  public readonly headerName: string;

  constructor(headerName: string) {
    super(`Missing required trust header: ${headerName}`);
    this.name = "MissingHeaderError";
    this.headerName = headerName;
  }
}

/**
 * Generates a UUID v4 string for request/correlation IDs.
 * Uses crypto.randomUUID when available, falls back to manual generation.
 */
export function generateId(): string {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }
  // Fallback for environments without crypto.randomUUID
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Builds the trust header map from session context.
 * Includes all 14 trust headers as specified in the v1.1 contract.
 */
export function buildTrustHeaders(
  session: SessionContext,
  options?: {
    method?: string;
    correlationId?: string;
    clientTimeoutMs?: number;
  }
): Record<string, string> {
  const requestId = generateId();
  const correlationId = options?.correlationId ?? generateId();

  const headers: Record<string, string> = {
    "content-type": "application/json",
    [TRUST_HEADERS.TENANT_ID]: session.tenantId,
    [TRUST_HEADERS.POD_ID]: session.podId,
    [TRUST_HEADERS.REQUEST_ID]: requestId,
    [TRUST_HEADERS.CORRELATION_ID]: correlationId,
    [TRUST_HEADERS.ACTOR_ID]: session.actorId,
    [TRUST_HEADERS.ACTOR_TYPE]: session.actorType,
    [TRUST_HEADERS.PURPOSE_OF_USE]: session.purposeOfUse,
    [TRUST_HEADERS.AUTHORIZATION]: `Bearer ${session.accessToken}`,
  };

  if (session.facilityId) {
    headers[TRUST_HEADERS.FACILITY_ID] = session.facilityId;
  }
  if (session.departmentId) {
    headers[TRUST_HEADERS.DEPARTMENT_ID] = session.departmentId;
  }
  if (session.wardId) {
    headers[TRUST_HEADERS.WARD_ID] = session.wardId;
  }
  if (session.providerId) {
    headers[TRUST_HEADERS.PROVIDER_ID] = session.providerId;
  }
  if (session.workspaceId) {
    headers[TRUST_HEADERS.WORKSPACE_ID] = session.workspaceId;
  }
  if (session.programmeId) {
    headers[TRUST_HEADERS.PROGRAMME_ID] = session.programmeId;
  }
  if (session.shiftId) {
    headers[TRUST_HEADERS.SHIFT_ID] = session.shiftId;
  }
  if (session.accessMode) {
    headers[TRUST_HEADERS.ACCESS_MODE] = session.accessMode;
  }
  if (session.deviceFingerprint) {
    headers[TRUST_HEADERS.DEVICE_FINGERPRINT] = session.deviceFingerprint;
  }
  if (session.assuranceLevel) {
    headers["x-assurance-level"] = session.assuranceLevel;
  }
  if (options?.clientTimeoutMs) {
    headers[TRUST_HEADERS.CLIENT_TIMEOUT_MS] = String(options.clientTimeoutMs);
  }

  // Add idempotency key for command methods
  const method = options?.method?.toUpperCase();
  if (method && (COMMAND_METHODS as readonly string[]).includes(method)) {
    headers[TRUST_HEADERS.IDEMPOTENCY_KEY] = generateId();
  }

  return headers;
}

/**
 * Context for an anonymous (pre-authentication) request on a public gateway lane.
 * There is no actor and no bearer token — only the platform-identity headers the
 * v1.1 companion filter hard-requires on every /internal/v1/** route.
 */
export interface PublicHeaderContext {
  /** Tenant identifier (the public/default tenant when the caller is anonymous). */
  tenantId: string;
  /** Pod identifier (typically the national spine). */
  podId: string;
  /** Optional purpose-of-use hint (e.g. PUBLIC_HEALTH). Never carries actor identity. */
  purposeOfUse?: string;
}

/**
 * Builds the minimal trust-header map for an ANONYMOUS public-lane request.
 *
 * Includes only the four hard-required platform headers (tenant, pod, request,
 * correlation), content-type, an idempotency key on command methods, and an
 * optional purpose-of-use. Crucially it NEVER attaches an Authorization header or
 * an actor identity — the caller is unauthenticated. Used by the public API client
 * for the gateway's permitAll lanes (/internal/v1/public/gateway/**,
 * /internal/v1/auth/contact/otp/**, /internal/v1/auth/register*).
 */
export function buildPublicTrustHeaders(
  context: PublicHeaderContext,
  options?: {
    method?: string;
    correlationId?: string;
    clientTimeoutMs?: number;
  }
): Record<string, string> {
  const requestId = generateId();
  const correlationId = options?.correlationId ?? generateId();

  const headers: Record<string, string> = {
    "content-type": "application/json",
    [TRUST_HEADERS.TENANT_ID]: context.tenantId,
    [TRUST_HEADERS.POD_ID]: context.podId,
    [TRUST_HEADERS.REQUEST_ID]: requestId,
    [TRUST_HEADERS.CORRELATION_ID]: correlationId,
  };

  if (context.purposeOfUse) {
    headers[TRUST_HEADERS.PURPOSE_OF_USE] = context.purposeOfUse;
  }
  if (options?.clientTimeoutMs) {
    headers[TRUST_HEADERS.CLIENT_TIMEOUT_MS] = String(options.clientTimeoutMs);
  }

  const method = options?.method?.toUpperCase();
  if (method && (COMMAND_METHODS as readonly string[]).includes(method)) {
    headers[TRUST_HEADERS.IDEMPOTENCY_KEY] = generateId();
  }

  return headers;
}

/**
 * Validates that all hard-required headers are present and non-blank.
 * Throws MissingHeaderError on first missing header.
 */
export function validateRequiredHeaders(headers: Record<string, string>): void {
  for (const headerName of HARD_REQUIRED_HEADERS) {
    const value = headers[headerName];
    if (!value || value.trim() === "") {
      throw new MissingHeaderError(headerName);
    }
  }
}
