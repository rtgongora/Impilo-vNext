/**
 * Impilo vNext — Trust Layer Contracts (Shared)
 *
 * Trust header names and types shared across all UI apps.
 * MUST match: services/tshepo-service/.../core/TrustHeaders.java
 */

// ============================================================================
// Trust Header Names — SINGLE SOURCE OF TRUTH (frontend side)
// ============================================================================

export const TRUST_HEADERS = {
  // Request headers (UI → Envoy → TSHEPO)
  TENANT_ID: "x-tenant-id",
  ACTOR_ID: "x-actor-id",
  ACTOR_TYPE: "x-actor-type",
  PURPOSE_OF_USE: "x-purpose-of-use",
  DEVICE_FINGERPRINT: "x-device-fingerprint",
  CORRELATION_ID: "x-correlation-id",
  FACILITY_ID: "x-facility-id",
  WORKSPACE_ID: "x-workspace-id",
  SHIFT_ID: "x-shift-id",

  // Response / obligation headers (TSHEPO → Envoy → downstream)
  DECISION: "x-decision",
  OBLIGATIONS: "x-obligations",
  MAX_SCOPE: "x-max-scope",
  MASK_FIELDS: "x-mask-fields",
  LOGGING_LEVEL: "x-logging-level",
} as const;

// ============================================================================
// Common Types
// ============================================================================

// Mirrors zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse exactly. TSHEPO denies any
// request whose purpose is outside this set (INVALID_PURPOSE at PolicyEngine Step 2, before
// rule matching), so a code missing here is unreachable and a code invented here is a denial.
export type PurposeOfUse =
  | "TREATMENT"
  | "CARE_COORDINATION"
  | "PAYMENT"
  | "OPERATIONS"
  | "RESEARCH"
  | "PUBLIC_HEALTH"
  | "SELF_SERVICE"
  | "REGULATORY_DUTY"
  | "EMERGENCY"
  | "BREAK_GLASS"
  | "SYSTEM";

export type ActorType = "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM";

export interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  error?: { code: string; message: string; status: number };
  correlationId: string;
  timestamp: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}
