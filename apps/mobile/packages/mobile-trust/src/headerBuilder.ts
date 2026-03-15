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
  if (session.workspaceId) {
    headers[TRUST_HEADERS.WORKSPACE_ID] = session.workspaceId;
  }
  if (session.shiftId) {
    headers[TRUST_HEADERS.SHIFT_ID] = session.shiftId;
  }
  if (session.deviceFingerprint) {
    headers[TRUST_HEADERS.DEVICE_FINGERPRINT] = session.deviceFingerprint;
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
