/**
 * Friction Level Hook — Health OS §8 (Graduated Trust)
 *
 * "Friction must be proportionate to risk."
 *
 * The PolicyEngine classifies each action into a friction level:
 *   MINIMAL — wellness browsing, club discovery, search
 *   LOW — non-regulated marketplace browse, device pairing
 *   STANDARD — authenticated access, scheduling
 *   ELEVATED — clinical record access, health data writes
 *   MAXIMUM — prescribing, claims, controlled substance fulfilment
 *
 * The BFF forwards X-Friction-Level from the gateway. This hook reads
 * the last known friction level from session context and provides
 * helpers for friction-aware UI rendering.
 */

import { create } from "zustand";

export type FrictionLevel = "MINIMAL" | "LOW" | "STANDARD" | "ELEVATED" | "MAXIMUM";

const FRICTION_ORDER: FrictionLevel[] = ["MINIMAL", "LOW", "STANDARD", "ELEVATED", "MAXIMUM"];

interface FrictionState {
  level: FrictionLevel;
  setLevel: (level: FrictionLevel) => void;
  /** True if current friction is at or above the given threshold. */
  isAtLeast: (threshold: FrictionLevel) => boolean;
  /** True if action needs confirmation dialog before proceeding. */
  requiresConfirmation: () => boolean;
  /** True if action needs step-up authentication. */
  requiresStepUp: () => boolean;
}

export const useFrictionLevel = create<FrictionState>((set, get) => ({
  level: "STANDARD",

  setLevel: (level) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:friction_level", level);
    }
    set({ level });
  },

  isAtLeast: (threshold) => {
    const current = FRICTION_ORDER.indexOf(get().level);
    const required = FRICTION_ORDER.indexOf(threshold);
    return current >= required;
  },

  requiresConfirmation: () => {
    return get().isAtLeast("ELEVATED");
  },

  requiresStepUp: () => {
    return get().isAtLeast("MAXIMUM");
  },
}));

/** Map friction level to UI treatment. */
export function frictionUiHints(level: FrictionLevel) {
  switch (level) {
    case "MINIMAL":
      return { color: "green", label: "Open access", showWarning: false, requirePin: false };
    case "LOW":
      return { color: "blue", label: "Standard access", showWarning: false, requirePin: false };
    case "STANDARD":
      return { color: "gray", label: "Authenticated", showWarning: false, requirePin: false };
    case "ELEVATED":
      return { color: "amber", label: "Elevated trust", showWarning: true, requirePin: false };
    case "MAXIMUM":
      return { color: "red", label: "Maximum assurance", showWarning: true, requirePin: true };
  }
}
