/**
 * Impilo vNext — Trust Layer Contracts
 *
 * Re-exports shared trust header constants from shared-ui and defines
 * app-specific types for the one-ui-shell (clinical provider workflows).
 *
 * Header names MUST match the Java TrustHeaders.java constants exactly.
 */

// Re-export shared trust contracts (single source of truth)
export { TRUST_HEADERS } from "shared-ui";
import type { ActorType } from "shared-ui";
export type { ActorType, ApiEnvelope, PagedResponse, PurposeOfUse } from "shared-ui";

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
