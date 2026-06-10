/**
 * Web shell trust catalogue + session contract bindings.
 * Source of truth: contracts/trust/
 */

export {
  loadTrustCatalogueBundles,
  loadActiveCatalogueEntries,
  findCatalogueEntryByCode,
  findFriendlyResolution,
  findRoleTemplate,
  findPermission,
  validateRequiredCatalogueCodes,
  runAllCatalogueValidators,
  TRUST_CATALOGUE_VERSION,
  resolveSessionExperienceContract,
  sessionContractAllowsRoute,
  isActiveWorkAssignment,
  normalizeWorkAssignment,
  mapGovernanceAssignmentNode,
  providerStatusAllowsProfessional,
  providerStatusBlocksWork,
  SESSION_EXPERIENCE_CONTRACT_VERSION,
  ACTIVE_WORK_ASSIGNMENT_STATUSES,
} from "../../../../../contracts/trust";

export type {
  CatalogueEntry,
  TrustCatalogueBundle,
  WorkAssignment,
  WorkAssignmentStatus,
  ProviderProfessionalTruth,
  SessionExperienceContract,
  SessionExperienceInput,
  SessionTabDefinition,
  SessionIdentityType,
  SessionLoginMethod,
} from "../../../../../contracts/trust";
