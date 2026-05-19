/**
 * Experience UI — Consent Store (Zustand)
 *
 * Tracks whether the authenticated user has accepted the current
 * Privacy Policy and Terms of Use. Consent state is persisted to
 * localStorage and, when the backend is available, recorded
 * server-side for audit.
 *
 * Consent version follows the policy effective date (e.g. "2026-04-11").
 * When the policy is updated, the version changes, and all users are
 * prompted to re-accept on their next session.
 */

import { create } from "zustand";

/** Must match the "Effective Date" in /privacy and /terms pages. */
export const CURRENT_CONSENT_VERSION = "2026-04-11";

const STORAGE_KEY = "exp:consent_accepted";
const STORAGE_VERSION_KEY = "exp:consent_version";

export interface ConsentRecord {
  /** ISO-8601 timestamp when consent was granted. */
  acceptedAt: string;
  /** Policy version that was accepted. */
  version: string;
  /** User ID who accepted. */
  userId: string;
}

interface ConsentState {
  /** True when the current user has accepted the current consent version. */
  hasConsented: boolean;
  /** The stored consent record, if any. */
  record: ConsentRecord | null;
  /** Accept the current policy version. */
  acceptConsent: (userId: string) => void;
  /** Withdraw consent (used during account deletion flow). */
  revokeConsent: () => void;
  /** Hydrate from localStorage. Called by StoreHydrator. */
  hydrate: (userId: string) => void;
}

export const useConsentStore = create<ConsentState>((set) => ({
  hasConsented: false,
  record: null,

  acceptConsent: (userId: string) => {
    const record: ConsentRecord = {
      acceptedAt: new Date().toISOString(),
      version: CURRENT_CONSENT_VERSION,
      userId,
    };

    if (typeof window !== "undefined") {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(record));
      localStorage.setItem(STORAGE_VERSION_KEY, CURRENT_CONSENT_VERSION);
    }

    set({ hasConsented: true, record });
  },

  revokeConsent: () => {
    if (typeof window !== "undefined") {
      localStorage.removeItem(STORAGE_KEY);
      localStorage.removeItem(STORAGE_VERSION_KEY);
    }
    set({ hasConsented: false, record: null });
  },

  hydrate: (userId: string) => {
    if (typeof window === "undefined") return;

    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (!stored) {
        set({ hasConsented: false, record: null });
        return;
      }

      const record: ConsentRecord = JSON.parse(stored);

      // Consent is valid only if the version matches and the user matches
      if (
        record.version === CURRENT_CONSENT_VERSION &&
        record.userId === userId
      ) {
        set({ hasConsented: true, record });
      } else {
        // Stale consent — clear and require re-acceptance
        localStorage.removeItem(STORAGE_KEY);
        localStorage.removeItem(STORAGE_VERSION_KEY);
        set({ hasConsented: false, record: null });
      }
    } catch {
      set({ hasConsented: false, record: null });
    }
  },
}));
