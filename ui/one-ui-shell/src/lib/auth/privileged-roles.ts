/**
 * Platform override roles — full preview/sandbox visibility parity with SYSTEM_ADMIN.
 * Must stay aligned with Keycloak realm roles and BFF SecurityConfig.
 */

export const PLATFORM_OVERRIDE_ROLES = ["SYSTEM_ADMIN", "DEVELOPER", "SUPER_ADMIN"] as const;

export type PlatformOverrideRole = (typeof PLATFORM_OVERRIDE_ROLES)[number];

/** Merge SUPER_ADMIN into groups that already grant SYSTEM_ADMIN or DEVELOPER override. */
export function expandRoleGroup(roles: readonly string[]): string[] {
  const merged = new Set(roles);
  if (roles.some((role) => role === "SYSTEM_ADMIN" || role === "DEVELOPER")) {
    merged.add("SUPER_ADMIN");
  }
  return [...merged];
}

export function hasPlatformOverride(roles: string[] | undefined): boolean {
  if (!roles?.length) return false;
  return PLATFORM_OVERRIDE_ROLES.some((role) => roles.includes(role));
}
