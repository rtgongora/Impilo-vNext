/**
 * Pure (RN-runtime-free) tier resolution — unit-testable in isolation.
 * See the Future-Realism style guide §6.
 */
export type Tier = "baseline" | "enhanced" | "cinematic";

export interface TierSignals {
  reduceMotion: boolean;
  reduceTransparency: boolean;
  /** True for known low-end devices (RAM/year-class); supply from expo-device when wired. */
  lowTier?: boolean;
}

/** Any a11y or device constraint → the always-shippable accessible baseline. */
export function resolveMobileTier(s: TierSignals): Tier {
  if (s.reduceMotion || s.reduceTransparency || s.lowTier) return "baseline";
  // `cinematic` requires an explicit high-end opt-in; not inferred here (avoids an expo-device hard dep).
  return "enhanced";
}
