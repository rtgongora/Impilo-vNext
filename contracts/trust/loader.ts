/**
 * Loads versioned trust seed catalogues for BFF, UI, mobile, OPA references, and tests.
 */

import type { CatalogueEntry, TrustCatalogueBundle } from "./catalogue-schema";
import identitySeed from "./seeds/identity.json";
import providerWorkerSeed from "./seeds/provider-worker.json";
import workContextSeed from "./seeds/work-context.json";
import permissionsSeed from "./seeds/permissions.json";
import governanceMarketplaceSeed from "./seeds/governance-marketplace.json";
import roleTemplatesSeed from "./seeds/role-templates.json";
import resolutionTabsOpaSeed from "./seeds/resolution-tabs-opa.json";
import zimbabweMohccSeed from "./seeds/zimbabwe-mohcc.json";
import regulatoryInstitutionsSeed from "./seeds/regulatory-institutions.json";
import organisationGovernanceSeed from "./seeds/organisation-governance.json";
import hscWorkforceGovernanceSeed from "./seeds/hsc-workforce-governance.json";
import vashandiWorkforceSeed from "./seeds/vashandi-workforce.json";
import bootstrapOnboardingSeed from "./seeds/bootstrap-onboarding.json";
import {
  REQUIRED_CATALOGUE_CODES,
  validateRequiredCatalogueCodes as validateRequiredCodes,
} from "./validators";

export const TRUST_CATALOGUE_VERSION = "1.6.0";

const ALL_BUNDLES: TrustCatalogueBundle[] = [
  identitySeed as TrustCatalogueBundle,
  providerWorkerSeed as TrustCatalogueBundle,
  zimbabweMohccSeed as TrustCatalogueBundle,
  regulatoryInstitutionsSeed as TrustCatalogueBundle,
  organisationGovernanceSeed as TrustCatalogueBundle,
  hscWorkforceGovernanceSeed as TrustCatalogueBundle,
  vashandiWorkforceSeed as TrustCatalogueBundle,
  workContextSeed as TrustCatalogueBundle,
  permissionsSeed as TrustCatalogueBundle,
  governanceMarketplaceSeed as TrustCatalogueBundle,
  roleTemplatesSeed as TrustCatalogueBundle,
  bootstrapOnboardingSeed as TrustCatalogueBundle,
  resolutionTabsOpaSeed as TrustCatalogueBundle,
];

export function loadTrustCatalogueBundles(): TrustCatalogueBundle[] {
  return ALL_BUNDLES;
}

export function loadActiveCatalogueEntries(category?: string): CatalogueEntry[] {
  const entries = ALL_BUNDLES.flatMap((b) => b.entries).filter((e) => e.status === "active");
  if (!category) return entries;
  return entries.filter((e) => e.category === category);
}

export function findCatalogueEntryByCode(code: string, category?: string): CatalogueEntry | undefined {
  return loadActiveCatalogueEntries(category).find((e) => e.code === code);
}

export function findFriendlyResolution(code: string): CatalogueEntry | undefined {
  return findCatalogueEntryByCode(code, "friendly_resolution_state");
}

export function findRoleTemplate(code: string): CatalogueEntry | undefined {
  return findCatalogueEntryByCode(code, "role_template");
}

export function findPermission(code: string): CatalogueEntry | undefined {
  return findCatalogueEntryByCode(code, "permission");
}

export function requiredCatalogueCodes(): Record<string, string[]> {
  return REQUIRED_CATALOGUE_CODES;
}

export function validateRequiredCatalogueCodes(): string[] {
  return validateRequiredCodes();
}

export { runAllCatalogueValidators, REQUIRED_CATALOGUE_CODES } from "./validators";
