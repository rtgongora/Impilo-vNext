/**
 * Hook for checking role-group membership in UI components.
 *
 * Uses the same ROLE_GROUPS mapping as AuthGuardProvider and the
 * backend SecurityConfig to ensure frontend/backend consistency.
 *
 * Usage:
 *   const { isClinical, isPrescriber, isDispenser } = useRoleGroup();
 *   if (isPrescriber) { // show prescription button }
 */

import { useAuthStore } from "@/hooks/useAuthStore";
import { ROLE_GROUPS } from "@/providers/AuthGuardProvider";

function hasGroupRole(hasRole: (r: string) => boolean, group: string): boolean {
  const roles = ROLE_GROUPS[group];
  if (!roles) return false;
  return roles.some((r) => hasRole(r));
}

export function useRoleGroup() {
  const { hasRole } = useAuthStore();

  return {
    isAdmin: hasGroupRole(hasRole, "ADMIN"),
    isFinance: hasGroupRole(hasRole, "FINANCE"),
    isClinical: hasGroupRole(hasRole, "CLINICAL"),
    isPrescriber: hasGroupRole(hasRole, "PRESCRIBER"),
    isDispenser: hasGroupRole(hasRole, "DISPENSER"),
    isQueueManager: hasGroupRole(hasRole, "QUEUE"),
    isCitizen: hasGroupRole(hasRole, "CITIZEN"),
    hasGroup: (group: string) => hasGroupRole(hasRole, group),
  };
}
