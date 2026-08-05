/**
 * Would these roles open this hub section's destination?
 *
 * Online this question is answered by the experience BFF, which withholds sections whose routes
 * the caller's roles refuse. Offline there is no BFF to ask, and the bundled layout was shown to
 * everyone unfiltered — so a nurse with no connection saw Administration, Registry Administration
 * and the Developer Portal, and tapping any of them left the app, opened a browser and landed on
 * /home with no explanation.
 *
 * The requirement is not restated here. It is carried on each section by
 * `hubCatalogue.generated.ts`, resolved from the route registry at generation time, so this file
 * holds only the matching rule — the same rule as `matchesRequiredRole` in the web shell and
 * `RouteGuardRegistry` in the BFF.
 */

import { HUB_ROLE_GROUPS } from "./hubCatalogue.generated";

/** A section carrying the role requirement its route declares (absent = refuses no role). */
export type RoleGuardedSection = { requiredRole?: string };

export function admitsRole(requiredRole: string | undefined, roles: readonly string[]): boolean {
  // No requirement is not a silent denial: auth-guarded routes, and destinations the route
  // registry does not know, refuse no role and must stay visible.
  if (!requiredRole) return true;
  const group = HUB_ROLE_GROUPS[requiredRole];
  if (group) return group.some((role) => roles.includes(role));
  // Not a group name — treat it as a literal realm role, as the shell helper does.
  return roles.includes(requiredRole);
}

export function reachableByRole<T extends RoleGuardedSection>(
  sections: readonly T[],
  roles: readonly string[],
): T[] {
  return sections.filter((section) => admitsRole(section.requiredRole, roles));
}
