/**
 * Shared types for Impilo vNext shared-kernel.
 * Companion Spec v1.1.0-canonical compliance primitives.
 */

// --- Tenant Context ---

export interface TenantContext {
  tenant_id: string;
  pod_id: string;
  request_id: string;
  correlation_id: string;
}

// --- Action Types ---

export type ActionType =
  | "clinical.prescribe.controlled_substance"
  | "finance.tariff.update"
  | "identity.patient.merge"
  | "clinical.admit.patient"
  | "clinical.capture.vitals.offline"
  | (string & {});

// --- Consistency Class (Companion Spec section 3.4) ---

export enum ConsistencyClass {
  A = "A",
  B = "B",
  C = "C",
}

// --- Authority ---

export enum Authority {
  NATIONAL_SPINE_ONLY = "NATIONAL_SPINE_ONLY",
  NATIONAL_CONSUMER = "NATIONAL_CONSUMER",
  POD_AUTHORITATIVE_WITH_LINKAGE = "POD_AUTHORITATIVE_WITH_LINKAGE",
  UNKNOWN = "UNKNOWN",
}

// --- Consistency Decision ---

export interface ConsistencyDecision {
  sync_required: boolean;
  authority: Authority;
  class: ConsistencyClass;
  /** Required for Class B */
  max_staleness_ms?: number;
  /** Required for Class C */
  offline_allowed?: boolean;
}
