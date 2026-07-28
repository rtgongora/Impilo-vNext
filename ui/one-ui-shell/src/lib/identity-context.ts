/**
 * Identity-context orchestration — Health OS single-login experience.
 *
 * Resolves who the user is, how they logged in, provider status, facility
 * assignments, and what shell tabs/actions are allowed. Used by post-login
 * routing, sidebar decluttering, route guards, Nompilo, and mobile parity.
 */

import type { AuthUser } from "@/hooks/useAuthStore";
import type { LinkedIdsAttributes } from "@/hooks/queries/useLinkedIds";
import type { WorkAssignment } from "@/lib/trust";
import {
  isActiveWorkAssignment,
  providerStatusAllowsProfessional,
  providerStatusBlocksWork,
} from "@/lib/trust";
import type { OperationalMode } from "@/lib/operational-context";
import { matchRouteDefinition } from "@/lib/routes";

export type LoginMethod = "health_id" | "provider_id" | "email" | "biometric" | "unknown";

export type ProviderIdStatus =
  | "NOT_STARTED"
  | "DRAFT"
  | "SUBMITTED"
  | "PENDING"
  | "PENDING_VERIFICATION"
  | "VERIFIED"
  | "ACTIVE"
  | "SUSPENDED"
  | "EXPIRED"
  | "REVOKED"
  | "REQUIRES_UPDATE";

export type ShellMode =
  | "citizen"
  | "provider"
  | "provider_restricted"
  | "facility_mode"
  | "programme_mode"
  | "above_site_mode";

export type NavZone = "life" | "work" | "professional";

export type ContextChooserOptionId =
  | "facility_work"
  | "telemedicine_pool"
  | "professional_profile"
  | "programme_workspace"
  | "above_site_dashboard"
  | "fundo_learning"
  | "personal_health";

export interface FacilityAffiliation {
  facilityId: string;
  facilityName?: string;
  role?: string;
  status?: string;
}

export interface ContextChooserOption {
  id: ContextChooserOptionId;
  label: string;
  description: string;
  href: string;
  operationalMode?: OperationalMode;
}

export interface IdentityContextInput {
  user: AuthUser | null;
  linkedIds?: LinkedIdsAttributes | null;
  affiliations?: FacilityAffiliation[];
  workAssignments?: WorkAssignment[];
  loginMethod?: LoginMethod;
  hasFacility?: boolean;
  operationalMode?: OperationalMode;
}

export interface IdentityContext {
  healthId: string | null;
  providerId: string | null;
  linkedProviderId: string | null;
  loginMethod: LoginMethod;
  providerStatus: ProviderIdStatus | null;
  licenceValid: boolean;
  isCitizenOnly: boolean;
  isProviderActivated: boolean;
  hasLinkedProviderId: boolean;
  hasProfessionalAccess: boolean;
  hasWorkAccess: boolean;
  shellMode: ShellMode;
  visibleNavZones: NavZone[];
  defaultLandingPath: string;
  defaultOperationalMode: OperationalMode;
  needsContextChooser: boolean;
  needsProviderStatusResolution: boolean;
  contextChooserOptions: ContextChooserOption[];
  isFacilityAdmin: boolean;
  activeAffiliationCount: number;
}

const WORK_BLOCKED_STATUSES = new Set<ProviderIdStatus>([
  "NOT_STARTED",
  "DRAFT",
  "SUBMITTED",
  "PENDING",
  "PENDING_VERIFICATION",
  "SUSPENDED",
  "EXPIRED",
  "REVOKED",
  "REQUIRES_UPDATE",
]);

const WORK_ALLOWED_STATUSES = new Set<ProviderIdStatus>(["VERIFIED", "ACTIVE"]);

const PROFESSIONAL_ROLES = new Set([
  "CLINICIAN",
  "NURSE",
  "PHARMACIST",
  "FACILITY_ADMIN",
  "SYSTEM_ADMIN",
  "DEVELOPER",
  "FINANCE",
  "SUPPORT_AGENT",
  "PUBLIC_HEALTH_OFFICER",
  "ENV_HEALTH",
  "CHW",
]);

