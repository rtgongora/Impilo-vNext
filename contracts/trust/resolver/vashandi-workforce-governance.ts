/**
 * Vashandi workforce workspace resolution for Session Experience Contract.
 */

import type { SessionOrganisationContext } from "../types/session-experience-contract";
import type { WorkAssignment } from "../types/work-assignment";

export const VASHANDI_WORKSPACES = [
  "vashandi.dashboard",
  "vashandi.workforce_registry",
  "vashandi.my_roster",
  "vashandi.my_attendance",
  "vashandi.facility_staff",
  "vashandi.rosters",
  "vashandi.leave_availability",
  "vashandi.assignments",
  "vashandi.access_review",
  "vashandi.analytics",
  "vashandi.imports",
  "vashandi.hsc_postings",
  "vashandi.organisation_workforce",
  "vashandi.emergency_surge",
] as const;

export type VashandiWorkspace = (typeof VASHANDI_WORKSPACES)[number];

export interface VashandiWorkspaceInput {
  workVisible?: boolean;
  roleTemplates?: string[];
  organisation?: SessionOrganisationContext;
  activeAssignments?: WorkAssignment[];
  currentWorkforceStatus?: string | null;
  providerWorkerStatus?: string | null;
}

const ROLE_WORKSPACE_MAP: Record<string, VashandiWorkspace[]> = {
  vashandi_self_service_worker: [
    "vashandi.dashboard",
    "vashandi.my_roster",
    "vashandi.my_attendance",
    "vashandi.leave_availability",
  ],
  vashandi_facility_workforce_manager: [
    "vashandi.dashboard",
    "vashandi.facility_staff",
    "vashandi.rosters",
    "vashandi.assignments",
    "vashandi.analytics",
  ],
  vashandi_roster_manager: ["vashandi.rosters", "vashandi.facility_staff", "vashandi.analytics"],
  vashandi_attendance_supervisor: ["vashandi.facility_staff", "vashandi.my_attendance", "vashandi.analytics"],
  vashandi_leave_approver: ["vashandi.leave_availability", "vashandi.facility_staff"],
  vashandi_access_reviewer: ["vashandi.access_review", "vashandi.analytics"],
  vashandi_analytics_viewer: ["vashandi.analytics", "vashandi.dashboard"],
  vashandi_national_workforce_admin: ["vashandi.workforce_registry", "vashandi.dashboard", "vashandi.analytics"],
  vashandi_hsc_workforce_admin: ["vashandi.hsc_postings", "vashandi.workforce_registry", "vashandi.dashboard"],
  vashandi_organisation_workforce_admin: ["vashandi.organisation_workforce", "vashandi.assignments", "vashandi.dashboard"],
  vashandi_emergency_surge_coordinator: ["vashandi.emergency_surge", "vashandi.rosters", "vashandi.assignments"],
};

function normalizeStatus(status?: string | null): string | undefined {
  if (!status) return undefined;
  return status.trim().toLowerCase().replace(/[\s-]+/g, "_");
}

export function resolveVashandiWorkspaces(input: VashandiWorkspaceInput): {
  visible: VashandiWorkspace[];
  blocked: VashandiWorkspace[];
  friendlyResolutionState?: string;
} {
  const visible = new Set<VashandiWorkspace>();
  const blocked = new Set<VashandiWorkspace>(VASHANDI_WORKSPACES);

  const workforceStatus = normalizeStatus(input.currentWorkforceStatus);
  const providerStatus = normalizeStatus(input.providerWorkerStatus);
  if (workforceStatus === "suspended" || providerStatus === "suspended") {
    return {
      visible: [],
      blocked: [...VASHANDI_WORKSPACES],
      friendlyResolutionState: "vashandi_workforce_suspended",
    };
  }

  if (input.workVisible) {
    visible.add("vashandi.dashboard");
    visible.add("vashandi.my_roster");
    visible.add("vashandi.my_attendance");
    visible.add("vashandi.leave_availability");
  }

  for (const role of input.roleTemplates ?? []) {
    const key = role.trim().toLowerCase();
    for (const [template, workspaces] of Object.entries(ROLE_WORKSPACE_MAP)) {
      if (key.includes(template) || key === template) {
        workspaces.forEach((w) => visible.add(w));
      }
    }
  }

  if (input.organisation?.organisationType === "health_service_commission") {
    visible.add("vashandi.hsc_postings");
  }
  if (input.organisation?.organisationType === "public_facility") {
    visible.add("vashandi.facility_staff");
    visible.add("vashandi.rosters");
  }
  if (input.organisation?.organisationType === "health_professions_authority") {
    visible.add("vashandi.workforce_registry");
    blocked.add("vashandi.assignments");
    blocked.add("vashandi.facility_staff");
  }

  for (const workspace of visible) {
    blocked.delete(workspace);
  }

  return {
    visible: [...visible],
    blocked: [...blocked],
    friendlyResolutionState: visible.size === 0 && input.workVisible ? "vashandi_no_workspace_authority" : undefined,
  };
}
