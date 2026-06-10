/**
 * Session Experience Contract — BFF-issued trust envelope for web/mobile shell rendering.
 */

import type { WorkAssignment } from "./work-assignment";

export type SessionLoginMethod =
  | "health_id"
  | "provider_id"
  | "email"
  | "phone"
  | "sso"
  | "assisted_login"
  | "device_bound_login"
  | "service_account_token"
  | "unknown";

export type SessionIdentityType =
  | "citizen"
  | "provider_worker"
  | "dual_citizen_provider"
  | "marketplace_actor"
  | "regulator"
  | "registry_owner"
  | "system_admin"
  | "support_user";

export interface SessionIdentity {
  healthId?: string;
  providerWorkerId?: string;
  organisationId?: string;
  identityType: SessionIdentityType;
  citizenAccountState?: string;
  identityAssuranceLevel?: string;
}

export interface SessionTabDefinition {
  visible: boolean;
  default?: boolean;
  label: string;
  route: string;
  reason?: string;
  requiredSource?: string;
}

export interface SessionExperienceContract {
  authenticated: boolean;
  loginMethod: SessionLoginMethod;
  identity: SessionIdentity;
  tabs: {
    personal: SessionTabDefinition;
    professional: SessionTabDefinition;
    work: SessionTabDefinition;
  };
  providerWorkerStatus?: string;
  marketplacePipelineState?: string;
  activeWorkAssignments: WorkAssignment[];
  availableContexts: string[];
  selectedContext?: string;
  visibleWorkspaces: string[];
  visibleActions: string[];
  blockedActions: string[];
  roleTemplates: string[];
  policyMetadata: {
    contractVersion: string;
    opaPackages: string[];
    enforcement: "bff_and_opa";
  };
  nompiloContext: {
    activeTab: "personal" | "professional" | "work";
    suggestedPrompts: string[];
    allowedActionDomains: string[];
  };
  friendlyResolutionState?: string;
  resolutionActions: string[];
  requiresContextChooser: boolean;
  facilityModeAvailable: boolean;
  facilityModeActive: boolean;
  defaultRoute: string;
}

export const SESSION_EXPERIENCE_CONTRACT_VERSION = "1.0.0";
