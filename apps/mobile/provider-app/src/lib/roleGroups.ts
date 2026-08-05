/**
 * Canonical Keycloak role groups, mirrored for the mobile provider app.
 *
 * The realm role vocabulary is UPPERCASE — measured against the live preview
 * realm (66 realm roles, all uppercase; `dr.mapfumo` holds `CLINICIAN`,
 * `courier.banda` holds `COURIER`). The `impilo-mobile-provider` client carries
 * its own `oidc-usermodel-realm-role-mapper` with `userinfo.token.claim=true`,
 * so `/userinfo` returns those uppercase names verbatim in
 * `realm_access.roles`. Never lowercase incoming roles to make a match — the
 * vocabulary is the contract.
 *
 * These arrays are a mirror of `RAW_ROLE_GROUPS` in
 * `ui/one-ui-shell/src/lib/auth/role-groups.ts`, which is itself aligned with
 * the BFF `SecurityConfig` role-group arrays. `roleGroups.drift.test.ts` reads
 * that file and fails RED if the two diverge, so this is a checked mirror
 * rather than another hand-written role list.
 *
 * Only the groups the mobile app actually consumes are mirrored. Add a group
 * here when a mobile surface needs it — the drift test will then hold it too.
 */

/** Mirrors `PLATFORM_OVERRIDE_ROLES` / `expandRoleGroup` in the shell. */
const SUPER_ADMIN = "SUPER_ADMIN";

export const RAW_ROLE_GROUPS = {
  ADMIN: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"],
  CLINICAL: ["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  PUBLIC_HEALTH: ["PUBLIC_HEALTH_OFFICER", "ENV_HEALTH", "CHW", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
  DISPATCH_OPERATIONS: ["DISPATCH_COORDINATOR", "COURIER", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"],
} as const satisfies Record<string, readonly string[]>;

export type RoleGroupName = keyof typeof RAW_ROLE_GROUPS;

/** SUPER_ADMIN inherits every group that already grants SYSTEM_ADMIN or DEVELOPER. */
function expandRoleGroup(roles: readonly string[]): string[] {
  const merged = new Set(roles);
  if (roles.some((role) => role === "SYSTEM_ADMIN" || role === "DEVELOPER")) {
    merged.add(SUPER_ADMIN);
  }
  return [...merged];
}

export const ROLE_GROUPS: Record<RoleGroupName, string[]> = Object.fromEntries(
  Object.entries(RAW_ROLE_GROUPS).map(([key, roles]) => [key, expandRoleGroup(roles)]),
) as Record<RoleGroupName, string[]>;

/** True when `roles` contains at least one role from at least one named group. */
export function hasAnyRoleInGroups(roles: readonly string[], groups: readonly RoleGroupName[]): boolean {
  return groups.some((group) => ROLE_GROUPS[group].some((role) => roles.includes(role)));
}