const PROGRAMME_ROLES = new Set(["PROGRAMME_ADMIN", "PROGRAMME_OFFICER", "PROGRAMME_STAFF"]);

const ABOVE_SITE_ROLES = new Set([
  "SYSTEM_ADMIN",
  "HIE_ADMIN",
  "PUBLIC_HEALTH_OFFICER",
  "SUPER_ADMIN",
]);

/** Routes citizens must never reach even via deep link. */
const CITIZEN_BLOCKED_PREFIXES = [
  "/clinical",
  "/queue",
  "/work",
  "/provider-workspace",
  "/operations/facility-operations",
  "/organization-admin",
  "/registry-admin",
  "/admin",
  "/developer",
  "/operations",
  "/platform-journey",
  "/production-command-centre",
  "/health-os/command-centre",
  "/omnichannel",
  "/coverage/contracts",
];

/** Professional nav labels — prefer clearer worker terminology. */
export const WORKER_ACCESS_LABELS = {
  providerManagement: "Provider & Staff Management",
  facilityStaffManagement: "Facility Staff Management",
  workerAccess: "Worker Access Management",
  teamManagement: "Team Management",
} as const;

export function normalizeProviderStatus(raw: string | null | undefined): ProviderIdStatus | null {
  if (!raw) return null;
  const normalized = raw.trim().toUpperCase().replace(/[\s-]+/g, "_") as ProviderIdStatus;
  const known: ProviderIdStatus[] = [
    "NOT_STARTED",
    "DRAFT",
    "SUBMITTED",
    "PENDING",
    "PENDING_VERIFICATION",
    "VERIFIED",
    "ACTIVE",
    "SUSPENDED",
    "EXPIRED",
    "REVOKED",
    "REQUIRES_UPDATE",
  ];
  if (known.includes(normalized)) return normalized;
  if (normalized === "PENDING_VERIFICATION") return "PENDING_VERIFICATION";
  return null;
}

export function resolveLoginMethod(user: AuthUser | null, explicit?: LoginMethod): LoginMethod {
  if (explicit && explicit !== "unknown") return explicit;
  const stored = (user as AuthUser & { loginMethod?: LoginMethod })?.loginMethod;
  if (stored) return stored;
  return "unknown";
}

function hasRole(user: AuthUser | null, role: string): boolean {
  return user?.roles?.includes(role) ?? false;
}

function activeAffiliations(affiliations: FacilityAffiliation[]): FacilityAffiliation[] {
  return affiliations.filter((a) => {
    const status = a.status?.toUpperCase();
    return !status || status === "ACTIVE" || status === "VERIFIED";
  });
}

export function providerStatusAllowsWork(status: ProviderIdStatus | null): boolean {
  if (!status) return false;
  if (WORK_ALLOWED_STATUSES.has(status)) return true;
  return !WORK_BLOCKED_STATUSES.has(status);
}

