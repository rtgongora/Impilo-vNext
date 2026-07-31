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
import { resolveWorkRouteVisibility } from "@/lib/auth/work-route-visibility";

/** Re-export for existing imports from this module. */
export { ROLE_GROUPS, matchesRequiredRole };

/** Paths that bypass the consent gate (legal pages, consent page itself, auth). */
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
  const { isAuthenticated, hasRole, hasActiveProvider, sessionRestoreAttempted } = useAuthStore();
  const { hasConsented, hydrated: consentHydrated } = useConsentStore();
  const { hasFacility } = useFacilityStore();
  const { hasWorkspace } = useWorkspaceStore();
  const { hasShift } = useShiftStore();

  const user = useAuthStore((s) => s.user);
  const identity = useIdentityContext();
  const { contract, isLoading: contractLoading } = useSessionExperienceContract();

  useEffect(() => {
    // The session lives in sessionStorage and is restored by StoreHydrator, which is this
    // component's parent — so React runs this effect first, against a store that is still
    // empty. Every redirect below reads as "no session, no professional identity" on that
    // pass, which is how a full page load of any guarded deep link ended up at /home or
    // /auth/login before the session had been read. Wait for the read to have happened.
    if (!sessionRestoreAttempted) return;

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

    // Assurance tier gate (data-driven — lib/auth/action-trust-matrix.ts). The matrix is a
    // strict superset of the former hard-coded HEALTH_RESTRICTED_PREFIXES bounce: UNVERIFIED
    // users are still blocked from health surfaces (which now require at least TEMPORARY), and
    // the rule set is extensible per-surface. The BFF Session Experience Contract stays
    // authoritative for route visibility; this governs the assurance dimension only.
    if (isAuthenticated) {
      const trust = evaluateRouteTrust(pathname, user?.assuranceLevel);
      if (trust && !trust.allowed && trust.upgradePath) {
        router.replace(trust.upgradePath);
        return;
      }
    }

    // Work / My-Professional / My-Life isolation (L3 W4). The boundary between
    // the three shells is a correctness invariant: work permissions must never
    // reach the actor's OWN citizen record, and a non-activated identity must
    // never transact clinically. Enforced here as defence-in-depth; the Tshepo
    // policy track (WORK-PRO-LIFE-ISOLATION / SELF-TREATMENT-BLOCK) is the
    // authoritative server-side enforcement and owns the break-glass path.
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

    // The BFF Session Experience Contract is authoritative for visibility. The client-side
    // isCitizenOnly heuristic only blocks when the contract does NOT grant the route, so a
    // contract that unlocks work/governance (e.g. an operator with a WGV assignment) is not
    // overridden by stale client identity inference. Neither source may be acted on until it
    // has settled: both start empty, and empty reads the same as "citizen".
    const visibility = resolveWorkRouteVisibility({
      isCitizenOnly: identity.isCitizenOnly,
      identityLoading: identity.isLoading,
      routeBlockedForCitizen: isRouteBlockedForCitizen(pathname, identity),
      hasContract: !!contract,
      contractLoading,
      contractGrantsRoute:
        (!!contract && sessionContractAllowsRoute(contract, pathname)) ||
        isGovernanceWorkPathGrantedBySession(contract, pathname),
    });
    if (visibility === "wait") return;
    if (visibility === "block") {
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
          // Organization operators reach roster/on-call from /organization-admin/staffing
          // without picking a clinical workspace; facility context is enough for staffing APIs.
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
        // Health OS §6: "Sign in as a person; practice as a provider only under activated Provider ID."
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
  }, [pathname, sessionRestoreAttempted, isAuthenticated, hasConsented, consentHydrated, hasFacility, hasWorkspace, hasShift, hasRole, hasActiveProvider, user, identity, contract, contractLoading, router]);

  return <>{children}</>;
}
