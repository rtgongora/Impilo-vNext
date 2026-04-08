/**
 * Top-level operational context model for single-login experience entry.
 *
 * Facility Work is a mode (not a separate account). Registry Admin is a
 * high-trust operational mode distinct from generic organization admin.
 */

/** Subset of auth profile needed for mode resolution (avoids circular imports). */
export interface OperationalAuthPrincipal {
  roles: string[];
  actorType: "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM";
}

/** Primary post-login operational modes shown in the experience shell. */
export type OperationalMode =
  | "my_life"
  | "my_professional"
  | "facility_work"
  | "registry_admin"
  | "organization_admin";

/** Future: front-line subcontext within Facility Work (workspace/role hints). */
export type FacilityWorkSubcontext =
  | "front_desk"
  | "triage"
  | "consultation"
  | "nursing_station"
  | "pharmacy"
  | "laboratory"
  | "imaging"
  | "billing"
  | "ward"
  | "supervisor";

/** Future: sovereign registry plane (high-trust). */
export type RegistryAdminSubtype =
  | "client_registry"
  | "provider_registry"
  | "facility_registry"
  | "terminology_admin"
  | "trust_admin";

/** Organization administration plane — distinct from Facility Work execution and registry governance. */
export type OrganizationAdminSurface =
  | "finance_revenue"
  | "facility_admin"
  | "service_point_admin"
  | "staffing_roster"
  | "approvals"
  | "reports"
  | "settings";

export type OperationalTrustTier = "standard" | "elevated" | "registry";

export interface OperationalModeDefinition {
  id: OperationalMode;
  label: string;
  shortLabel: string;
  description: string;
  trustTier: OperationalTrustTier;
  /** When true, the canonical sequencing is facility → workspace → shift for guarded clinical routes. */
  expectsFacilityWorkSequence: boolean;
}

export const OPERATIONAL_MODE_ORDER: OperationalMode[] = [
  "my_life",
  "my_professional",
  "facility_work",
  "registry_admin",
  "organization_admin",
];

export const OPERATIONAL_MODE_DEF: Record<OperationalMode, OperationalModeDefinition> = {
  my_life: {
    id: "my_life",
    label: "My Life",
    shortLabel: "Life",
    description: "Personal health, preferences, and citizen-facing tools.",
    trustTier: "standard",
    expectsFacilityWorkSequence: false,
  },
  my_professional: {
    id: "my_professional",
    label: "My Professional",
    shortLabel: "Professional",
    description: "Credentials, schedule, learning, and professional profile.",
    trustTier: "standard",
    expectsFacilityWorkSequence: false,
  },
  facility_work: {
    id: "facility_work",
    label: "Facility Work",
    shortLabel: "Facility",
    description: "Operational patient care and facility workflows (queue, chart, wards).",
    trustTier: "standard",
    expectsFacilityWorkSequence: true,
  },
  registry_admin: {
    id: "registry_admin",
    label: "Registry Admin",
    shortLabel: "Registry",
    description:
      "High-trust sovereign registry operations (MPI, providers, facilities, terminology, trust).",
    trustTier: "registry",
    expectsFacilityWorkSequence: false,
  },
  organization_admin: {
    id: "organization_admin",
    label: "Organization Admin",
    shortLabel: "Org Admin",
    description: "Facility or enterprise configuration, users, billing setup, and local policy.",
    trustTier: "elevated",
    expectsFacilityWorkSequence: false,
  },
};

const CLINICAL_FACILITY_ROLES = new Set([
  "CLINICIAN",
  "NURSE",
  "PHARMACIST",
  "SUPPORT_AGENT",
  "FACILITY_ADMIN",
]);

const REGISTRY_PLANE_ROLES = new Set(["SYSTEM_ADMIN", "HIE_ADMIN"]);

const ORG_ADMIN_ROLES = new Set(["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"]);

const REGISTRY_ADMIN_SUBTYPE_VALUES: RegistryAdminSubtype[] = [
  "client_registry",
  "provider_registry",
  "facility_registry",
  "terminology_admin",
  "trust_admin",
];

