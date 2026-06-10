/**
 * Trust catalogue schema — versioned seed entries consumed by BFF, UI, mobile, OPA, tests.
 */

export type CatalogueEntryStatus = "active" | "deprecated" | "draft";

export type AuditSensitivity = "low" | "medium" | "high" | "critical" | "standard" | "elevated" | "registry" | "clinical" | "break_glass";

export interface StandardMapping {
  openHie?: string;
  fhir?: string;
  whoDak?: string;
  opa?: string;
  nist?: string;
}

export interface CatalogueEntry {
  id: string;
  code: string;
  displayName: string;
  description?: string;
  category?: string;
  status: CatalogueEntryStatus;
  ownerService?: string;
  policyMapping?: string;
  /** @deprecated prefer standardMapping.fhir */
  fhirMapping?: string;
  /** @deprecated prefer standardMapping.openHie */
  openHieMapping?: string;
  standardMapping?: StandardMapping;
  auditSensitivity?: AuditSensitivity;
  metadata?: Record<string, unknown>;
}

export interface TrustCatalogueBundle {
  version: string;
  catalogueId: string;
  displayName: string;
  entries: CatalogueEntry[];
}

export interface RoleTemplateSeed extends CatalogueEntry {
  roleTemplateId: string;
  roleCategory: string;
  allowedContextTypes?: string[];
  allowedWorkspaces?: string[];
  requiredProviderWorkerTypes?: string[];
  requiredCadres?: string[];
  requiredLicenceStatus?: string[];
  defaultPermissions?: string[];
  restrictedPermissions?: string[];
  opaPolicyMapping?: string;
  canAssignRoles?: boolean;
  canManageLocalAccess?: boolean;
  canManageRegistry?: boolean;
  canApproveMarketplace?: boolean;
  canAccessSensitiveData?: boolean;
}

export interface ProfessionCadreSeed extends CatalogueEntry {
  clinicalOrNonClinical: "clinical" | "non_clinical" | "mixed";
  defaultEligibleContextTypes?: string[];
  defaultRoleTemplates?: string[];
  requiredLicenceCategory?: string;
  requiredTraining?: string[];
}

export interface PermissionSeed extends CatalogueEntry {
  domain: string;
}

export interface FriendlyResolutionSeed extends CatalogueEntry {
  userTitle: string;
  explanation: string;
  allowedActions: string[];
  escalationPath?: string;
  auditCode: string;
}
