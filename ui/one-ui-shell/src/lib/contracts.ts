/**
 * Impilo vNext — Trust Layer Contracts
 *
 * These types define the contract between the UI and the TSHEPO trust service.
 * Header names MUST match the Java TrustHeaders.java constants exactly.
 */

// ============================================================================
// Trust Header Names — SINGLE SOURCE OF TRUTH (frontend side)
// Must match: services/tshepo-service/.../core/TrustHeaders.java
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
// Purpose of Use
// ============================================================================

export type PurposeOfUse =
  | "TREATMENT"
  | "PAYMENT"
  | "OPERATIONS"
  | "RESEARCH"
  | "PUBLIC_HEALTH"
  | "EMERGENCY"
  | "BREAK_GLASS"
  | "SYSTEM";

// ============================================================================
// Actor Types
// ============================================================================

export type ActorType = "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM";

// ============================================================================
// Authorization Decision
// ============================================================================

export type AuthzVerdict = "ALLOW" | "DENY" | "STEP_UP_REQUIRED";

export interface AuthzResponse {
  decision: AuthzVerdict;
  obligations?: Obligations;
  errorCode?: string;
  errorMessage?: string;
  stepUpMethods?: string[];
}

export interface Obligations {
  maxScope?: string;
  maskFields?: string[];
  loggingLevel: string;
  consentScopeRef?: string;
}

// ============================================================================
// Session & Context
// ============================================================================

export interface SessionInfo {
  actorId: string;
  actorType: ActorType;
  displayName: string;
  email?: string;
  roles: string[];
  accessToken: string;
  refreshToken?: string;
  expiresAt: number;
}

export interface WorkContext {
  tenantId: string;
  facilityId?: string;
  facilityName?: string;
  workspaceId?: string;
  workspaceName?: string;
  shiftId?: string;
}

// ============================================================================
// API Error
// ============================================================================

export interface ApiError {
  status: number;
  code: string;
  message: string;
  correlationId?: string;
}

// ============================================================================
// Step-Up Challenge
// ============================================================================

export interface StepUpChallenge {
  methods: string[];
  correlationId: string;
  originalRequest?: {
    method: string;
    url: string;
    body?: unknown;
  };
}
