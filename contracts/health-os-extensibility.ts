/**
 * Health OS Extensibility Contract — canonical TypeScript taxonomy.
 *
 * Implements the doctrine defined in
 * `docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md`.
 *
 * This file is the **single source of truth** for the capability classes,
 * marketplace listing model, integration registration model, and
 * service-to-service trust model on the TypeScript side.
 *
 * Java counterparts live in:
 *   - libs/tech-companion/.../CompanionHeaders.java (headers)
 *   - services/msika-apps-service/.../domain (marketplace entities)
 *   - services/integration-hub/.../domain (governance entities)
 *
 * JSON Schemas for installable artefacts live in
 * `contracts/schemas/*-manifest.schema.json`.
 */

import type {
  FacilityId,
  HealthId,
  ProgrammeId,
  PurposeOfUse,
  WorkspaceId,
} from "./health-os-identifiers";

// ─────────────────────────────────────────────────────────────────────────────
// 1. Caller classes (mutually exclusive)
// ─────────────────────────────────────────────────────────────────────────────

export type CallerClass =
  | "INTERNAL_SOVEREIGN_SERVICE"
  | "EXPERIENCE_LAYER"
  | "EXTERNAL_INSTITUTIONAL_SYSTEM"
  | "MARKETPLACE_APP"
  | "CITIZEN_MEDIATED_PROXY";

// ─────────────────────────────────────────────────────────────────────────────
// 2. Capability classes (extensibility taxonomy)
// ─────────────────────────────────────────────────────────────────────────────

export type CapabilityType =
  | "APP"
  | "EXTENSION"
  | "PLUGIN"
  | "CONNECTOR"
  | "ADAPTER"
  | "WORKFLOW_PACK"
  | "CONTENT_PACK"
  | "AI_SKILL"
  | "DEVICE_INTEGRATION";

export type CapabilityCategory =
  | "CLINICAL"
  | "DIAGNOSTICS"
  | "PHARMACY"
  | "PUBLIC_HEALTH"
  | "TELEMEDICINE"
  | "IMAGING"
  | "LABORATORY"
  | "LOGISTICS"
  | "PAYMENT"
  | "CLAIMS"
  | "COMMUNICATIONS"
  | "AI"
  | "TRAINING"
  | "WELLNESS"
  | "COMMUNITY"
  | "REGISTRY"
  | "DEVICE"
  | "OTHER";

export type EnvironmentScope = "SANDBOX" | "STAGING" | "PRODUCTION";

export type IntegrationCategory =
  | "IMAGING_PACS_RIS"
  | "LABORATORY_LIMS"
  | "LOGISTICS_ELMIS"
  | "TELEMEDICINE_VIDEO"
  | "COMMUNICATIONS_OMNICHANNEL"
  | "PAYMENTS"
  | "CLAIMS_SWITCH"
  | "INSURANCE"
  | "DHIS2"
  | "CRVS_NATIONAL_ID"
  | "BIOMETRIC"
  | "PHARMACY_EXTERNAL"
  | "EPRESCRIBING"
  | "SURVEILLANCE_EXTERNAL"
  | "PORTS_OF_ENTRY"
  | "SCHOOL_HEALTH"
  | "COMMUNITY_HEALTH"
  | "EMERGENCY_RESPONSE"
  | "CALL_CENTRE_HELPDESK"
  | "LMS_EXTERNAL"
  | "DATA_WAREHOUSE_EXTERNAL"
  | "ANALYTICS_BI"
  | "AI_DECISION_SUPPORT"
  | "IOT_WEARABLE"
  | "REMOTE_MONITORING"
  | "AMBULANCE_TRANSPORT"
  | "REGULATORY_COUNCIL"
  | "FACILITY_LICENSING"
  | "MARKETPLACE_VENDOR"
  | "DONOR_PROGRAMME"
  | "OTHER";

// ─────────────────────────────────────────────────────────────────────────────
// 3. Visibility, lifecycle, classification
// ─────────────────────────────────────────────────────────────────────────────

export type MarketplaceVisibility =
  | "PUBLIC"
  | "MOHCC_ONLY"
  | "FACILITY_ONLY"
  | "PROGRAMME_ONLY"
  | "PROVINCE_ONLY"
  | "DISTRICT_ONLY"
  | "PRIVATE_SECTOR_ONLY"
  | "SANDBOX_ONLY"
  | "DEVELOPER_PREVIEW"
  | "DEPRECATED"
  | "SUSPENDED";