export function resolveIdentityContext(input: IdentityContextInput): IdentityContext {
  const { user, linkedIds, affiliations = [], workAssignments = [], hasFacility = false } = input;
  const loginMethod = resolveLoginMethod(user, input.loginMethod);
  const linkedProviderId = linkedIds?.providerId ?? user?.linkedIds?.providerId ?? null;
  const providerId = user?.providerId ?? (user?.providerActivated ? linkedProviderId : null);
  const providerStatus = normalizeProviderStatus(linkedIds?.providerStatus);
  const licenceValid = linkedIds?.licenceValid !== false;
  const hasLinkedProviderId = !!linkedProviderId;
  const isProviderActivated = !!(user?.providerActivated && providerId);
  const isFacilityAdmin = hasRole(user, "FACILITY_ADMIN") || hasRole(user, "SYSTEM_ADMIN");

  const providerLogin = loginMethod === "provider_id";
  const statusNormalized = providerStatus?.toLowerCase().replace(/[\s-]+/g, "_") ?? null;

  const hasProfessionalAccess =
    !!(providerId || (providerLogin && hasLinkedProviderId)) &&
    licenceValid &&
    (statusNormalized
      ? providerStatusAllowsProfessional(statusNormalized)
      : isProviderActivated || providerLogin);

  const activeWorkAssignments = workAssignments.filter(isActiveWorkAssignment);
  const activeAffs = activeAffiliations(affiliations);
  const activeAffiliationCount = activeAffs.length;

  const hasWorkAccess =
    hasProfessionalAccess &&
    activeWorkAssignments.length > 0 &&
    !(statusNormalized && providerStatusBlocksWork(statusNormalized) && statusNormalized !== "active_restricted");

  // Citizen shell unless verified professional identity is established for session.
  const isCitizenOnly = !hasProfessionalAccess;

  const hasProgrammeRole = user?.roles?.some((r) => PROGRAMME_ROLES.has(r)) ?? false;
  const hasAboveSiteRole = user?.roles?.some((r) => ABOVE_SITE_ROLES.has(r)) ?? false;

  const needsProviderStatusResolution = Boolean(
    hasLinkedProviderId &&
      statusNormalized &&
      providerStatusBlocksWork(statusNormalized) &&
      statusNormalized !== "active_restricted" &&
      (providerLogin || isProviderActivated),
  );

  let shellMode: ShellMode = "citizen";
  if (needsProviderStatusResolution) {
    shellMode = "provider_restricted";
  } else if (hasWorkAccess) {
    if (hasFacility && activeAffiliationCount >= 1) shellMode = "facility_mode";
    else if (hasProgrammeRole) shellMode = "programme_mode";
    else if (hasAboveSiteRole) shellMode = "above_site_mode";
    else shellMode = "provider";
  } else if (hasProfessionalAccess) {
    shellMode = "provider";
  }

  const visibleNavZones: NavZone[] = isCitizenOnly
    ? ["life"]
    : hasWorkAccess
      ? ["work", "professional", "life"]
      : hasProfessionalAccess
        ? ["professional", "life"]
        : ["life"];

  const defaultOperationalMode: OperationalMode = isCitizenOnly
    ? "my_life"
    : hasWorkAccess
      ? "facility_work"
      : hasProfessionalAccess
        ? "my_professional"
        : "my_life";

  const needsContextChooser =
    hasWorkAccess &&
    !needsProviderStatusResolution &&
    (activeAffiliationCount > 1 || hasProgrammeRole || hasAboveSiteRole) &&
    !hasFacility;

  const contextChooserOptions = buildContextChooserOptions({
    user,
    activeAffs,
    hasWorkAccess,
    hasProfessionalAccess,
    hasProgrammeRole,
    hasAboveSiteRole,
    isFacilityAdmin,
  });

  let defaultLandingPath = "/home";
  if (needsProviderStatusResolution) {
    defaultLandingPath = "/provider/status";
  } else if (needsContextChooser) {
    defaultLandingPath = "/auth/context-chooser";
  } else if (hasWorkAccess) {
    if (!hasFacility && activeAffiliationCount === 1) {
      defaultLandingPath = "/facility";
    } else if (hasFacility) {
      // Phase F6: /provider-workspace is now an intent-resolution shim to /work — land there
      // directly. The Java-side twin (SessionExperienceService#resolveDefaultRoute) makes the
      // same change; landing-parity is asserted in resolve-post-login-destination.test.ts.
      defaultLandingPath = "/work";
    } else if (providerLogin) {
      defaultLandingPath = "/facility";
    } else {
      defaultLandingPath = "/home";
    }
  } else if (hasProfessionalAccess && !hasWorkAccess) {
    defaultLandingPath = "/professional";
  }

  return {
    healthId: user?.id ?? null,
    providerId,
    linkedProviderId,
    loginMethod,
    providerStatus,
    licenceValid,
    isCitizenOnly,
    isProviderActivated,
    hasLinkedProviderId,
    hasProfessionalAccess,
    hasWorkAccess,
    shellMode,
    visibleNavZones,
    defaultLandingPath,
    defaultOperationalMode,
    needsContextChooser,
    needsProviderStatusResolution,
    contextChooserOptions,
    isFacilityAdmin,
    activeAffiliationCount,
  };
}

