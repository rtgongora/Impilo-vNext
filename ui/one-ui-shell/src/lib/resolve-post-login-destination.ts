/**
 * Post-login destination resolver — single source of truth for auth success paths.
 *
 * Used by /auth/resolving and all login success handlers. Honors safe returnTo,
 * facility guards for facility-bound routes, and operational mode hints.
 */

import type { AuthUser } from "@/hooks/useAuthStore";
import type { OperationalMode } from "@/lib/operational-context";
import { matchRouteDefinition } from "@/lib/routes";

export interface ResolvePostLoginDestinationInput {
  user: Pick<AuthUser, "actorType" | "roles" | "providerActivated" | "providerId"> | null;
  linkedProviderId?: string | null;
  hasFacility?: boolean;
  returnTo?: string | null;
}

export interface PostLoginDestinationResult {
  href: string;
  operationalMode?: OperationalMode;
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

function isCitizenPrincipal(
  user: ResolvePostLoginDestinationInput["user"],
): boolean {
  if (!user) return true;
  if (user.actorType === "CITIZEN") return true;
  const roles = user.roles ?? [];
  return roles.length > 0 && roles.every((role) => role === "CITIZEN");
}

function isProviderActivated(
  user: ResolvePostLoginDestinationInput["user"],
): boolean {
  return !!(user?.providerActivated && user.providerId);
}

export function resolvePostLoginDestination(
  input: ResolvePostLoginDestinationInput,
): PostLoginDestinationResult {
  const { user, linkedProviderId, hasFacility = false, returnTo } = input;

  if (isSafeReturnTo(returnTo)) {
    if (routeRequiresFacility(returnTo) && !hasFacility) {
      return { href: withReturnTo("/facility", returnTo) };
    }
    return { href: returnTo };
  }

  if (isProviderActivated(user)) {
    const target = "/provider-workspace";
    if (!hasFacility) {
      return { href: withReturnTo("/facility", target), operationalMode: "facility_work" };
    }
    return { href: target, operationalMode: "facility_work" };
  }

  const pendingProviderId = linkedProviderId ?? user?.providerId;
  if (pendingProviderId && !user?.providerActivated) {
    return {
      href: `/provider/activate?returnTo=${encodeURIComponent("/provider-workspace")}`,
    };
  }

  if (isCitizenPrincipal(user)) {
    return { href: "/home", operationalMode: "my_life" };
  }

  return { href: "/home", operationalMode: "my_life" };
}

/** Path to the identity-resolution screen, preserving an optional returnTo. */
export function buildPostLoginResolvingPath(returnTo?: string | null): string {
  if (isSafeReturnTo(returnTo)) {
    return `/auth/resolving?returnTo=${encodeURIComponent(returnTo)}`;
  }
  return "/auth/resolving";
}
