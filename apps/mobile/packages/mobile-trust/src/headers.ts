/**
 * Impilo vNext — Mobile Trust Header Constants
 *
 * Single source of truth for trust header names on mobile.
 * MUST match: services/tshepo-service/.../core/TrustHeaders.java
 * MUST match: ui/shared-ui/lib/contracts.ts
 *
 * Header names are lowercase per HTTP/2 convention.
 */

export const TRUST_HEADERS = {
  // Request headers (Mobile → Envoy → TSHEPO)
  TENANT_ID: "x-tenant-id",
  ACTOR_ID: "x-actor-id",
  ACTOR_TYPE: "x-actor-type",
  PROVIDER_ID: "x-provider-id",
  PURPOSE_OF_USE: "x-purpose-of-use",
  DEVICE_FINGERPRINT: "x-device-fingerprint",
  CORRELATION_ID: "x-correlation-id",
  REQUEST_ID: "x-request-id",
  POD_ID: "x-pod-id",
  FACILITY_ID: "x-facility-id",
  DEPARTMENT_ID: "x-department-id",
  WARD_ID: "x-ward-id",
  WORKSPACE_ID: "x-workspace-id",
  PROGRAMME_ID: "x-programme-id",
  SHIFT_ID: "x-shift-id",
  ACCESS_MODE: "x-access-mode",
  CLIENT_TIMEOUT_MS: "x-client-timeout-ms",
  IDEMPOTENCY_KEY: "idempotency-key",
  AUTHORIZATION: "authorization",

  // Response / obligation headers (TSHEPO → Envoy → downstream)
  DECISION: "x-decision",
  OBLIGATIONS: "x-obligations",
  MAX_SCOPE: "x-max-scope",
  MASK_FIELDS: "x-mask-fields",
  LOGGING_LEVEL: "x-logging-level",
} as const;

export type TrustHeaderKey = keyof typeof TRUST_HEADERS;
export type TrustHeaderValue = (typeof TRUST_HEADERS)[TrustHeaderKey];

/**
 * The 4 hard-required request headers. Every request MUST carry these.
 * Matches CompanionHeaders.HARD_REQUIRED in tech-companion.
 */
export const HARD_REQUIRED_HEADERS = [
  TRUST_HEADERS.TENANT_ID,
  TRUST_HEADERS.POD_ID,
  TRUST_HEADERS.REQUEST_ID,
  TRUST_HEADERS.CORRELATION_ID,
] as const;

/**
 * Command methods that require an Idempotency-Key header.
 */
export const COMMAND_METHODS = ["POST", "PUT", "PATCH"] as const;
export type CommandMethod = (typeof COMMAND_METHODS)[number];
