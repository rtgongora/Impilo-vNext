/**
 * Vashandi workspace access — driven by SessionExperienceContract.visibleVashandiWorkspaces.
 */

import type { SessionExperienceContract } from "@/lib/trust";

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

const PATH_WORKSPACE_RULES: Array<{ prefix: string; workspaces: VashandiWorkspace[] }> = [
  { prefix: "/work/vashandi/workforce", workspaces: ["vashandi.workforce_registry", "vashandi.organisation_workforce", "vashandi.facility_staff"] },
  { prefix: "/work/vashandi/assignments", workspaces: ["vashandi.assignments", "vashandi.emergency_surge"] },
  { prefix: "/work/vashandi/rosters", workspaces: ["vashandi.rosters", "vashandi.my_roster"] },
  { prefix: "/work/vashandi/attendance", workspaces: ["vashandi.my_attendance", "vashandi.facility_staff"] },
  { prefix: "/work/vashandi/leave-availability", workspaces: ["vashandi.leave_availability"] },
  { prefix: "/work/vashandi/imports", workspaces: ["vashandi.imports", "vashandi.workforce_registry"] },
  { prefix: "/work/vashandi/access-review", workspaces: ["vashandi.access_review"] },
  { prefix: "/work/vashandi/analytics", workspaces: ["vashandi.analytics"] },
  { prefix: "/work/vashandi", workspaces: ["vashandi.dashboard"] },
];

export function visibleVashandiWorkspaces(contract: SessionExperienceContract | undefined): string[] {
  return contract?.visibleVashandiWorkspaces ?? [];
}

export function hasVashandiEntry(contract: SessionExperienceContract | undefined): boolean {
  if (!contract?.authenticated || !contract.tabs.work.visible) return false;
  return visibleVashandiWorkspaces(contract).length > 0;
}

export function vashandiWorkspaceAllowed(
  contract: SessionExperienceContract | undefined,
  workspace: string,
): boolean {
  return visibleVashandiWorkspaces(contract).includes(workspace);
}

export function canAccessVashandiPath(
  contract: SessionExperienceContract | undefined,
  pathname: string,
): boolean {
  if (!contract?.authenticated) return false;
  if (!contract.tabs.work.visible) return false;

  const path = pathname.split("?")[0];
  if (!path.startsWith("/work/vashandi")) return true;

  const visible = new Set(visibleVashandiWorkspaces(contract));
  if (visible.size === 0) return false;

  const sortedRules = [...PATH_WORKSPACE_RULES].sort((a, b) => b.prefix.length - a.prefix.length);
  for (const rule of sortedRules) {
    if (path === rule.prefix || path.startsWith(`${rule.prefix}/`)) {
      return rule.workspaces.some((w) => visible.has(w));
    }
  }

  return visible.has("vashandi.dashboard");
}

export function requiredWorkspacesForPath(pathname: string): VashandiWorkspace[] {
  const path = pathname.split("?")[0];
  const sortedRules = [...PATH_WORKSPACE_RULES].sort((a, b) => b.prefix.length - a.prefix.length);
  for (const rule of sortedRules) {
    if (path === rule.prefix || path.startsWith(`${rule.prefix}/`)) {
      return rule.workspaces;
    }
  }
  return ["vashandi.dashboard"];
}
