/**
 * Experience UI — Delegation "acting for" context store (Zustand) — L5 (G-CZO-03).
 *
 * Holds the subject the user is currently acting FOR (as a guardian/caregiver/CHW). When set, the
 * api-client adds X-Subject-ID and the trust plane (PolicyEngine Step 4.5) authorises the delegated
 * action against an active Mvumo delegation. Persisted to sessionStorage (exp:acting_for); the bare
 * subject ref is mirrored to exp:acting_for_subject for the api-client to read.
 */
import { create } from "zustand";

export interface ActingForContext {
  subjectRef: string;
  subjectLabel: string;
  relationshipType: string;
}

interface DelegationState {
  actingFor: ActingForContext | null;
  enterContext: (ctx: ActingForContext) => void;
  exitContext: () => void;
}

const KEY = "exp:acting_for";
const SUBJECT_KEY = "exp:acting_for_subject";

function loadInitial(): ActingForContext | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as ActingForContext) : null;
  } catch {
    return null;
  }
}

export const useDelegationStore = create<DelegationState>(
  (set: (partial: Partial<DelegationState>) => void) => ({
    actingFor: loadInitial(),

    enterContext: (ctx: ActingForContext) => {
      if (typeof window !== "undefined") {
        sessionStorage.setItem(KEY, JSON.stringify(ctx));
        sessionStorage.setItem(SUBJECT_KEY, ctx.subjectRef);
      }
      set({ actingFor: ctx });
    },

    exitContext: () => {
      if (typeof window !== "undefined") {
        sessionStorage.removeItem(KEY);
        sessionStorage.removeItem(SUBJECT_KEY);
      }
      set({ actingFor: null });
    },
  }),
);
