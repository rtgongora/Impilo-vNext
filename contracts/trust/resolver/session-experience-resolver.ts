/**
 * Session Experience Contract resolver — standards-grounded three-tab model.
 *
 * Doctrine:
 * - Health ID → Personal
 * - Verified Provider/Worker ID → Professional (NOT automatic Work)
 * - Active WorkAssignment → Work
 * - Keycloak roles alone never unlock Work
 */

import { findFriendlyResolution } from "../loader";
import {
  providerStatusAllowsProfessional,
  providerStatusBlocksWork,
  type ProviderProfessionalTruth,
} from "../types/provider-professional-truth";
import {
  SESSION_EXPERIENCE_CONTRACT_VERSION,
  type SessionExperienceContract,
  type SessionIdentity,
  type SessionIdentityType,
  type SessionLoginMethod,
  type SessionTabDefinition,
} from "../types/session-experience-contract";
import {
  isActiveWorkAssignment,
  type WorkAssignment,
} from "../types/work-assignment";

export interface SessionExperienceInput {
  authenticated: boolean;
  loginMethod?: SessionLoginMethod;
  healthId?: string | null;
  providerWorkerId?: string | null;
  linkedProviderWorkerId?: string | null;
  organisationId?: string | null;
  identityType?: SessionIdentityType;
  citizenAccountState?: string;
  identityAssuranceLevel?: string;
  professionalTruth?: Partial<ProviderProfessionalTruth> | null;
  workAssignments?: WorkAssignment[];
  marketplacePipelineState?: string | null;
  selectedContext?: string | null;
  facilityModeActive?: boolean;
  hasSelectedFacility?: boolean;
}

const OPA_PACKAGES = [
  "impilo.tabs",
  "impilo.personal",
  "impilo.professional",
  "impilo.work",
  "impilo.work_assignment",
  "impilo.registry",
  "impilo.marketplace",
  "impilo.nompilo",
];

function tab(
  visible: boolean,
  label: string,
  route: string,
  opts?: { default?: boolean; reason?: string; requiredSource?: string },
): SessionTabDefinition {
  return {
    visible,
    label,
    route,
    default: opts?.default,
    reason: opts?.reason,
    requiredSource: opts?.requiredSource,
  };
}

function resolveIdentityType(input: SessionExperienceInput): SessionIdentityType {
  if (input.identityType) return input.identityType;
  if (input.marketplacePipelineState && !input.healthId) return "marketplace_actor";
  if (input.providerWorkerId || input.linkedProviderWorkerId) {
    return input.healthId ? "dual_citizen_provider" : "provider_worker";
  }
  return "citizen";
}

function activeAssignments(assignments: WorkAssignment[] = []): WorkAssignment[] {
  return assignments.filter(isActiveWorkAssignment);
}

function professionalEligible(input: SessionExperienceInput): boolean {
  const status = input.professionalTruth?.providerWorkerStatus;
  const providerId = input.providerWorkerId ?? input.linkedProviderWorkerId;
  if (!providerId) return false;
  if (status && !providerStatusAllowsProfessional(status)) return false;
  if (status && providerStatusBlocksWork(status) && status !== "active_restricted") {
    // pending/suspended etc. may still show professional status resolution, not full professional tools
    return status === "verified" || status === "active" || status === "active_restricted";
  }
  return providerStatusAllowsProfessional(status ?? "active") || !!providerId;
}

function workEligible(input: SessionExperienceInput, assignments: WorkAssignment[]): boolean {
  const status = input.professionalTruth?.providerWorkerStatus;
  if (status && providerStatusBlocksWork(status) && status !== "active_restricted") return false;
  const cpd = input.professionalTruth?.cpdComplianceSummary?.status;
  if (cpd === "overdue" || cpd === "restricted_until_complete") return false;
  if (assignments.length === 0) return false;

  const identityType = resolveIdentityType(input);
  if (identityType === "citizen") return false;
  if (identityType === "marketplace_actor") {
    return assignments.some((a) => a.contextType === "marketplace_operations");
  }
  return true;
}

