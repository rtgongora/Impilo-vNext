"use client";

/**
 * Auth Guard Provider — wraps the router and enforces auth state hierarchy.
 *
 * Guard chain: auth → facility → workspace → shift → role
 * Redirects to the appropriate selection page if context is missing.
 */

import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { matchRouteDefinition } from "@/lib/routes";

/**
 * Map abstract role-group names used in routes.ts to concrete Keycloak roles.
 * Must stay aligned with backend SecurityConfig.java role-group arrays.
 */
export const ROLE_GROUPS: Record<string, string[]> = {
  /** Sovereign registry / HIE governance plane — distinct from facility org admin. */
  REGISTRY_ADMIN: ["SYSTEM_ADMIN", "HIE_ADMIN"],
  /** Facility and enterprise operations (includes finance operators). */
  ORGANIZATION_ADMIN: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER", "FINANCE"],
  /**
   * Identity + trust operations: facility/developers plus HIE registry operators.
   * Used for id-services and selected trust admin routes so HIE_ADMIN can reach real tooling.
   */
  ADMIN_OR_HIE: ["SYSTEM_ADMIN", "HIE_ADMIN", "FACILITY_ADMIN", "DEVELOPER"],
  ADMIN: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"],
  FINANCE: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"],
  CLINICAL: ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  PRESCRIBER: ["CLINICIAN", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  DISPENSER: ["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  QUEUE: ["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  CITIZEN: ["CITIZEN", "SYSTEM_ADMIN", "DEVELOPER"],
};

export function matchesRequiredRole(hasRole: (r: string) => boolean, requiredRole: string): boolean {
  const group = ROLE_GROUPS[requiredRole];
  if (group) return group.some((r) => hasRole(r));
  return hasRole(requiredRole);
}

export function AuthGuardProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, hasRole } = useAuthStore();
  const { hasFacility } = useFacilityStore();
  const { hasWorkspace } = useWorkspaceStore();
  const { hasShift } = useShiftStore();

  useEffect(() => {
    const routeInfo = matchRouteDefinition(pathname);
    if (!routeInfo) return;

    const { guard, requiredRole } = routeInfo;

    switch (guard) {
      case "none":
        return;
      case "auth":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        break;
      case "facility":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasFacility) { router.replace("/facility"); return; }
        break;
      case "workspace":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasFacility) { router.replace("/facility"); return; }
        if (!hasWorkspace) { router.replace("/workspace"); return; }
        break;
      case "shift":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasFacility) { router.replace("/facility"); return; }
        if (!hasWorkspace) { router.replace("/workspace"); return; }
        if (!hasShift) { router.replace("/shift"); return; }
        break;
      case "role":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (requiredRole && !matchesRequiredRole(hasRole, requiredRole)) { router.replace("/home"); return; }
        break;
    }
  }, [pathname, isAuthenticated, hasFacility, hasWorkspace, hasShift, hasRole, router]);

  return <>{children}</>;
}
