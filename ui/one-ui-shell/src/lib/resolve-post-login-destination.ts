/**
 * Post-login destination resolver — single source of truth for auth success paths.
 *
 * Delegates identity-context orchestration to identity-context.ts.
 */

import type { AuthUser } from "@/hooks/useAuthStore";
import type { LinkedIdsAttributes } from "@/hooks/queries/useLinkedIds";
import type { FacilityAffiliation, LoginMethod } from "@/lib/identity-context";
import { resolveIdentityContext } from "@/lib/identity-context";
import type { WorkAssignment } from "@/lib/trust";
import type { OperationalMode } from "@/lib/operational-context";
import type { GatewayIntent } from "@/lib/gateway-intent";
import { resolveIntentDestination } from "@/lib/gateway-intent";
import { matchRouteDefinition } from "@/lib/routes";
import { WORK_CONTEXT_ENTRY } from "@/lib/work-home/resume-context";

export interface ResolvePostLoginDestinationInput {
  user: Pick<
    AuthUser,
    "id" | "actorType" | "roles" | "providerActivated" | "providerId" | "linkedIds"
  > | null;
  linkedIds?: LinkedIdsAttributes | null;
  linkedProviderId?: string | null;
  affiliations?: FacilityAffiliation[];
  workAssignments?: WorkAssignment[];
  loginMethod?: LoginMethod;
  hasFacility?: boolean;
  returnTo?: string | null;
  /** When set (e.g. `no_work`), blocked returnTo targets must not be replayed after resolving. */
  resolutionReason?: string | null;
  /**
   * Pending gateway intent (doctrine §4.1 law 3: journey context survives auth).
   * When it carries a safe destination it takes precedence over returnTo; the
   * caller consumes the intent once navigation restores the journey.
   */
  intent?: GatewayIntent | null;
}

export interface PostLoginDestinationResult {
  href: string;
  operationalMode?: OperationalMode;
  autoActivateProvider?: boolean;
  linkedProviderId?: string | null;
  /** Set when the destination was driven by the gateway intent — the caller should consume it. */
  restoredIntent?: GatewayIntent;
}

const AUTH_EXEMPT_PREFIXES = ["/auth", "/consent"];

/**
 * Re-exported so callers of this module keep a single import. Defined in work-home to avoid an
 * import cycle with identity-context.ts, which needs it too — pointing only the route guard at
 * it while the post-login resolver still sent people to `/facility` left the "every time I log
 * in" path untouched, which was the actual complaint.
 */
export { WORK_CONTEXT_ENTRY };

export function isSafeReturnTo(path: string | null | undefined): path is string {
  if (!path || typeof path !== "string") return false;
  if (!path.startsWith("/") || path.startsWith("//")) return false;
  if (AUTH_EXEMPT_PREFIXES.some((prefix) => path.startsWith(prefix))) return false;
  return true;
}

function routeRequiresFacility(path: string): boolean {
  const route = matchRouteDefinition(path);
  if (!route) return false;
  return (
    route.guard === "facility" ||
    route.guard === "workspace" ||
    route.guard === "shift" ||
    route.guard === "provider"
  );
}

function withReturnTo(base: string, returnTo: string): string {
  const separator = base.includes("?") ? "&" : "?";
  return `${base}${separator}returnTo=${encodeURIComponent(returnTo)}`;
}

function isBlockedReturnToAfterResolution(
  returnTo: string,
  resolutionReason: string | null | undefined,
): boolean {
  if (resolutionReason === "no_work" && returnTo.startsWith("/work/")) {
    return true;
  }
  return false;
}