function buildContextChooserOptions(args: {
  user: AuthUser | null;
  activeAffs: FacilityAffiliation[];
  hasWorkAccess: boolean;
  hasProfessionalAccess: boolean;
  hasProgrammeRole: boolean;
  hasAboveSiteRole: boolean;
  isFacilityAdmin: boolean;
}): ContextChooserOption[] {
  if (!args.user) return [];

  const options: ContextChooserOption[] = [];

  // Facility work and the telemedicine pool genuinely require an active work
  // assignment. Everything below does NOT: a national admin or a provider with
  // no assignment yet must still land somewhere — never a zero-option chooser.
  if (args.hasWorkAccess && args.activeAffs.length > 0) {
    options.push({
      id: "facility_work",
      label: args.activeAffs.length === 1 ? "Work at Facility" : "Choose Facility",
      description:
        args.activeAffs.length === 1
          ? `Continue at ${args.activeAffs[0].facilityName ?? "your facility"}`
          : "Select where you are working today",
      href: "/facility",
      operationalMode: "facility_work",
    });
  }

  if (args.hasWorkAccess && args.user.roles.some((r) => PROFESSIONAL_ROLES.has(r))) {
    options.push({
      id: "telemedicine_pool",
      label: "Join Telemedicine Pool",
      description: "Enter the virtual care queue when authorised",
      href: "/telemedicine",
      operationalMode: "facility_work",
    });
  }

  if (args.hasProfessionalAccess) {
    options.push({
      id: "professional_profile",
      label: "Open My Professional Profile",
      description: "Credentials, licence, CPD and professional details",
      href: "/professional",
      operationalMode: "my_professional",
    });
  }

  if (args.hasProgrammeRole) {
    options.push({
      id: "programme_workspace",
      label: "Open Programme Workspace",
      description: "Programme dashboards and coordinated care",
      href: "/public-health",
      operationalMode: "facility_work",
    });
  }

  if (args.hasAboveSiteRole) {
    options.push({
      id: "above_site_dashboard",
      label: "Open Above-Site Dashboard",
      description: "National oversight and cross-facility operations",
      href: "/public-health/oversight",
      operationalMode: "organization_admin",
    });
  }

  if (args.hasProfessionalAccess) {
    options.push({
      id: "fundo_learning",
      label: "Open Fundo Learning",
      description: "Professional learning and CPD courses",
      href: "/learning",
      operationalMode: "my_professional",
    });
  }

  options.push({
    id: "personal_health",
    label: "Open My Personal Health Account",
    description: "Switch to My Life / My Health",
    href: "/home",
    operationalMode: "my_life",
  });

  return options;
}

/** Whether a sidebar nav zone should render for the resolved context. */
export function canSeeNavZone(zone: NavZone, context: IdentityContext): boolean {
  return context.visibleNavZones.includes(zone);
}

/** Whether My Life should be secondary (profile/context menu) in provider mode. */
export function isLifeNavSecondary(context: IdentityContext): boolean {
  return context.hasWorkAccess && !context.isCitizenOnly;
}

