/**
 * Which realm roles make an AppMode button visible in the ModeSwitcher.
 *
 * Modes are expressed as canonical role GROUPS, not raw role strings — the
 * previous hand-written lowercase list (`provider`, `clinician`,
 * `community_health_worker`, `driver`, `facility_runner`, …) matched no realm
 * role that has ever existed, so every user fell through to the `["provider"]`
 * default and mode availability was effectively hardcoded. See
 * `src/lib/roleGroups.ts` for the measured vocabulary and the drift test that
 * keeps the groups aligned with the web shell and the BFF.
 *
 * Visibility is a recommendation, never an authority: every governed mode
 * still mints a duty token through `useSwitchAppMode` before the mode moves,
 * and Trust-Layer policy decides what the actor may then do.
 */
import type { AppMode } from "../types";
import { hasAnyRoleInGroups, type RoleGroupName } from "../lib/roleGroups";

export const MODE_ROLE_GROUPS: Record<AppMode, RoleGroupName[]> = {
  provider: ["CLINICAL"],
  /** Community outreach is clinical staff plus the public-health cadres (CHW, ENV_HEALTH, PHO). */
  outreach: ["CLINICAL", "PUBLIC_HEALTH"],
  supervisor: ["ADMIN"],
  /** Offline Edge is connectivity state, open to whoever can work in the field. */
  offline: ["CLINICAL", "PUBLIC_HEALTH"],
  /**
   * Couriers, dispatch coordinators and the ops chain unlock Nhume courier
   * mode; clinical staff keep it too, because facility runners are usually
   * clinical staff. Trust-Layer policy further restricts which deliveries
   * each may act on.
   */
  courier: ["DISPATCH_OPERATIONS", "CLINICAL"],
};

/**
 * Modes the signed-in realm roles alone make visible.
 *
 * Returns `["provider"]` when nothing matches. That default is deliberate and
 * safe rather than a lie: `provider` is a governed mode, so it cannot be
 * entered without a successful duty-token mint against a resolved work
 * context. It offers a starting point without asserting authority.
 */
export function getAvailableModes(roles: readonly string[]): AppMode[] {
  const modes = (Object.keys(MODE_ROLE_GROUPS) as AppMode[]).filter((mode) =>
    hasAnyRoleInGroups(roles, MODE_ROLE_GROUPS[mode]),
  );
  return modes.length > 0 ? modes : ["provider"];
}
