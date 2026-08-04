/**
 * DENIED is a product state, not a redirect (CLAUDE_GOVERNANCE.md #19:
 * `EMPTY`, `UNAVAILABLE`, `DEGRADED`, `DENIED`, `STALE` and `LOADING` are
 * distinct product states).
 *
 * A failed `role` guard used to be answered with `router.replace("/home")`.
 * The page mounted, the guard fired, and the person was back where they
 * started — a flash, then home, with no message and no state. A mis-targeted
 * link (a citizen offered `/coverage`, which is the ADMIN payer console) was
 * therefore reported as "the Coverage page doesn't open" rather than as a
 * permissions decision. DENIED was indistinguishable from broken.
 *
 * `UnregisteredRouteDenied` in `AuthGuardProvider` already established the
 * opposite treatment for the registry dimension — an explicit surface,
 * deliberately instead of "being silently bounced". This is the role
 * dimension's equivalent.
 *
 * Three design decisions are settled here, because each one is a way this
 * surface could itself become dishonest:
 *
 * 1. The required role is NOT named. Not for confidentiality — `routes.ts`
 *    (every `requiredRole`) and `role-groups.ts` (every group expansion) are
 *    imported by `"use client"` modules and therefore already ship in the
 *    browser bundle to everyone, signed in or not. Naming the role leaks
 *    nothing. It is omitted because "you need ADMIN" is an internal Keycloak
 *    token, not an answer: it tells a citizen nothing actionable and invites
 *    them to pursue a grant they should not have.
 *
 * 2. No step-up is offered. Assurance and role are different dimensions —
 *    re-authenticating or raising assurance does not grant a role, so a
 *    "verify to continue" affordance here would be a button that cannot work.
 *    `action-trust-matrix.ts` owns the genuine assurance dimension and has its
 *    own `upgradePath`; this surface must not borrow that affordance. The copy
 *    says so explicitly, because the wrong remedy is the one people try first.
 *
 * 3. No "request access" link. The real self-service seam
 *    (`/professional/request-access`) is currently absent from the route
 *    registry, so deny-by-default renders it as "This page is not available" —
 *    linking to it would rebuild the dead end this change exists to remove.
 *    Once that route is registered, a conditional link belongs here for actors
 *    who genuinely have professional standing.
 *
 * Scope: the ROLE dimension only. Facility, workspace, shift and provider
 * guards redirect into a context-selection flow, which is a legitimate
 * continuation rather than a dead end — the same boundary `route-reachability`
 * draws.
 */

import { matchRouteDefinition } from "@/lib/routes";
import { matchesRequiredRole } from "@/lib/auth/role-groups";
import type { WorkRouteVisibility } from "@/lib/auth/work-route-visibility";

export interface RoleDenialInput {
  pathname: string;
  /** sessionStorage has been read. Before this, every store reads empty. */
  sessionRestoreAttempted: boolean;
  isAuthenticated: boolean;
  consentHydrated: boolean;
  hasConsented: boolean;
  /** Outcome of the earlier assurance gate: false when it would redirect. */
  assuranceAllows: boolean;
  /** Outcome of the earlier work/pro/life boundary: false when it would redirect. */
  boundaryAllows: boolean;
  /** Outcome of the earlier contract-visibility gate. */
  visibility: WorkRouteVisibility;
  hasRole: (role: string) => boolean;
}

export type RoleDenialDecision =
  | { denied: false }
  | { denied: true; pathname: string; pageTitle: string };

/**
 * Deny only where the effect's guard chain would actually have reached the
 * `role` case and failed it.
 *
 * Every earlier gate is re-checked, in the effect's order, because this runs at
 * RENDER — before the effect has had a chance to redirect. Showing DENIED to
 * someone who is merely mid-hydration, or who is about to be sent to login or
 * consent, would replace a false "broken" state with a false "refused" one,
 * which is the same defect wearing different copy.
 */
export function evaluateRoleDenial(input: RoleDenialInput): RoleDenialDecision {
  const no: RoleDenialDecision = { denied: false };

  // LOADING is not DENIED.
  if (!input.sessionRestoreAttempted) return no;
  // The effect sends unauthenticated visitors to login; that is not a refusal.
  if (!input.isAuthenticated) return no;
  // Consent gate outranks role and redirects; wait for it to settle either way.
  if (!input.consentHydrated || !input.hasConsented) return no;
  // Assurance and boundary gates outrank role and have their own destinations.
  if (!input.assuranceAllows || !input.boundaryAllows) return no;
  // "wait" is LOADING; "block" is the contract's own redirect, not this one.
  if (input.visibility !== "allow") return no;

  const route = matchRouteDefinition(input.pathname);
  if (!route || route.guard !== "role" || !route.requiredRole) return no;
  if (matchesRequiredRole(input.hasRole, route.requiredRole)) return no;

  return { denied: true, pathname: input.pathname, pageTitle: route.pageTitle };
}