function buildResolutionActions(input: SessionExperienceInput, hasWork: boolean, hasProfessional: boolean): string[] {
  if (hasWork) return [];
  if (hasProfessional) {
    return [
      "View Professional Profile",
      "Complete Required CPD",
      "Request Work Assignment",
      "Switch to Personal",
    ];
  }
  return ["View My Health", "Create My Health Account"];
}

function buildNompilo(
  activeTab: "personal" | "professional" | "work",
  visibleActions: string[],
): SessionExperienceContract["nompiloContext"] {
  const domains =
    activeTab === "personal"
      ? ["nompilo.personal"]
      : activeTab === "professional"
        ? ["nompilo.professional"]
        : ["nompilo.work"];

  const suggestedPrompts =
    activeTab === "personal"
      ? ["Book me an appointment", "Show my prescriptions", "Show my health card"]
      : activeTab === "professional"
        ? ["Show my professional licence status", "Open Fundo courses", "Request work assignment"]
        : ["Open today's queue", "Find client by Health ID", "Start consultation"];

  return { activeTab, suggestedPrompts, allowedActionDomains: domains };
}

export function resolveSessionExperienceContract(input: SessionExperienceInput): SessionExperienceContract {
  const loginMethod: SessionLoginMethod = input.loginMethod ?? "unknown";
  const identityType = resolveIdentityType(input);
  const assignments = activeAssignments(input.workAssignments);
  const hasProfessional = professionalEligible(input);
  const hasWork = workEligible(input, assignments);
  const isMarketplace = identityType === "marketplace_actor";

  const personalVisible = !!input.healthId || identityType === "citizen" || identityType === "dual_citizen_provider";
  const professionalVisible = hasProfessional && !isMarketplace;
  const workVisible = hasWork || (isMarketplace && assignments.length > 0);

  let friendlyResolutionState: string | undefined;
  if (!hasWork && hasProfessional) friendlyResolutionState = "no_active_work_assignment";
  else if (input.professionalTruth?.providerWorkerStatus === "suspended") friendlyResolutionState = "provider_suspended";
  else if (input.professionalTruth?.cpdComplianceSummary?.status === "overdue") friendlyResolutionState = "missing_required_training";
  else if (input.marketplacePipelineState === "sandbox_access_granted" && !assignments.some((a) => a.contextType === "marketplace_operations")) {
    friendlyResolutionState = "marketplace_sandbox_only";
  } else if (identityType === "citizen" && !hasProfessional) friendlyResolutionState = "citizen_only_access";

  const defaultTab: "personal" | "professional" | "work" = workVisible
    ? loginMethod === "health_id"
      ? assignments.length === 1
        ? "work"
        : "work"
      : "work"
    : professionalVisible
      ? loginMethod === "provider_id"
        ? "professional"
        : "personal"
      : "personal";

  const tabs = {
    personal: tab(personalVisible, "My Life / My Health", "/home", {
      default: defaultTab === "personal",
      requiredSource: "health_id",
      reason: personalVisible ? undefined : "No Health ID context for this login",
    }),
    professional: tab(professionalVisible, "My Professional", "/professional", {
      default: defaultTab === "professional",
      requiredSource: "verified_provider_worker_id",
      reason: professionalVisible ? undefined : hasProfessional ? undefined : "No verified Provider/Worker ID",
    }),
    work: tab(workVisible, "Work", "/provider-workspace", {
      default: defaultTab === "work",
      requiredSource: "active_work_assignment",
      reason: workVisible ? undefined : "No active work assignment",
    }),
  };

  const facilityAssignments = assignments.filter((a) => !!a.facilityId);
  const facilityModeAvailable =
    workVisible && facilityAssignments.length > 0 && professionalVisible;
  const requiresContextChooser =
    workVisible && (assignments.length > 1 || (facilityAssignments.length > 1 && !input.hasSelectedFacility));

  const visibleWorkspaces = assignments.map((a) => a.workspaceId ?? a.contextType).filter(Boolean) as string[];
  const roleTemplates = assignments.map((a) => a.roleTemplateId ?? a.roleName ?? "").filter(Boolean);
  const visibleActions: string[] = [];
  if (tabs.personal.visible) visibleActions.push("personal.profile.view");
  if (tabs.professional.visible) visibleActions.push("professional.profile.view");
  if (tabs.work.visible) visibleActions.push("work.context.enter");

  const blockedActions: string[] = [];
  if (!tabs.work.visible) {
    blockedActions.push("work.context.enter", "facility.staff.assign", "clinical.encounter.create");
  }
  if (!tabs.professional.visible) {
    blockedActions.push("professional.profile.view", "registry.provider.verify");
  }

  let defaultRoute = tabs.personal.route;
  if (defaultTab === "work") {
    if (requiresContextChooser) defaultRoute = "/auth/context-chooser";
    else if (assignments.length === 1 && assignments[0].startupRoute) defaultRoute = assignments[0].startupRoute;
    else if (facilityAssignments.length === 1 && !input.hasSelectedFacility) defaultRoute = "/facility";
    else defaultRoute = tabs.work.route;
  } else if (defaultTab === "professional") {
    if (input.professionalTruth?.providerWorkerStatus === "suspended") defaultRoute = "/provider/status";
    else defaultRoute = tabs.professional.route;
  }

  const identity: SessionIdentity = {
    healthId: input.healthId ?? undefined,
    providerWorkerId: input.providerWorkerId ?? input.linkedProviderWorkerId ?? undefined,
    organisationId: input.organisationId ?? undefined,
    identityType,
    citizenAccountState: input.citizenAccountState,
    identityAssuranceLevel: input.identityAssuranceLevel,
  };

  const resolutionActions = buildResolutionActions(input, hasWork, hasProfessional);
  if (friendlyResolutionState) {
    const fr = findFriendlyResolution(friendlyResolutionState);
    const meta = fr?.metadata as { allowedActions?: string[] } | undefined;
    if (meta?.allowedActions?.length) {
      resolutionActions.splice(0, resolutionActions.length, ...meta.allowedActions);
    }
  }

  return {
    authenticated: input.authenticated,
    loginMethod,
    identity,
    tabs,
    providerWorkerStatus: input.professionalTruth?.providerWorkerStatus,
    marketplacePipelineState: input.marketplacePipelineState ?? undefined,
    activeWorkAssignments: assignments,
    availableContexts: [...new Set(assignments.map((a) => a.contextType))],
    selectedContext: input.selectedContext ?? undefined,
    visibleWorkspaces,
    visibleActions,
    blockedActions,
    roleTemplates,
    policyMetadata: {
      contractVersion: SESSION_EXPERIENCE_CONTRACT_VERSION,
      opaPackages: OPA_PACKAGES,
      enforcement: "bff_and_opa",
    },
    nompiloContext: buildNompilo(defaultTab, visibleActions),
    friendlyResolutionState,
    resolutionActions,
    requiresContextChooser,
    facilityModeAvailable,
    facilityModeActive: !!input.facilityModeActive,
    defaultRoute,
  };
}

/** Route/API guard helper — citizens and unassigned providers cannot access work routes. */
export function sessionContractAllowsRoute(
  contract: SessionExperienceContract,
  pathname: string,
): boolean {
  if (!contract.authenticated) return false;
  const path = pathname.split("?")[0];
  const workPrefixes = ["/clinical", "/queue", "/provider-workspace", "/operations/facility-operations", "/organization-admin"];
  const professionalPrefixes = ["/registry-admin", "/admin", "/registry/providers"];
  if (!contract.tabs.work.visible && workPrefixes.some((p) => path === p || path.startsWith(`${p}/`))) return false;
  if (!contract.tabs.professional.visible && professionalPrefixes.some((p) => path === p || path.startsWith(`${p}/`))) return false;
  return true;
}