export type MarketplaceItemLifecycle =
  | "LISTED"
  | "REQUESTED"
  | "IN_REVIEW"
  | "APPROVED"
  | "INSTALLED"
  | "CONFIGURED"
  | "ACTIVE"
  | "SUSPENDED"
  | "DEPRECATED"
  | "REJECTED";

export type SecurityClassification = "LOW" | "STANDARD" | "ELEVATED" | "CRITICAL";

export type ClinicalSafetyClassification =
  | "NON_CLINICAL"
  | "CLINICAL_INFORMATIONAL"
  | "CLINICAL_DECISION_SUPPORT"
  | "CLINICAL_DIRECT_CARE";

export type DataProtectionClassification =
  | "NO_PHI"
  | "PSEUDONYMOUS"
  | "PHI_LIMITED"
  | "PHI_FULL";

// ─────────────────────────────────────────────────────────────────────────────
// 4. Marketplace listing model
// ─────────────────────────────────────────────────────────────────────────────

export type MarketplaceItemId = string;
export type PublisherId = string;
export type InstallationId = string;
export type ActivationRequestId = string;
export type IntegrationContractId = string;
export type ExternalAppId = string;
export type WebhookSubscriptionId = string;
export type EventCatalogueEntryId = string;
export type ServiceToServiceContractId = string;
export type AdapterId = string;

export interface MarketplacePublisher {
  id: PublisherId;
  name: string;
  legalName: string;
  websiteUrl?: string;
  technicalContact: ContactPoint;
  dataProtectionContact?: ContactPoint;
  supportContact?: ContactPoint;
  status: "ACTIVE" | "SUSPENDED" | "REVOKED";
  verifiedAt?: string;
  createdAt: string;
}

export interface ContactPoint {
  name: string;
  email: string;
  phone?: string;
  role?: string;
}

export interface MarketplaceItem {
  id: MarketplaceItemId;
  itemCode: string;
  name: string;
  description: string;
  type: CapabilityType;
  category: CapabilityCategory;
  publisherId: PublisherId;
  version: string;
  compatibilityVersion: string;
  supportedEnvironments: EnvironmentScope[];
  visibility: MarketplaceVisibility;
  iconRef?: string;
  documentationUrl?: string;
  manifestRef: string;
  securityClassification: SecurityClassification;
  dataProtectionClassification: DataProtectionClassification;
  clinicalSafetyClassification?: ClinicalSafetyClassification;
  approvalStatus: "DRAFT" | "PENDING_REVIEW" | "APPROVED" | "REJECTED";
  certificationStatus?: "NONE" | "SELF_CERTIFIED" | "PLATFORM_CERTIFIED";
  regulatoryStatus?: string;
  pricingModel?: "FREE" | "PER_FACILITY" | "PER_USER" | "TRANSACTION_BASED" | "CONTRACT";
  licensingModel?: string;
  requiredScopes: string[];
  requiredEvents: string[];
  requiredApis: string[];
  requiredDataCategories: string[];
  requiredPermissions: string[];
  facilityTypeWhitelist?: string[];
  rolesAllowed?: string[];
  consentRequirements?: string[];
  healthStatus: "HEALTHY" | "DEGRADED" | "UNAVAILABLE" | "UNKNOWN";
  lastUpdated: string;
  deprecationDate?: string;
}

