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

/** Whether actorType was inferred from realm_access.roles or defaulted for local OIDC. */
export type ActorTypeSource = "realm_roles" | "default_no_roles";

export interface SessionInfo {
  actorId: string;
  actorType: ActorType;
  /** When default_no_roles, UI should label PROVIDER as a fallback (no realm roles in token). */
  actorTypeSource?: ActorTypeSource;
  displayName: string;
  email?: string;
  roles: string[];
  accessToken: string;
  refreshToken?: string;
  expiresAt: number;
}

/** How x-tenant-id was chosen (OIDC vs dev fallback vs manual form). */
export type TenantIdHeaderOrigin =
  | "keycloak_access_token"
  | "fallback_uuid_no_claim"
  | "manual";

export interface WorkContext {
  tenantId: string;
  facilityId?: string;
  facilityName?: string;
  workspaceId?: string;
  workspaceName?: string;
  shiftId?: string;
  /** When set, explains why x-tenant-id may not match a Keycloak tenant_id claim. */
  tenantIdHeaderOrigin?: TenantIdHeaderOrigin;
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
