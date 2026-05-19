/**
 * Health OS Capability Marketplace — Manifest Contracts (TypeScript).
 *
 * Every installable artefact in the Capability Marketplace is described
 * by a manifest. Each manifest type has both a TypeScript definition (here)
 * and a JSON Schema at `contracts/schemas/<type>-manifest.schema.json`
 * (the schema is the runtime validation source of truth; this file is the
 * IDE / SDK-friendly definition).
 *
 * Doctrine: docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md §3
 */

import type {
  CapabilityCategory,
  CapabilityType,
  ContactPoint,
  DataProtectionClassification,
  EnvironmentScope,
  PublisherId,
  SecurityClassification,
  ClinicalSafetyClassification,
  IntegrationCategory,
} from "./health-os-extensibility";

// ─────────────────────────────────────────────────────────────────────────────
// Base manifest fields (every manifest extends this)
// ─────────────────────────────────────────────────────────────────────────────

export interface BaseManifest {
  /** Manifest schema version, e.g. "1.0" */
  manifestVersion: string;
  /** Globally unique stable code for the artefact */
  artifactCode: string;
  /** Capability type discriminator */
  type: CapabilityType;
  /** Human-readable name */
  name: string;
  /** Short description (<=280 chars) */
  description: string;
  category: CapabilityCategory;
  publisher: {
    id: PublisherId;
    name: string;
    legalName?: string;
    websiteUrl?: string;
    technicalContact: ContactPoint;
    dataProtectionContact?: ContactPoint;
    supportContact?: ContactPoint;
  };
  /** Semantic version of THIS artefact */
  version: string;
  /** Minimum Health OS platform version required */
  platformCompatibility: string;
  supportedEnvironments: EnvironmentScope[];
  iconRef?: string;
  documentationUrl?: string;
  securityClassification: SecurityClassification;
  dataProtectionClassification: DataProtectionClassification;
  clinicalSafetyClassification?: ClinicalSafetyClassification;
  /** Required Health OS API scopes */
  requiredScopes: string[];
  /** Required event topic subscriptions */
  requiredEvents?: string[];
  /** Allowed roles that may invoke or see this artefact */
  rolesAllowed?: string[];
  /** Optional pricing/licensing notes */
  pricingModel?: "FREE" | "PER_FACILITY" | "PER_USER" | "TRANSACTION_BASED" | "CONTRACT";
  licensingModel?: string;
  /** Cryptographic checksum of the manifest body (for tamper detection) */
  checksum?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// App manifest — full user-facing capability
// ─────────────────────────────────────────────────────────────────────────────

export interface AppManifest extends BaseManifest {
  type: "APP";
  /** Launcher entry definition */
  launcher: {
    appCode: string;
    href: string;
    icon: string;
    weight?: number;
    pinByDefault?: boolean;
  };
  /** APIs the app calls (canonical Impilo services only) */
  requiredApis: string[];
  /** Web surfaces declared by the app */
  webSurfaces?: Array<{
    routePattern: string;
    title: string;
    requiredRole?: string;
  }>;
  /** Mobile surfaces declared by the app */
  mobileSurfaces?: Array<{
    platform: "CITIZEN_APP" | "PROVIDER_APP";
    routeKey: string;
    title: string;
    requiredRole?: string;
  }>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension manifest — bounded capability augmenting a sovereign service
// ─────────────────────────────────────────────────────────────────────────────

export interface ExtensionManifest extends BaseManifest {
  type: "EXTENSION";
  extendsServiceId: string;
  extensionPoint:
    | "CLINICAL_RULE"
    | "CLINICAL_FORM"
    | "WORKFLOW"
    | "DASHBOARD_TILE"
    | "REPORT"
    | "WORKSPACE_PANEL"
    | "NOMPILO_SKILL_HOOK"
    | "OTHER";
  declaredRules?: string[];
  declaredForms?: string[];
  declaredWorkflows?: string[];
  declaredDashboards?: string[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Plugin manifest — small capability inside an approved frame
// ─────────────────────────────────────────────────────────────────────────────

export type PluginRuntime = "FRONTEND_REACT" | "BACKEND_RULE" | "BACKEND_FORM" | "EDGE_OFFLINE";

export interface PluginManifest extends BaseManifest {
  type: "PLUGIN";
  runtime: PluginRuntime;
  /** Mount-point within an existing screen, when frontend */
  mountPoint?: string;
  /** Asset reference (signed bundle) — never inline JS */
  assetBundleRef?: string;
  /** Backend rule identifier — when runtime is BACKEND_RULE */
  ruleId?: string;
  /** Strict allow-list of proxied API paths the plugin may call */
  allowedProxyPaths?: string[];
  /** When true, requires explicit user confirmation before any state-changing call */
  requiresConfirmation?: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Connector manifest — bridge to a named external system
// ─────────────────────────────────────────────────────────────────────────────

export interface ConnectorManifest extends BaseManifest {
  type: "CONNECTOR";
  integrationCategory: IntegrationCategory;
  externalSystemName: string;
  externalSystemVersion?: string;
  authenticationMethod:
    | "OAUTH2_CLIENT_CREDENTIALS"
    | "OAUTH2_AUTHORIZATION_CODE_PKCE"
    | "MTLS"
    | "API_KEY_SANDBOX_ONLY";
  signatureMethod: "HMAC_SHA256" | "ED25519" | "NONE_SANDBOX_ONLY";
  endpoints: Array<{
    name: string;
    url: string;
    method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  }>;
  /** Webhook topics the connector emits inward (external → Impilo) */
  inboundWebhookTopics?: string[];
  /** Health OS event topics the connector consumes for outbound delivery */
  outboundEventTopics?: string[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter manifest — vendor-specific implementation behind a canonical port
// ─────────────────────────────────────────────────────────────────────────────

export interface AdapterManifest extends BaseManifest {
  type: "ADAPTER";
  canonicalPort:
    | "ImagingIntegrationPort"
    | "LaboratoryIntegrationPort"
    | "LogisticsIntegrationPort"
    | "TelemedicineIntegrationPort"
    | "OmnichannelIntegrationPort"
    | "PaymentIntegrationPort"
    | "MapsIntegrationPort"
    | "AIProviderPort"
    | "DeviceIntegrationPort";
  adapterImplementationClass: string;
  vendorName: string;
  vendorVersion?: string;
  /** Whether this adapter is the default for its port (only one may be true at a time per tenant) */
  defaultForPort?: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Workflow Pack manifest
// ─────────────────────────────────────────────────────────────────────────────

export interface WorkflowPackManifest extends BaseManifest {
  type: "WORKFLOW_PACK";
  workflows: Array<{
    workflowCode: string;
    name: string;
    description: string;
  }>;
  queues?: Array<{
    queueCode: string;
    name: string;
  }>;
  forms?: string[];
  dashboards?: string[];
  rolesIntroduced?: string[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Content Pack manifest
// ─────────────────────────────────────────────────────────────────────────────

export interface ContentPackManifest extends BaseManifest {
  type: "CONTENT_PACK";
  contentCategory:
    | "SMS_TEMPLATES"
    | "WHATSAPP_TEMPLATES"
    | "EMAIL_TEMPLATES"
    | "KNOWLEDGE_SNIPPETS"
    | "COURSE_CONTENT"
    | "NOMPILO_KB"
    | "PUBLIC_HEALTH_CAMPAIGN"
    | "LOCAL_LANGUAGE_PACK";
  itemCount: number;
  languageCodes: string[];
  bundleRef: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Skill manifest
// ─────────────────────────────────────────────────────────────────────────────

export interface AISkillManifest extends BaseManifest {
  type: "AI_SKILL";
  modelRegistryRef: string;
  modelVersion: string;
  invocationSurface: "NOMPILO" | "EXPERIENCE_BFF" | "MOBILE_APP" | "ADMIN_CONSOLE";
  allowedRoles: string[];
  allowedPurposes: string[];
  declaredDataAccess: Array<{
    dataCategory: string;
    accessMode: "READ" | "REFERENCE_ONLY";
  }>;
  allowedActions: Array<{
    actionCode: string;
    description: string;
    requiresConfirmation: boolean;
  }>;
  outputClassification: "INFORMATIONAL" | "RECOMMENDATION" | "ALERT";
  explainability: "TEMPLATED" | "PROMPT_TRACED" | "FULL_REASONING_CHAIN";
}

// ─────────────────────────────────────────────────────────────────────────────
// Device Integration manifest
// ─────────────────────────────────────────────────────────────────────────────

export interface DeviceIntegrationManifest extends BaseManifest {
  type: "DEVICE_INTEGRATION";
  deviceCategory:
    | "BIOMETRIC"
    | "VITALS_GLUCOMETER"
    | "VITALS_BP"
    | "VITALS_SPO2"
    | "VITALS_THERMOMETER"
    | "COLD_CHAIN_SENSOR"
    | "VEHICLE_TRACKER"
    | "DRONE_TELEMETRY"
    | "WEARABLE_FITNESS"
    | "REMOTE_MONITORING"
    | "OTHER";
  vendorName: string;
  ingestionProtocol: "MQTT" | "HTTP_WEBHOOK" | "HEALTH_CONNECT" | "BLE" | "OTHER";
  ingestionEventTopic: string;
  requiresDeviceProvisioning: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Union and type guards
// ─────────────────────────────────────────────────────────────────────────────

export type AnyManifest =
  | AppManifest
  | ExtensionManifest
  | PluginManifest
  | ConnectorManifest
  | AdapterManifest
  | WorkflowPackManifest
  | ContentPackManifest
  | AISkillManifest
  | DeviceIntegrationManifest;

export function isAppManifest(m: AnyManifest): m is AppManifest {
  return m.type === "APP";
}

export function isConnectorManifest(m: AnyManifest): m is ConnectorManifest {
  return m.type === "CONNECTOR";
}

export function isAdapterManifest(m: AnyManifest): m is AdapterManifest {
  return m.type === "ADAPTER";
}

export function isAISkillManifest(m: AnyManifest): m is AISkillManifest {
  return m.type === "AI_SKILL";
}

export function isDeviceIntegrationManifest(
  m: AnyManifest,
): m is DeviceIntegrationManifest {
  return m.type === "DEVICE_INTEGRATION";
}