export interface MarketplaceActivationRequest {
  id: ActivationRequestId;
  itemId: MarketplaceItemId;
  requesterActorId: HealthId;
  requesterTenantId: string;
  requesterFacilityId?: FacilityId;
  requesterWorkspaceId?: WorkspaceId;
  requesterProgrammeId?: ProgrammeId;
  purposeOfUse: PurposeOfUse;
  justification: string;
  requestedConfiguration?: Record<string, unknown>;
  status: "REQUESTED" | "IN_REVIEW" | "APPROVED" | "REJECTED" | "WITHDRAWN";
  reviewers: ApprovalRecord[];
  decidedAt?: string;
  decisionReason?: string;
  correlationId: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApprovalRecord {
  reviewerActorId: HealthId;
  reviewerRole: string;
  approvalDomain:
    | "DATA_GOVERNANCE"
    | "SECURITY"
    | "TENANT_ADMIN"
    | "CLINICAL_SAFETY"
    | "COMMERCIAL"
    | "EDITORIAL"
    | "AI_GOVERNANCE"
    | "ENGINEERING";
  decision: "PENDING" | "APPROVED" | "REJECTED" | "CHANGES_REQUESTED";
  decidedAt?: string;
  notes?: string;
}

export interface MarketplaceInstallation {
  id: InstallationId;
  itemId: MarketplaceItemId;
  itemVersion: string;
  tenantId: string;
  facilityIds: FacilityId[];
  workspaceIds?: WorkspaceId[];
  programmeIds?: ProgrammeId[];
  rolesEnabled?: string[];
  configuration: Record<string, unknown>;
  integrationContractId?: IntegrationContractId;
  externalAppId?: ExternalAppId;
  lifecycle: MarketplaceItemLifecycle;
  installedBy: HealthId;
  installedAt: string;
  configuredAt?: string;
  activatedAt?: string;
  suspendedAt?: string;
  suspendedReason?: string;
  lastHealthCheckAt?: string;
  healthStatus: "HEALTHY" | "DEGRADED" | "UNAVAILABLE" | "UNKNOWN";
  metadata?: Record<string, unknown>;
}

export interface MarketplaceAuditEvent {
  id: string;
  itemId: MarketplaceItemId;
  installationId?: InstallationId;
  activationRequestId?: ActivationRequestId;
  externalAppId?: ExternalAppId;
  eventType:
    | "ITEM_LISTED"
    | "ITEM_UPDATED"
    | "ITEM_DEPRECATED"
    | "ACTIVATION_REQUESTED"
    | "ACTIVATION_APPROVED"
    | "ACTIVATION_REJECTED"
    | "INSTALLATION_CREATED"
    | "INSTALLATION_CONFIGURED"
    | "INSTALLATION_ACTIVATED"
    | "INSTALLATION_SUSPENDED"
    | "INSTALLATION_REINSTATED"
    | "INSTALLATION_UNINSTALLED"
    | "VERSION_UPDATED";
  actorId: HealthId;
  actorType: string;
  tenantId: string;
  facilityId?: FacilityId;
  reason?: string;
  correlationId: string;
  occurredAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. External application registration
// ─────────────────────────────────────────────────────────────────────────────

export type ExternalAppStatus =
  | "REGISTERED"
  | "PENDING_REVIEW"
  | "APPROVED_SANDBOX"
  | "APPROVED_STAGING"
  | "APPROVED_PRODUCTION"
  | "SUSPENDED"
  | "REVOKED";

export type AuthenticationMethod =
  | "OAUTH2_CLIENT_CREDENTIALS"
  | "OAUTH2_AUTHORIZATION_CODE_PKCE"
  | "MTLS"
  | "API_KEY_SANDBOX_ONLY";

export type WebhookSignatureMethod = "HMAC_SHA256" | "ED25519" | "NONE_SANDBOX_ONLY";

export interface ExternalApplication {
  id: ExternalAppId;
  appCode: string;
  name: string;
  description: string;
  publisherId: PublisherId;
  publisherName: string;
  technicalContact: ContactPoint;
  dataProtectionContact?: ContactPoint;
  status: ExternalAppStatus;
  environment: EnvironmentScope;
  integrationCategory: IntegrationCategory;
  riskClassification: SecurityClassification;
  authenticationMethod: AuthenticationMethod;
  oauthClientId?: string;
  /** never returned to clients; rotated through Integration Service */
  oauthClientSecretRef?: string;
  mtlsCertificateFingerprint?: string;
  ipAllowlist?: string[];
  allowedTenants: string[];
  allowedFacilities?: FacilityId[];
  rateLimit?: {
    requestsPerMinute: number;
    burst?: number;
  };
  credentialExpiresAt?: string;
  registeredAt: string;
  approvedAt?: string;
  approvedBy?: HealthId;
  suspendedAt?: string;
  suspendedReason?: string;
  revokedAt?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Integration contract
// ─────────────────────────────────────────────────────────────────────────────

export interface IntegrationContract {
  id: IntegrationContractId;
  externalAppId: ExternalAppId;
  contractCode: string;
  version: string;
  integrationCategory: IntegrationCategory;
  environment: EnvironmentScope;
  effectiveFrom: string;
  effectiveUntil?: string;
  allowedApis: string[];
  allowedEvents: string[];
  allowedWebhookCallbacks: string[];
  scopes: string[];
  purposeOfUse: PurposeOfUse[];
  consentBasis?:
    | "PATIENT_CONSENT_REQUIRED"
    | "LEGAL_OBLIGATION"
    | "PUBLIC_INTEREST"
    | "VITAL_INTEREST"
    | "LEGITIMATE_INTEREST"
    | "NOT_APPLICABLE";
  signatureMethod: WebhookSignatureMethod;
  rateLimit?: {
    requestsPerMinute: number;
    burst?: number;
  };
  errorHandlingPolicy: "FAIL_OPEN_SANDBOX" | "FAIL_CLOSED_PRODUCTION";
  dataRetentionDays?: number;
  incidentReportingHours?: number;
  status: "DRAFT" | "ACTIVE" | "EXPIRED" | "SUSPENDED" | "TERMINATED";
  signedBy?: ContactPoint;
  signedAt?: string;
  documentRef?: string;
  createdAt: string;
  updatedAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. Service-to-service contract (internal trust pattern)
// ─────────────────────────────────────────────────────────────────────────────

export type RequestSource =
  | "HUMAN"
  | "SYSTEM"
  | "SCHEDULED_JOB"
  | "BACKGROUND_WORKER"
  | "EVENT_CONSUMER"
  | "AI_ASSISTED"
  | "EXTERNAL_APP";

export interface ServiceToServiceContract {
  id: ServiceToServiceContractId;
  callerServiceId: string;
  calleeServiceId: string;
  contractVersion: string;
  allowedOperations: string[];
  allowedScopes: string[];
  allowedEventTopics: string[];
  allowedRequestSources: RequestSource[];
  rateLimit?: {
    requestsPerMinute: number;
    burst?: number;
  };
  ownerTeam: string;
  supportContact: ContactPoint;
  lastVerifiedAt?: string;
  status: "DRAFT" | "ACTIVE" | "DEPRECATED";
  createdAt: string;
  updatedAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. Webhook subscriptions
// ─────────────────────────────────────────────────────────────────────────────

export interface WebhookSubscription {
  id: WebhookSubscriptionId;
  externalAppId: ExternalAppId;
  integrationContractId: IntegrationContractId;
  eventTopics: string[];
  deliveryUrl: string;
  /** never returned to clients */
  signatureSecretRef: string;
  signatureMethod: WebhookSignatureMethod;
  active: boolean;
  filter?: {
    facilityIds?: FacilityId[];
    programmeIds?: ProgrammeId[];
    extra?: Record<string, unknown>;
  };
  retryPolicy: {
    maxRetries: number;
    initialDelayMs: number;
    maxDelayMs: number;
  };
  deadLetterAfterRetries: number;
  lastDeliveryAt?: string;
  lastDeliveryStatus?: "SUCCESS" | "FAILED" | "RETRYING";
  consecutiveFailures: number;
  createdAt: string;
  updatedAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 9. Event catalogue (internal vs externally-publishable)
// ─────────────────────────────────────────────────────────────────────────────

export type EventClassification = "INTERNAL_ONLY" | "EXTERNALLY_PUBLISHABLE";

export type SensitivityTier = "LOW" | "STANDARD" | "ELEVATED" | "CRITICAL";

export interface EventCatalogueEntry {
  id: EventCatalogueEntryId;
  topic: string;
  description: string;
  classification: EventClassification;
  sensitivityTier: SensitivityTier;
  ownerServiceId: string;
  schemaRef: string;
  schemaVersion: string;
  partnerPublishable: boolean;
  containsPii: boolean;
  containsPhi: boolean;
  sampleReferenceRef?: string;
  deprecated: boolean;
  deprecatedFrom?: string;
  introducedAt: string;
  updatedAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 10. External event subscriptions (per external app projection)
// ─────────────────────────────────────────────────────────────────────────────

export interface ExternalEventSubscription {
  id: string;
  externalAppId: ExternalAppId;
  integrationContractId: IntegrationContractId;
  webhookSubscriptionId: WebhookSubscriptionId;
  eventTopic: string;
  status: "ACTIVE" | "PAUSED" | "REVOKED";
  approvedBy: HealthId;
  approvedAt: string;
  createdAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 11. Canonical integration ports (anti-vendor-lock-in)
// ─────────────────────────────────────────────────────────────────────────────

export type CanonicalPortName =
  | "ImagingIntegrationPort"
  | "LaboratoryIntegrationPort"
  | "LogisticsIntegrationPort"
  | "TelemedicineIntegrationPort"
  | "OmnichannelIntegrationPort"
  | "PaymentIntegrationPort"
  | "MapsIntegrationPort"
  | "AIProviderPort"
  | "DeviceIntegrationPort";

export interface CanonicalPortRegistration {
  id: string;
  portName: CanonicalPortName;
  description: string;
  ownerServiceId: string;
  defaultAdapterId?: AdapterId;
  registeredAdapters: AdapterId[];
}

// ─────────────────────────────────────────────────────────────────────────────
// 12. Launcher (Health OS App Switcher) response model
// ─────────────────────────────────────────────────────────────────────────────

export type LauncherAppState =
  | "AVAILABLE"
  | "INSTALLED"
  | "REQUEST_ACCESS"
  | "PENDING_APPROVAL"
  | "NOT_AVAILABLE_AT_FACILITY"
  | "REQUIRES_CONFIGURATION"
  | "TEMPORARILY_UNAVAILABLE"
  | "SUSPENDED"
  | "DEPRECATED";

export interface LauncherApp {
  id: string;
  appCode: string;
  name: string;
  description: string;
  icon?: string;
  href: string;
  category: string;
  state: LauncherAppState;
  /** When the entry comes from a marketplace installation, this is the item id */
  marketplaceItemId?: MarketplaceItemId;
  installationId?: InstallationId;
  /** True if this is a Core Platform Service (not removable) */
  systemAppFlag: boolean;
  /** Sort weight within category */
  weight?: number;
  requiredRole?: string;
  pinned?: boolean;
  recentlyUsed?: boolean;
  stateExplanation?: string;
  metadata?: Record<string, unknown>;
}

export interface LauncherResponse {
  actor: {
    actorId: HealthId;
    actorType: string;
    tenantId: string;
    facilityId?: FacilityId;
    workspaceId?: WorkspaceId;
  };
  apps: LauncherApp[];
  generatedAt: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// 13. AI Skill manifest (Nompilo extensibility)
// ─────────────────────────────────────────────────────────────────────────────

export interface AISkillTool {
  /** Stable tool identifier exposed to the LLM orchestration layer. */
  name: string;
  description: string;
  /** Coarse-grained category of side-effects this tool can produce. */
  effect: "READ_ONLY" | "WRITE_LOW_RISK" | "WRITE_HIGH_RISK" | "INVOKE_EXTERNAL";
  /** TSHEPO scopes required to invoke this tool. */
  requiredScopes: string[];
  /** Roles the calling actor must hold for this tool to be exposed. */
  rolesAllowed: string[];
  /**
   * If true, the orchestrator MUST present a confirmation step before
   * executing the tool. Defaults to true for any write-effect tool.
   */
  requiresConfirmation?: boolean;
  /**
   * Backend service that owns the tool implementation. The LLM never calls
   * the service directly; it goes through Nompilo's authenticated tool proxy.
   */
  implementedBy: {
    serviceId: string;
    className: string;
    methodName: string;
  };
  inputSchemaRef?: string;
  outputSchemaRef?: string;
}

export interface AISkillManifest {
  /** Stable id, registered in the marketplace as a CapabilityType "AI_SKILL". */
  itemCode: string;
  name: string;
  version: string;
  description: string;
  publisherId: PublisherId;
  /** Default visibility — marketplace governance can override per-tenant. */
  defaultVisibility: MarketplaceVisibility;
  /** Roles the AI skill is available to. */
  rolesAllowed: string[];
  /** Tools (deterministic operations) exposed to the LLM. */
  tools: AISkillTool[];
  /**
   * Permitted purpose-of-use values for any tool invocation. Empty means
   * inherit the caller's purpose; otherwise restricted to this set.
   */
  permittedPurposesOfUse: PurposeOfUse[];
  /** TSHEPO policy reference enforced at the Nompilo orchestration boundary. */
  tshepoPolicyRef: string;
  /**
   * If true, this skill is allowed to access PHI when invoked under a
   * permitted purpose-of-use. Defaults to false.
   */
  phiAccess?: boolean;
  /** Free-text safety doctrine reference for reviewers. */
  safetyDoctrineRef?: string;
  /**
   * High-impact actions this skill must NEVER attempt autonomously, even if
   * tools technically allow it. Enforced by Nompilo at the orchestration
   * boundary.
   */
  prohibitedAutonomousActions: string[];
  documentationUrl?: string;
  supportContact: ContactPoint;
}
