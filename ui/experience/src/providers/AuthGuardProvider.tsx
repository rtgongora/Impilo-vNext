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
import { useConsentStore } from "@/hooks/useConsentStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { matchRouteDefinition } from "@/lib/routes";
import { isSchedulingClusterPath } from "@/lib/scheduling-paths";

/** Paths that bypass the consent gate (legal pages, consent page itself, auth). */
const CONSENT_EXEMPT_PREFIXES = ["/auth", "/consent", "/privacy", "/terms", "/account-deletion", "/kiosk", "/verify", "/share"];

/**
 * Map abstract role-group names used in routes.ts to concrete Keycloak roles.
 * Must stay aligned with backend SecurityConfig.java role-group arrays.
 *
 * Per Health OS Doctrine §4: Role-based means the system adapts what is visible,
 * enabled, required, or emphasized according to the active role and context.
 * This includes regulated professional roles, caregiving roles, operational roles,
 * administrative roles, and non-clinical participation roles.
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
  PAYER_OPS: ["SYSTEM_ADMIN", "FINANCE", "DEVELOPER"],
  MSIKA_GOVERNANCE: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE", "DEVELOPER"],
  CLINICAL: ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  PRESCRIBER: ["CLINICIAN", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  DISPENSER: ["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  QUEUE: ["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  CITIZEN: ["CITIZEN", "SYSTEM_ADMIN", "DEVELOPER"],
  /** Health OS §4: Caregiving roles — delegated care partners and family caregivers. */
  CAREGIVER: ["CAREGIVER", "CARE_PARTNER", "CITIZEN", "SYSTEM_ADMIN"],
  /** Health OS §7: Broad operational and public health roles (+ DEVELOPER, aligned with SecurityConfig). */
  PUBLIC_HEALTH: ["PUBLIC_HEALTH_OFFICER", "ENV_HEALTH", "CHW", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  /** Health OS §10: App/module extensibility — roles for governed extensions. */
  COMMERCE: ["FINANCE", "CLINICIAN", "NURSE", "PHARMACIST", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
};

export function matchesRequiredRole(hasRole: (r: string) => boolean, requiredRole: string): boolean {
  const group = ROLE_GROUPS[requiredRole];
  if (group) return group.some((r) => hasRole(r));
  return hasRole(requiredRole);
}

export function AuthGuardProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, hasRole, hasActiveProvider } = useAuthStore();
  const { hasConsented } = useConsentStore();
  const { hasFacility } = useFacilityStore();
  const { hasWorkspace } = useWorkspaceStore();
  const { hasShift } = useShiftStore();

  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    // Consent gate: redirect authenticated users who haven't consented,
    // unless they're on a consent-exempt path.
    if (
      isAuthenticated &&
      !hasConsented &&
      !CONSENT_EXEMPT_PREFIXES.some((p) => pathname.startsWith(p))
    ) {
      router.replace(`/consent?returnTo=${encodeURIComponent(pathname)}`);
      return;
    }

    // Assurance tier gate: restrict UNVERIFIED users from health pages
    const HEALTH_RESTRICTED_PREFIXES = [
      "/ehr", "/clinical", "/pharmacy", "/lab", "/queue",
      "/scheduling", "/shift", "/telemedicine",
    ];

    if (
      isAuthenticated &&
      user?.assuranceLevel === "UNVERIFIED" &&
      HEALTH_RESTRICTED_PREFIXES.some((p) => pathname.startsWith(p))
    ) {
      router.replace("/auth/register/assurance");
      return;
    }

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
        if (!hasWorkspace) {
          // Organization operators reach roster/on-call from /organization-admin/staffing
          // without picking a clinical workspace; facility context is enough for staffing APIs.
          if (isSchedulingClusterPath(pathname) && matchesRequiredRole(hasRole, "ORGANIZATION_ADMIN")) {
            break;
          }
          router.replace("/workspace");
          return;
        }
        break;
      case "shift":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasFacility) { router.replace("/facility"); return; }
        if (!hasWorkspace) { router.replace("/workspace"); return; }
        if (!hasShift) { router.replace("/shift"); return; }
        break;
      case "provider":
        // Health OS §6: "Sign in as a person; practice as a provider only under activated Provider ID."
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasActiveProvider()) {
          router.replace(`/provider/activate?returnTo=${encodeURIComponent(pathname)}`);
          return;
        }
        if (!hasFacility) { router.replace("/facility"); return; }
        break;
      case "role":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (requiredRole && !matchesRequiredRole(hasRole, requiredRole)) { router.replace("/home"); return; }
        break;
    }
  }, [pathname, isAuthenticated, hasConsented, hasFacility, hasWorkspace, hasShift, hasRole, hasActiveProvider, user, router]);

  return <>{children}</>;
}
