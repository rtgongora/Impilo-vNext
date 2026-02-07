/**
 * ZIBO API service layer.
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> ZIBO.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types -- Artifact
// ---------------------------------------------------------------------------

export type ArtifactStatus = "DRAFT" | "ACTIVE" | "DEPRECATED" | "RETIRED";

export type FhirType =
  | "CodeSystem"
  | "ValueSet"
  | "ConceptMap"
  | "NamingSystem"
  | "StructureDefinition"
  | "SearchParameter"
  | "OperationDefinition"
  | "CapabilityStatement"
  | "ImplementationGuide"
  | "Questionnaire";

export interface Artifact {
  id: string;
  tenantId: string;
  fhirType: FhirType;
  canonicalUrl: string;
  name: string;
  title: string;
  version: string;
  status: ArtifactStatus;
  publisher: string | null;
  description: string | null;
  conceptCount: number | null;
  sourceFormat: "FHIR_JSON" | "FHIR_XML" | "CSV" | "CUSTOM";
  contentHash: string;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  deprecatedAt: string | null;
}

export interface ArtifactCreateRequest {
  fhirType: FhirType;
  canonicalUrl: string;
  name: string;
  title: string;
  version: string;
  publisher?: string;
  description?: string;
  content: string;
  sourceFormat: "FHIR_JSON" | "FHIR_XML" | "CSV" | "CUSTOM";
}

export interface ArtifactUpdateRequest {
  title?: string;
  description?: string;
  publisher?: string;
  content?: string;
}

export interface ArtifactVersion {
  version: string;
  status: ArtifactStatus;
  publishedAt: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Types -- Pack
// ---------------------------------------------------------------------------

export type PackStatus = "DRAFT" | "ACTIVE" | "DEPRECATED" | "RETIRED";

export interface Pack {
  id: string;
  tenantId: string;
  name: string;
  version: string;
  description: string | null;
  status: PackStatus;
  artifactCount: number;
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface PackCreateRequest {
  name: string;
  version: string;
  description?: string;
}

export interface PackArtifact {
  artifactId: string;
  canonicalUrl: string;
  fhirType: FhirType;
  name: string;
  version: string;
  status: ArtifactStatus;
}

// ---------------------------------------------------------------------------
// Types -- Mapping
// ---------------------------------------------------------------------------

export interface MapRequest {
  sourceSystem: string;
  sourceCode: string;
  targetSystem?: string;
}

export interface MapResult {
  sourceSystem: string;
  sourceCode: string;
  sourceDisplay: string | null;
  targetSystem: string;
  targetCode: string;
  targetDisplay: string | null;
  equivalence: "equivalent" | "wider" | "narrower" | "inexact" | "unmatched" | "disjoint";
  conceptMapUrl: string;
  conceptMapVersion: string;
}

export interface MapSource {
  system: string;
  display: string;
  conceptMapCount: number;
}

// ---------------------------------------------------------------------------
// Types -- Validation
// ---------------------------------------------------------------------------

export type ValidationSeverity = "ERROR" | "WARNING" | "INFO";

export interface ValidateCodingRequest {
  system: string;
  code: string;
  display?: string;
  valueSetUrl?: string;
}

export interface ValidateCodingResult {
  valid: boolean;
  severity: ValidationSeverity;
  message: string;
  system: string;
  code: string;
  display: string | null;
  valueSetUrl: string | null;
}

export interface ValidateResourceRequest {
  resourceType: string;
  resource: string;
  profileUrl?: string;
}

export interface ValidateResourceResult {
  valid: boolean;
  issueCount: number;
  issues: ValidationIssue[];
}

export interface ValidationIssue {
  severity: ValidationSeverity;
  code: string;
  diagnostics: string;
  location: string | null;
}

export interface ValidationJob {
  id: string;
  tenantId: string;
  status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
  totalResources: number;
  processedResources: number;
  errorCount: number;
  warningCount: number;
  submittedAt: string;
  completedAt: string | null;
}

// ---------------------------------------------------------------------------
// Types -- Assignment
// ---------------------------------------------------------------------------

export type PolicyMode = "ENFORCE" | "WARN" | "AUDIT";

export interface Assignment {
  id: string;
  tenantId: string;
  packId: string;
  packName: string;
  scope: string;
  scopeType: "FACILITY" | "WORKSPACE" | "SERVICE" | "TENANT";
  policyMode: PolicyMode;
  active: boolean;
  assignedAt: string;
  assignedBy: string;
}

export interface AssignRequest {
  packId: string;
  scope: string;
  scopeType: "FACILITY" | "WORKSPACE" | "SERVICE" | "TENANT";
  policyMode: PolicyMode;
}

// ---------------------------------------------------------------------------
// Types -- Governance
// ---------------------------------------------------------------------------

export interface ValidationLog {
  id: string;
  tenantId: string;
  timestamp: string;
  sourceService: string;
  resourceType: string;
  severity: ValidationSeverity;
  code: string;
  diagnostics: string;
  system: string | null;
  valueCode: string | null;
}

export interface TopFailure {
  system: string;
  code: string;
  display: string | null;
  count: number;
  lastSeen: string;
  severity: ValidationSeverity;
}

export interface GovernanceStats {
  totalValidations: number;
  errorCount: number;
  warningCount: number;
  infoCount: number;
  errorRate: number;
  topOffendingServices: ServiceErrorCount[];
  periodStart: string;
  periodEnd: string;
}

export interface ServiceErrorCount {
  service: string;
  errorCount: number;
  warningCount: number;
}

// ---------------------------------------------------------------------------
// Types -- Import / Export
// ---------------------------------------------------------------------------

export interface ImportResult {
  imported: number;
  skipped: number;
  errors: string[];
}

// ---------------------------------------------------------------------------
// API Functions
// ---------------------------------------------------------------------------

const V1 = "/v1";

export const ziboApi = {
  // -- Artifacts --
  listArtifacts: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<Artifact[]>(`${V1}/artifacts${qs}`);
  },

  getArtifact: (id: string) =>
    apiClient.get<Artifact>(`${V1}/artifacts/${encodeURIComponent(id)}`),

  createArtifact: (data: ArtifactCreateRequest) =>
    apiClient.post<Artifact>(`${V1}/artifacts`, data),

  updateArtifact: (id: string, data: ArtifactUpdateRequest) =>
    apiClient.put<Artifact>(`${V1}/artifacts/${encodeURIComponent(id)}`, data),

  publishArtifact: (id: string) =>
    apiClient.post<Artifact>(`${V1}/artifacts/${encodeURIComponent(id)}/publish`),

  deprecateArtifact: (id: string) =>
    apiClient.post<Artifact>(`${V1}/artifacts/${encodeURIComponent(id)}/deprecate`),

  retireArtifact: (id: string) =>
    apiClient.post<Artifact>(`${V1}/artifacts/${encodeURIComponent(id)}/retire`),

  getArtifactVersions: (id: string) =>
    apiClient.get<ArtifactVersion[]>(`${V1}/artifacts/${encodeURIComponent(id)}/versions`),

  resolveArtifact: (canonicalUrl: string, version?: string) => {
    const params = new URLSearchParams({ url: canonicalUrl });
    if (version) params.set("version", version);
    return apiClient.get<Artifact>(`${V1}/artifacts/resolve?${params.toString()}`);
  },

  // -- Packs --
  listPacks: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<Pack[]>(`${V1}/packs${qs}`);
  },

  getPack: (id: string) =>
    apiClient.get<Pack>(`${V1}/packs/${encodeURIComponent(id)}`),

  createPack: (data: PackCreateRequest) =>
    apiClient.post<Pack>(`${V1}/packs`, data),

  addArtifactToPack: (packId: string, artifactId: string) =>
    apiClient.post<Pack>(
      `${V1}/packs/${encodeURIComponent(packId)}/artifacts`,
      { artifactId },
    ),

  removeArtifactFromPack: (packId: string, artifactId: string) =>
    apiClient.delete<Pack>(
      `${V1}/packs/${encodeURIComponent(packId)}/artifacts/${encodeURIComponent(artifactId)}`,
    ),

  publishPack: (id: string) =>
    apiClient.post<Pack>(`${V1}/packs/${encodeURIComponent(id)}/publish`),

  deprecatePack: (id: string) =>
    apiClient.post<Pack>(`${V1}/packs/${encodeURIComponent(id)}/deprecate`),

  getPackArtifacts: (packId: string) =>
    apiClient.get<PackArtifact[]>(`${V1}/packs/${encodeURIComponent(packId)}/artifacts`),

  // -- Validation --
  validateCoding: (data: ValidateCodingRequest) =>
    apiClient.post<ValidateCodingResult>(`${V1}/validate/coding`, data),

  validateResource: (data: ValidateResourceRequest) =>
    apiClient.post<ValidateResourceResult>(`${V1}/validate/resource`, data),

  submitValidationJob: (data: { resources: string[]; profileUrl?: string }) =>
    apiClient.post<ValidationJob>(`${V1}/validate/job`, data),

  getValidationJob: (id: string) =>
    apiClient.get<ValidationJob>(`${V1}/validate/job/${encodeURIComponent(id)}`),

  // -- Mapping --
  mapCode: (data: MapRequest) =>
    apiClient.post<MapResult[]>(`${V1}/map`, data),

  getMapSources: (system?: string) => {
    const qs = system ? `?sourceSystem=${encodeURIComponent(system)}` : "";
    return apiClient.get<MapSource[]>(`${V1}/map/sources${qs}`);
  },

  rebuildMapIndex: () =>
    apiClient.post<void>(`${V1}/map/rebuild`),

  // -- Assignments --
  listAssignments: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<Assignment[]>(`${V1}/assignments${qs}`);
  },

  assignPack: (data: AssignRequest) =>
    apiClient.post<Assignment>(`${V1}/assignments`, data),

  unassignPack: (id: string) =>
    apiClient.delete<void>(`${V1}/assignments/${encodeURIComponent(id)}`),

  // -- Governance --
  getValidationLogs: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<ValidationLog[]>(`${V1}/governance/validation-logs${qs}`);
  },

  getTopFailures: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<TopFailure[]>(`${V1}/governance/top-failures${qs}`);
  },

  getGovernanceStats: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<GovernanceStats>(`${V1}/governance/stats${qs}`);
  },

  // -- Import / Export --
  importFhirBundle: (bundle: unknown) =>
    apiClient.post<ImportResult>(`${V1}/import/fhir-bundle`, bundle),

  importCsv: (data: { fhirType: FhirType; csvContent: string; canonicalUrl: string; name: string; version: string }) =>
    apiClient.post<ImportResult>(`${V1}/import/csv`, data),

  exportPackBundle: (packId: string) =>
    apiClient.get<unknown>(`${V1}/export/packs/${encodeURIComponent(packId)}/bundle`),

  exportArtifact: (artifactId: string) =>
    apiClient.get<unknown>(`${V1}/export/artifacts/${encodeURIComponent(artifactId)}`),
};
