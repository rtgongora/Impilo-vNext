/**
 * Experience UI — Privacy Display Store (Zustand)
 *
 * Controls PII visibility levels within the Health Record and clinical views.
 * Per Health OS doctrine: "No PII in SHR" — BUTANO uses CPID only; PII stays in VITO.
 *
 * Privacy levels:
 *   FULL    — All PII visible (default for treating provider with active encounter)
 *   PARTIAL — Name visible, DOB as age-only, IDs partially masked
 *   MINIMAL — Initials + CPID only, all direct identifiers hidden
 *
 * The level can be:
 *   - Toggled manually by the user (persisted to sessionStorage)
 *   - Automatically set based on context (e.g. MINIMAL when no active encounter,
 *     screen share detected, or privacy mode enabled)
 *
 * Components read `maskPII()` from this store to determine how to render
 * each PII field. This keeps masking logic centralized.
 */

import { create } from "zustand";

export type PrivacyLevel = "FULL" | "PARTIAL" | "MINIMAL";

const STORAGE_KEY = "exp:privacy_display_level";

interface PrivacyDisplayState {
  /** Current privacy display level. */
  level: PrivacyLevel;
  /** Whether the user has explicitly toggled privacy mode. */
  isManualOverride: boolean;
  /** Set the privacy level. */
  setLevel: (level: PrivacyLevel) => void;
  /** Cycle through levels: FULL → PARTIAL → MINIMAL → FULL. */
  cycleLevel: () => void;
  /** Hydrate from sessionStorage. */
  hydrate: () => void;
}

const CYCLE_ORDER: PrivacyLevel[] = ["FULL", "PARTIAL", "MINIMAL"];

export const usePrivacyDisplayStore = create<PrivacyDisplayState>((set, get) => ({
  level: "FULL",
  isManualOverride: false,

  setLevel: (level) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem(STORAGE_KEY, level);
    }
    set({ level, isManualOverride: true });
  },

  cycleLevel: () => {
    const current = get().level;
    const idx = CYCLE_ORDER.indexOf(current);
    const next = CYCLE_ORDER[(idx + 1) % CYCLE_ORDER.length];
    get().setLevel(next);
  },

  hydrate: () => {
    if (typeof window === "undefined") return;
    const stored = sessionStorage.getItem(STORAGE_KEY) as PrivacyLevel | null;
    if (stored && CYCLE_ORDER.includes(stored)) {
      set({ level: stored, isManualOverride: true });
    }
  },
}));
