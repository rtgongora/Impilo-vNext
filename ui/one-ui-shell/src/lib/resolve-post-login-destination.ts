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
import { matchRouteDefinition } from "@/lib/routes";

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
}

export interface PostLoginDestinationResult {
  href: string;
  operationalMode?: OperationalMode;
  autoActivateProvider?: boolean;
  linkedProviderId?: string | null;
}

const AUTH_EXEMPT_PREFIXES = ["/auth", "/consent"];

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

export function resolvePostLoginDestination(
  input: ResolvePostLoginDestinationInput,
): PostLoginDestinationResult {
  const { user, returnTo, hasFacility = false } = input;
  const linkedProviderId =
    input.linkedProviderId ??
    input.linkedIds?.providerId ??
    user?.linkedIds?.providerId ??
    null;

  const context = resolveIdentityContext({
    user: user as AuthUser | null,
    linkedIds: input.linkedIds ?? {
      providerId: linkedProviderId ?? undefined,
      providerStatus: input.linkedIds?.providerStatus,
      licenceValid: input.linkedIds?.licenceValid,
    },
    affiliations: input.affiliations,
    workAssignments: input.workAssignments,
    loginMethod: input.loginMethod,
    hasFacility,
  });

  if (isSafeReturnTo(returnTo)) {
    if (context.isCitizenOnly && routeRequiresFacility(returnTo)) {
      return { href: "/home", operationalMode: "my_life" };
    }
    if (routeRequiresFacility(returnTo) && !hasFacility) {
      return { href: withReturnTo("/facility", returnTo), operationalMode: "facility_work" };
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
    const target = context.defaultLandingPath === "/home" ? "/provider-workspace" : context.defaultLandingPath;
    if (!hasFacility && (target === "/provider-workspace" || target.startsWith("/clinical"))) {
      return {
        href: withReturnTo("/facility", "/provider-workspace"),
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
      href: `/provider/activate?returnTo=${encodeURIComponent("/provider-workspace")}`,
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
