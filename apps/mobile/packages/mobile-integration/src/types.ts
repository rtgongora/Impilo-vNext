/**
 * Integration Service mobile contract.
 *
 * The Integration Service (integration-hub) mediates between Impilo and
 * external/internal systems: PACS, LIMS, eLMIS, telemedicine vendors,
 * MusheX/payment gateways, claims switches, Comms Hub channels, Nhume
 * logistics adapters, Ndila map providers, civil registration, EMRs, etc.
 *
 * Mobile apps must consume integration status through this SDK rather than
 * calling vendors directly. The SDK exposes:
 *   - the canonical mobile-friendly status enum
 *   - safe copy for each status
 *   - a typed read-only client
 *   - a "handoff" descriptor for the safe deep-link pattern
 */

export type IntegrationDomain =
  | "PACS"
  | "LIMS"
  | "ELMIS"
  | "TELEMEDICINE"
  | "COMMS_HUB"
  | "MUSHEX"
  | "CLAIMS"
  | "NHUME"
  | "NDILA"
  | "EMR"
  | "REGISTRY"
  | "CRVS"
  | "INSURANCE"
  | "OTHER";

/**
 * Canonical mobile-facing status. This is *not* the raw upstream protocol
 * state. The BFF maps raw vendor / adapter states into this set so mobile UI
 * stays consistent.
 */
export type IntegrationStatus =
  | "CONNECTED"
  | "PENDING"
  | "PROCESSING"
  | "AWAITING_EXTERNAL"
  | "AVAILABLE"
  | "FAILED"
  | "RETRY_SCHEDULED"
  | "ACTION_REQUIRED"
  | "TEMPORARILY_UNAVAILABLE"
  | "NOT_CONFIGURED"
  | "DISABLED"
  | "UNKNOWN";

/** Severity tier used by the UI badge component. */
export type IntegrationSeverity = "info" | "success" | "warning" | "danger" | "muted";

export interface IntegrationStatusSummary {
  /** Unique status id from the BFF (used as React key). */
  id: string;
  /** Logical domain — drives icon / label / handoff routing. */
  domain: IntegrationDomain;
  /** Optional adapter or vendor name. Avoid showing raw vendor strings in citizen UI. */
  adapter?: string;
  /** Canonical status. */
  status: IntegrationStatus;
  /** Last successful sync ISO time. */
  lastSyncedAt?: string;
  /** Short, mobile-safe message (the BFF should provide this; we have a fallback). */
  message?: string;
  /** Correlation ID for support — never leaked into headline copy. */
  correlationId?: string;
  /** Optional handoff descriptor (deep link/external URL/support route). */
  handoff?: IntegrationHandoff;
}

export type IntegrationHandoffMode =
  | "IN_APP_DEEP_LINK"
  | "WEB_DEEP_LINK"
  | "SUPPORT_TICKET"
  | "NONE";

export interface IntegrationHandoff {
  mode: IntegrationHandoffMode;
  label: string;
  /** Used when mode == IN_APP_DEEP_LINK or WEB_DEEP_LINK. */
  url?: string;
  /** Whether opening the URL must be wrapped in a step-up prompt. */
  requiresStepUp?: boolean;
}
