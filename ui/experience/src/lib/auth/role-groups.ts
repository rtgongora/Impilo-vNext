/**
 * Role group → concrete Keycloak role expansion.
 *
 * Must stay aligned with `AuthGuardProvider` / backend SecurityConfig.java role-group arrays.
 */

export const ROLE_GROUPS: Record<string, string[]> = {
  REGISTRY_ADMIN: ["SYSTEM_ADMIN", "HIE_ADMIN"],
  ORGANIZATION_ADMIN: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER", "FINANCE"],
  ADMIN_OR_HIE: ["SYSTEM_ADMIN", "HIE_ADMIN", "FACILITY_ADMIN", "DEVELOPER"],
  ADMIN: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"],
  FINANCE: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"],
  PAYER_OPS: ["SYSTEM_ADMIN", "FINANCE", "DEVELOPER"],
  MSIKA_GOVERNANCE: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE", "DEVELOPER"],
  CLINICAL: ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  PRESCRIBER: ["CLINICIAN", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  DISPENSER: ["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  QUEUE: ["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  CITIZEN: ["CITIZEN", "SYSTEM_ADMIN", "DEVELOPER"],
  CAREGIVER: ["CAREGIVER", "CARE_PARTNER", "CITIZEN", "SYSTEM_ADMIN"],
  PUBLIC_HEALTH: ["PUBLIC_HEALTH_OFFICER", "ENV_HEALTH", "CHW", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  /** District / provincial / national queue aggregates (aligned with BFF OPERATIONS_AGGREGATE_ROLES). */
  OPERATIONS_AGGREGATE: [
    "PUBLIC_HEALTH_OFFICER",
    "ENV_HEALTH",
    "CHW",
    "CLINICIAN",
    "NURSE",
    "SUPPORT_AGENT",
    "FACILITY_ADMIN",
    "SYSTEM_ADMIN",
    "DEVELOPER",
    "HIE_ADMIN",
  ],
  COMMERCE: ["FINANCE", "CLINICIAN", "NURSE", "PHARMACIST", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
};

export function matchesRequiredRole(hasRole: (r: string) => boolean, requiredRole: string): boolean {
  const group = ROLE_GROUPS[requiredRole];
  if (group) return group.some((r) => hasRole(r));
  return hasRole(requiredRole);
}