const ORGANIZATION_ADMIN_SURFACE_VALUES: OrganizationAdminSurface[] = [
  "finance_revenue",
  "facility_admin",
  "service_point_admin",
  "staffing_roster",
  "approvals",
  "reports",
  "settings",
];

export function isRegistryAdminSubtype(value: string): value is RegistryAdminSubtype {
  return (REGISTRY_ADMIN_SUBTYPE_VALUES as string[]).includes(value);
}

export function isOrganizationAdminSurface(value: string): value is OrganizationAdminSurface {
  return (ORGANIZATION_ADMIN_SURFACE_VALUES as string[]).includes(value);
}

/** True when the principal may open the registry governance plane (SYSTEM_ADMIN / HIE_ADMIN). */
export function principalHasRegistryAdminPlane(user: OperationalAuthPrincipal | null): boolean {
  if (!user) return false;
  return user.roles.some((r) => REGISTRY_PLANE_ROLES.has(r));
}

/** True when the principal may open the organization administration plane. */
export function principalHasOrganizationAdminPlane(user: OperationalAuthPrincipal | null): boolean {
  if (!user) return false;
  if (user.roles.some((r) => ORG_ADMIN_ROLES.has(r))) return true;
  return user.actorType === "OPERATOR" && user.roles.includes("FINANCE");
}

export function isOperationalMode(value: string): value is OperationalMode {
  return (OPERATIONAL_MODE_ORDER as string[]).includes(value);
}

export function operationalModeExpectsFacilityWorkSequence(mode: OperationalMode): boolean {
  return OPERATIONAL_MODE_DEF[mode].expectsFacilityWorkSequence;
}

/**
 * Computes which operational modes the signed-in principal may use.
 * Same user account; different operational hats.
 */
export function buildAvailableOperationalModes(user: OperationalAuthPrincipal | null): OperationalMode[] {
  if (!user) return [];

  const roles = user.roles ?? [];
  const has = (r: string) => roles.includes(r);
  const allowed = new Set<OperationalMode>();

  allowed.add("my_life");

  const hasClinicalFacilityRole = roles.some((r) => CLINICAL_FACILITY_ROLES.has(r));

  if (user.actorType === "PROVIDER" || hasClinicalFacilityRole) {
    allowed.add("my_professional");
  }

  if (hasClinicalFacilityRole) {
    allowed.add("facility_work");
  }

  if (principalHasRegistryAdminPlane(user)) {
    allowed.add("registry_admin");
  }

  if (principalHasOrganizationAdminPlane(user)) {
    allowed.add("organization_admin");
  }

  return OPERATIONAL_MODE_ORDER.filter((m) => allowed.has(m));
}

/**
 * Default mode after login when nothing is remembered.
 */
export function suggestDefaultOperationalMode(user: OperationalAuthPrincipal | null): OperationalMode {
  const available = buildAvailableOperationalModes(user);
  if (available.length === 0) return "my_life";
  if (user?.actorType === "CITIZEN") {
    if (available.includes("my_life")) return "my_life";
  }
  if (available.includes("facility_work")) return "facility_work";
  if (available.includes("organization_admin")) return "organization_admin";
  if (available.includes("registry_admin")) return "registry_admin";
  if (available.includes("my_professional")) return "my_professional";
  return available[0];
}

export interface PostLoginContextResolution {
  available: OperationalMode[];
  suggested: OperationalMode;
}

export function resolvePostLoginOperationalContext(user: OperationalAuthPrincipal | null): PostLoginContextResolution {
  const available = buildAvailableOperationalModes(user);
  return {
    available,
    suggested: suggestDefaultOperationalMode(user),
  };
}

/** Cleared on sign-out alongside auth session keys. */
export const OPERATIONAL_CONTEXT_SESSION_KEYS = [
  "exp:operational_mode",
  "exp:facility_work_subcontext",
  "exp:registry_admin_subtype",
  "exp:organization_admin_surface",
] as const;
