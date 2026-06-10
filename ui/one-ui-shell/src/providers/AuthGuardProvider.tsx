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
import { useIdentityContext } from "@/hooks/useIdentityContext";
import { matchesRequiredRole, ROLE_GROUPS } from "@/lib/auth/role-groups";
import { isRouteBlockedForCitizen } from "@/lib/identity-context";
import { matchRouteDefinition } from "@/lib/routes";
import { isSchedulingClusterPath } from "@/lib/scheduling-paths";

/** Re-export for existing imports from this module. */
export { ROLE_GROUPS, matchesRequiredRole };

/** Paths that bypass the consent gate (legal pages, consent page itself, auth). */
const CONSENT_EXEMPT_PREFIXES = [
  "/auth",
  "/consent",
  "/privacy",
  "/terms",
  "/account-deletion",
  "/kiosk",
  "/verify",
  "/share",
  "/collaboration",
];

export function AuthGuardProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, hasRole, hasActiveProvider } = useAuthStore();
  const { hasConsented } = useConsentStore();
  const { hasFacility } = useFacilityStore();
  const { hasWorkspace } = useWorkspaceStore();
  const { hasShift } = useShiftStore();

  const user = useAuthStore((s) => s.user);
  const identity = useIdentityContext();

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

    if (identity.isCitizenOnly && isRouteBlockedForCitizen(pathname, identity)) {
      router.replace("/home");
      return;
    }

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
  }, [pathname, isAuthenticated, hasConsented, hasFacility, hasWorkspace, hasShift, hasRole, hasActiveProvider, user, identity, router]);

  return <>{children}</>;
}
