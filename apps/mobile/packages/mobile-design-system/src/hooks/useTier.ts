import { useEffect, useState } from "react";
import { AccessibilityInfo } from "react-native";
import { resolveMobileTier, type Tier } from "./tier";

/** Re-exported so consumers import tier utilities from one place. */
export { resolveMobileTier } from "./tier";
export type { Tier, TierSignals } from "./tier";

/**
 * Resolve the live tier from OS accessibility settings. `lowTier` lets a caller feed a device-class
 * signal (e.g. from expo-device) without this hook taking that dependency. Pure resolution lives in
 * ./tier (RN-runtime-free, unit-tested).
 */
export function useTier(lowTier = false): Tier {
  const [tier, setTier] = useState<Tier>("enhanced");

  useEffect(() => {
    let mounted = true;
    const recompute = async () => {
      const reduceMotion = await AccessibilityInfo.isReduceMotionEnabled();
      let reduceTransparency = false;
      const ai = AccessibilityInfo as unknown as { isReduceTransparencyEnabled?: () => Promise<boolean> };
      if (typeof ai.isReduceTransparencyEnabled === "function") {
        reduceTransparency = await ai.isReduceTransparencyEnabled();
      }
      if (mounted) setTier(resolveMobileTier({ reduceMotion, reduceTransparency, lowTier }));
    };
    void recompute();
    const sub = AccessibilityInfo.addEventListener("reduceMotionChanged", () => void recompute());
    return () => {
      mounted = false;
      (sub as { remove?: () => void } | undefined)?.remove?.();
    };
  }, [lowTier]);

  return tier;
}
