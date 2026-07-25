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
import { isGovernanceWorkPathGrantedBySession } from "@/lib/administration-governance";
import { isRouteBlockedForCitizen } from "@/lib/identity-context";
import { evaluateClinicalWorkAccess } from "@/lib/work-pro-life-boundary";
import { sessionContractAllowsRoute } from "@/lib/trust";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { buildContextGuardRedirect } from "@/lib/resolve-post-login-destination";
import { matchRouteDefinition } from "@/lib/routes";
import { isSchedulingClusterPath } from "@/lib/scheduling-paths";
import { evaluateRouteTrust } from "@/lib/auth/action-trust-matrix";

export { ROLE_GROUPS, matchesRequiredRole };

const CONSENT_EXEMPT_PREFIXES = [
  "/",
  "/welcome",
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
  const { hasConsented, hydrated: consentHydrated } = useConsentStore();
  const { hasFacility } = useFacilityStore();
  const { hasWorkspace } = useWorkspaceStore();
  const { hasShift } = useShiftStore();

  const user = useAuthStore((s) => s.user);
  const identity = useIdentityContext();
  const { contract, isLoading: contractLoading } = useSessionExperienceContract();

  useEffect(() => {
    // Consent gate: redirect authenticated users who haven't consented,
    // unless they're on a consent-exempt path.
    const isConsentExempt = CONSENT_EXEMPT_PREFIXES.some((p) =>
      p === "/" ? pathname === "/" : pathname.startsWith(p)
    );

    if (
      isAuthenticated &&
      consentHydrated &&
      !hasConsented &&
      !isConsentExempt
    ) {
      router.replace(`/consent?returnTo=${encodeURIComponent(pathname)}`);
      return;
    }

    if (isAuthenticated) {
      const trust = evaluateRouteTrust(pathname, user?.assuranceLevel);
      if (trust && !trust.allowed && trust.upgradePath) {
        router.replace(trust.upgradePath);
        return;
      }
    }

    if (isAuthenticated) {
      const boundary = evaluateClinicalWorkAccess(pathname, {
        healthId: user?.healthId ?? user?.id ?? null,
        providerActivated: user?.providerActivated ?? false,
      });
      if (!boundary.allowed) {
        router.replace(boundary.redirectTo);
        return;
      }
    }

    const routeInfo = matchRouteDefinition(pathname);
    if (!routeInfo) return;

    if (identity.isCitizenOnly && isRouteBlockedForCitizen(pathname, identity)) {
      if (!contract && contractLoading) {
        return;
      }
      const contractGrantsRoute =
        (!!contract && sessionContractAllowsRoute(contract, pathname)) ||
        isGovernanceWorkPathGrantedBySession(contract, pathname);
      if (!contractGrantsRoute) {
        router.replace("/home");
        return;
      }
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
        if (!hasFacility) {
          router.replace(buildContextGuardRedirect("/facility", pathname));
          return;
        }
        break;
      case "workspace":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasFacility) {
          router.replace(buildContextGuardRedirect("/facility", pathname));
          return;
        }
        if (!hasWorkspace) {
          if (isSchedulingClusterPath(pathname) && matchesRequiredRole(hasRole, "ORGANIZATION_ADMIN")) {
            break;
          }
          router.replace(buildContextGuardRedirect("/workspace", pathname));
          return;
        }
        break;
      case "shift":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasFacility) {
          router.replace(buildContextGuardRedirect("/facility", pathname));
          return;
        }
        if (!hasWorkspace) {
          router.replace(buildContextGuardRedirect("/workspace", pathname));
          return;
        }
        if (!hasShift) {
          router.replace(buildContextGuardRedirect("/shift", pathname));
          return;
        }
        break;
      case "provider":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (!hasActiveProvider()) {
          router.replace(`/provider/activate?returnTo=${encodeURIComponent(pathname)}`);
          return;
        }
        if (!hasFacility) {
          router.replace(buildContextGuardRedirect("/facility", pathname));
          return;
        }
        break;
      case "role":
        if (!isAuthenticated) { router.replace("/auth/login"); return; }
        if (requiredRole && !matchesRequiredRole(hasRole, requiredRole)) { router.replace("/home"); return; }
        break;
    }
  }, [pathname, isAuthenticated, hasConsented, consentHydrated, hasFacility, hasWorkspace, hasShift, hasRole, hasActiveProvider, user, identity, contract, contractLoading, router]);

  return <>{children}</>;
}
