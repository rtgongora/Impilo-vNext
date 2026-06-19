/**
 * Session contract helpers for Administration & Governance UI.
 */

import type { SessionExperienceContract } from "@/lib/trust";
import { findFriendlyResolution } from "@/lib/trust";
import { canAccessVashandiPath } from "@/lib/vashandi/access";

export function hasAdministrationGovernanceEntry(contract: SessionExperienceContract | undefined): boolean {
  if (!contract?.tabs.work.visible) return false;
  const visible = contract.visibleManagementWorkspaces ?? [];
  const workspaces = contract.visibleWorkspaces ?? [];
  return visible.length > 0 || workspaces.some((w) => w.includes("management") || w.includes("governance"));
}

/** BFF session contract may grant governance work paths to person-only logins (e.g. preview product owner). */
export function isGovernanceWorkPathGrantedBySession(
  contract: SessionExperienceContract | undefined,
  pathname: string,
): boolean {
  if (!contract?.authenticated || !contract.tabs.work.visible) return false;
  const path = pathname.split("?")[0];
  return path.startsWith("/work/administration-governance") && hasAdministrationGovernanceEntry(contract);
}

export function isWorkZoneGrantedBySession(contract: SessionExperienceContract | undefined): boolean {
  return isGovernanceWorkPathGrantedBySession(contract, "/work/administration-governance");
}

export function workspaceAllowed(
  contract: SessionExperienceContract | undefined,
  workspace: string,
): boolean {
  if (!contract) return false;
  if ((contract.visibleManagementWorkspaces ?? []).includes(workspace)) return true;
  return (contract.visibleWorkspaces ?? []).includes(workspace);
}

export function tileEnabled(
  contract: SessionExperienceContract | undefined,
  requiredWorkspaces: string[],
): boolean {
  if (!contract) return false;
  if (requiredWorkspaces.includes("*")) {
    return hasAdministrationGovernanceEntry(contract);
  }
  const visible = new Set([
    ...(contract.visibleManagementWorkspaces ?? []),
    ...(contract.visibleWorkspaces ?? []),
  ]);
  return requiredWorkspaces.some((w) => visible.has(w));
}

export function tileBlockedReason(
  contract: SessionExperienceContract | undefined,
  blockedWorkspaceHint?: string,
): string | undefined {
  if (!contract || !blockedWorkspaceHint) return undefined;
  if ((contract.blockedManagementWorkspaces ?? []).includes(blockedWorkspaceHint)) {
    return contract.friendlyResolutionState ?? "policy_denied";
  }
  return undefined;
}

export function resolveFriendlyStateMessage(state?: string): {
  title: string;
  description: string;
  actions: string[];
} {
  if (!state) {
    return {
      title: "Access not available",
      description: "You do not have authority for this management action in your current scope.",
      actions: ["View My Scope", "Contact Support"],
    };
  }
  const entry = findFriendlyResolution(state);
  if (entry) {
    const meta = entry.metadata as { allowedActions?: string[] } | undefined;
    return {
      title: entry.displayName ?? state,
      description: entry.description ?? "This action is blocked by policy.",
      actions: meta?.allowedActions ?? ["Contact Support"],
    };
  }
  return {
    title: state.replace(/_/g, " "),
    description: "This action is blocked by trust policy.",
    actions: ["Contact Support"],
  };
}

export function canAccessAdministrationPath(
  contract: SessionExperienceContract | undefined,
  pathname: string,
): boolean {
  if (!contract?.authenticated) return false;
  if (!contract.tabs.work.visible && pathname.startsWith("/work/")) {
    if (!hasAdministrationGovernanceEntry(contract)) return false;
  }

  const prefixRules: Array<{ prefix: string; workspaces: string[] }> = [
    { prefix: "/work/hsc", workspaces: ["hsc_user_management", "hsc_establishment_control", "hsc_workspace"] },
    { prefix: "/work/regulators", workspaces: ["council_user_management", "regulator_organisation_management"] },
    { prefix: "/work/madi", workspaces: ["blood_service_user_management", "blood_service_organisation_management"] },
    { prefix: "/work/marketplace/organisations", workspaces: ["marketplace_user_management", "marketplace_organisation_verification"] },
    { prefix: "/work/payers", workspaces: ["payer_user_management", "payer_organisation_management"] },
    { prefix: "/work/partners", workspaces: ["external_partner_user_management", "research_partner_organisation_management"] },
    { prefix: "/work/system-admin", workspaces: ["national_platform_user_administration", "national_system_operator_management"] },
    { prefix: "/work/facility", workspaces: ["public_facility_staff_management", "facility_work_assignment", "private_facility_user_management"] },
    { prefix: "/work/administration-governance/municipal", workspaces: ["municipal_user_management", "municipal_organisation_management"] },
    { prefix: "/work/administration-governance/private-facility", workspaces: ["private_facility_user_management", "mission_facility_user_management"] },
  ];

  for (const rule of prefixRules) {
    if (pathname === rule.prefix || pathname.startsWith(`${rule.prefix}/`)) {
      return tileEnabled(contract, rule.workspaces);
    }
  }

  if (pathname.startsWith("/work/vashandi")) {
    return canAccessVashandiPath(contract, pathname);
  }

  if (pathname.startsWith("/work/administration-governance")) {
    return hasAdministrationGovernanceEntry(contract);
  }

  return true;
}