export function resolvePostLoginDestination(
  input: ResolvePostLoginDestinationInput,
): PostLoginDestinationResult {
  const { user, returnTo, hasFacility = false, resolutionReason } = input;
  const linkedProviderId =
    input.linkedProviderId ??
    input.linkedIds?.providerId ??
    user?.linkedIds?.providerId ??
    null;

  const linkedIdsPayload: LinkedIdsAttributes = input.linkedIds ?? {
    providerId: linkedProviderId ?? undefined,
  };

  const context = resolveIdentityContext({
    user: user as AuthUser | null,
    linkedIds: linkedIdsPayload,
    affiliations: input.affiliations,
    workAssignments: input.workAssignments,
    loginMethod: input.loginMethod,
    hasFacility,
  });

  // Gateway intent precedence (doctrine §4.1 law 3): a valid intent destination wins
  // over returnTo, under the exact same safety + context guards. When no intent (or no
  // safe destination) is present, behaviour is unchanged.
  const intentDest = resolveIntentDestination(input.intent ?? null);
  if (intentDest && !isBlockedReturnToAfterResolution(intentDest, resolutionReason)) {
    if (context.isCitizenOnly && routeRequiresFacility(intentDest)) {
      return { href: "/home", operationalMode: "my_life" };
    }
    if (routeRequiresFacility(intentDest) && !hasFacility) {
      // Facility context first; the intent stays pending and replays as returnTo.
      return { href: withReturnTo(WORK_CONTEXT_ENTRY, intentDest), operationalMode: "facility_work" };
    }
    return {
      href: intentDest,
      operationalMode: context.defaultOperationalMode,
      restoredIntent: input.intent ?? undefined,
    };
  }

  if (isSafeReturnTo(returnTo) && !isBlockedReturnToAfterResolution(returnTo, resolutionReason)) {
    if (context.isCitizenOnly && routeRequiresFacility(returnTo)) {
      return { href: "/home", operationalMode: "my_life" };
    }
    if (routeRequiresFacility(returnTo) && !hasFacility) {
      return { href: withReturnTo(WORK_CONTEXT_ENTRY, returnTo), operationalMode: "facility_work" };
    }
    return { href: returnTo, operationalMode: context.defaultOperationalMode };
  }

  if (context.needsProviderStatusResolution) {
    return { href: "/provider/status" };
  }

  if (context.needsContextChooser) {
    return {
      href: "/auth/context-chooser",
      operationalMode: "facility_work",
    };
  }

  if (context.hasProfessionalAccess && !context.hasWorkAccess) {
    return {
      href: "/professional",
      operationalMode: "my_professional",
      linkedProviderId,
    };
  }

  if (context.hasWorkAccess) {
    // Phase F6: /provider-workspace is now an intent-resolution shim to /work — land there
    // directly. The Java-side twin (SessionExperienceService#resolveDefaultRoute) makes the
    // same change; landing-parity is asserted below in this file's own test.
    const target = context.defaultLandingPath === "/home" ? "/work" : context.defaultLandingPath;
    if (!hasFacility && (target === "/work" || target.startsWith("/clinical"))) {
      return {
        href: withReturnTo(WORK_CONTEXT_ENTRY, "/work"),
        operationalMode: "facility_work",
        autoActivateProvider: context.isProviderActivated && !user?.providerActivated,
        linkedProviderId,
      };
    }
    return {
      href: target,
      operationalMode: context.defaultOperationalMode,
      autoActivateProvider: context.isProviderActivated && !user?.providerActivated,
      linkedProviderId,
    };
  }

  if (linkedProviderId && !context.isProviderActivated && !context.needsProviderStatusResolution) {
    return {
      href: `/provider/activate?returnTo=${encodeURIComponent("/work")}`,
      linkedProviderId,
    };
  }

  return {
    href: context.defaultLandingPath,
    operationalMode: context.defaultOperationalMode,
  };
}

/** Path to the identity-resolution screen, preserving an optional returnTo. */
export function buildPostLoginResolvingPath(returnTo?: string | null): string {
  if (isSafeReturnTo(returnTo)) {
    return `/auth/resolving?returnTo=${encodeURIComponent(returnTo)}`;
  }
  return "/auth/resolving";
}

/**
 * Redirect target when a route guard blocks navigation (facility / workspace / shift).
 *
 * `/facility` is deliberately rewritten to `/work/resume`. 148 routes carry `guard: "facility"`
 * and every one of them used to land on the national facility registry, where a clinician had
 * to find their own hospital in a list of thousands — on every sign-in, despite the BFF already
 * having resolved and ranked their real postings. `/work/resume` offers that resolved answer as
 * one tap, mints a proper duty token, and returns them to what they were doing. It falls back
 * to `/facility` itself when someone genuinely holds no resolved work context, so the manual
 * chooser stays reachable rather than being deleted.
 *
 * `/workspace` and `/shift` are unchanged: workspace and shift are not carried on a resolved
 * work context, so there is nothing remembered to offer back yet.
 */
export function buildContextGuardRedirect(
  guardTarget: "/facility" | "/workspace" | "/shift",
  attemptedPath: string,
): string {
  const target = guardTarget === "/facility" ? WORK_CONTEXT_ENTRY : guardTarget;
  if (isSafeReturnTo(attemptedPath) && attemptedPath !== target) {
    return withReturnTo(target, attemptedPath);
  }
  return target;
}

/** After facility pick, continue to returnTo when it only needs facility context. */
export function resolvePostFacilitySelectionPath(returnTo: string | null): string {
  if (!isSafeReturnTo(returnTo)) {
    return "/workspace";
  }
  const route = matchRouteDefinition(returnTo);
  if (!route) {
    return returnTo;
  }
  if (route.guard === "facility") {
    return returnTo;
  }
  if (route.guard === "workspace" || route.guard === "shift" || route.guard === "provider") {
    return withReturnTo("/workspace", returnTo);
  }
  return returnTo;
}