/** Block citizen-only users from professional/work/admin routes. */
export function isRouteBlockedForCitizen(pathname: string, context: IdentityContext): boolean {
  if (!context.isCitizenOnly) return false;
  const path = pathname.split("?")[0];
  if (CITIZEN_BLOCKED_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))) {
    return true;
  }
  const route = matchRouteDefinition(path);
  if (!route) return false;
  if (route.navZone === "work" || route.navZone === "professional") return true;
  if (route.guard === "provider" || route.guard === "facility" || route.guard === "workspace" || route.guard === "shift") {
    return true;
  }
  if (route.requiredRole && route.requiredRole !== "CITIZEN") {
    const citizenSafeRoles = new Set(["CITIZEN"]);
    if (!citizenSafeRoles.has(route.requiredRole)) return true;
  }
  return false;
}

/** Nompilo identity packet — never surface provider/admin actions to citizens. */
export interface NompiloIdentityPacket {
  shellMode: ShellMode;
  isCitizenOnly: boolean;
  hasWorkAccess: boolean;
  providerStatus: ProviderIdStatus | null;
  facilityMode: boolean;
  suggestedPrompts: string[];
}

export function buildNompiloIdentityPacket(context: IdentityContext): NompiloIdentityPacket {
  if (context.isCitizenOnly) {
    return {
      shellMode: context.shellMode,
      isCitizenOnly: true,
      hasWorkAccess: false,
      providerStatus: context.providerStatus,
      facilityMode: false,
      suggestedPrompts: [
        "Book me an appointment",
        "Show my prescriptions",
        "How do I access my child's record?",
        "Register me for telemedicine",
        "Show my health card",
      ],
    };
  }

  if (context.isFacilityAdmin && context.shellMode === "facility_mode") {
    return {
      shellMode: context.shellMode,
      isCitizenOnly: false,
      hasWorkAccess: context.hasWorkAccess,
      providerStatus: context.providerStatus,
      facilityMode: true,
      suggestedPrompts: [
        "Add a provider to this facility",
        "Show staff with expired access",
        "Open provider management",
        "Show training compliance for this facility",
        "Show facility dashboard",
      ],
    };
  }

  return {
    shellMode: context.shellMode,
    isCitizenOnly: false,
    hasWorkAccess: context.hasWorkAccess,
    providerStatus: context.providerStatus,
    facilityMode: context.shellMode === "facility_mode",
    suggestedPrompts: [
      "Open today's queue",
      "Find client by Health ID",
      "Start consultation",
      "Show my professional licence status",
      "Join telemedicine pool",
    ],
  };
}

/** Provider status resolution copy for restricted providers. */
export function providerStatusResolutionMessage(status: ProviderIdStatus | null): {
  title: string;
  body: string;
  canUpdate: boolean;
  canContactSupport: boolean;
  canUsePersonalHealth: boolean;
} {
  switch (status) {
    case "SUSPENDED":
      return {
        title: "Provider ID suspended",
        body: "Your Provider ID is suspended. Work access is restricted until your status is restored.",
        canUpdate: false,
        canContactSupport: true,
        canUsePersonalHealth: true,
      };
    case "EXPIRED":
    case "REQUIRES_UPDATE":
      return {
        title: "Provider ID requires update",
        body: "Your professional registration needs renewal or updated information before work access is restored.",
        canUpdate: true,
        canContactSupport: true,
        canUsePersonalHealth: true,
      };
    case "REVOKED":
      return {
        title: "Provider ID revoked",
        body: "Your Provider ID has been revoked. Contact your council or facility administrator for guidance.",
        canUpdate: false,
        canContactSupport: true,
        canUsePersonalHealth: true,
      };
    case "PENDING":
    case "PENDING_VERIFICATION":
    case "SUBMITTED":
      return {
        title: "Provider ID pending verification",
        body: "Your Provider ID application is under review. You may continue in personal health mode while verification completes.",
        canUpdate: true,
        canContactSupport: true,
        canUsePersonalHealth: true,
      };
    default:
      return {
        title: "Provider access restricted",
        body: "Your Provider ID is not active for work. Review your status or continue in personal health mode.",
        canUpdate: true,
        canContactSupport: true,
        canUsePersonalHealth: true,
      };
  }
}
