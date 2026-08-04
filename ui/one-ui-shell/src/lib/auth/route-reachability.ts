/**
 * Would this route's role guard let the caller in?
 *
 * `AuthGuardProvider` now answers a failed `role` guard with an explicit DENIED surface
 * (`lib/auth/role-denial.ts`) rather than the silent `router.replace("/home")` bounce that
 * made a refused link read as "this is broken". That makes the refusal honest when it is
 * reached — but being told "not for you" is still a worse outcome than never being offered
 * the link, and deep links, bookmarks and role changes reach it regardless.
 *
 * So any surface that composes its own list of destinations must still filter that list, the way
 * `/ruvimbo` already does ("we only ever surface workspaces the caller can access, so a
 * selection never dead-ends"). This reads the requirement off the route registry rather than
 * taking a hand-copied role list, so a guard change in `routes.ts` cannot silently leave a
 * navigation surface pointing at a door that no longer opens.
 *
 * Scope: the ROLE dimension only. Facility, workspace, shift and provider guards redirect
 * into a context-selection flow that is a legitimate continuation, not a dead end.
 */

import { matchRouteDefinition } from "@/lib/routes";
import { matchesRequiredRole } from "@/lib/auth/role-groups";

export function roleGuardAdmits(href: string, hasRole: (role: string) => boolean): boolean {
  const route = matchRouteDefinition(href);
  if (!route || route.guard !== "role" || !route.requiredRole) return true;
  return matchesRequiredRole(hasRole, route.requiredRole);
}

/** Filter a list of link-bearing items down to the ones the caller can actually open. */
export function reachableByRole<T extends { href: string }>(
  items: readonly T[],
  hasRole: (role: string) => boolean,
): T[] {
  return items.filter((item) => roleGuardAdmits(item.href, hasRole));
}
