/**
 * Experience UI — Work-Session Token Store (Zustand) — D-P3 / PJ5.
 *
 * Holds the duty-scoped WORK_CONTEXT token minted by the BFF
 * (POST /internal/v1/work-context/session) after it proves the requested
 * facility/workspace against the actor's ACTIVE Vashandi assignments. This is
 * DISTINCT from the shift/facility/workspace UI stores: those record the local
 * picker state; THIS records the server-proven, revocable session identity.
 *
 * The `jti` is the revocation handle — on a workplace switch it is passed back
 * to the BFF as `previousJti` so the old token is revoked before the new one is
 * minted, and two contexts are never live for one session.
 *
 * Persistence key: exp:work-session (sessionStorage — cleared on tab close).
 */

import { create } from "zustand";

export interface WorkSession {
  jti: string;
  token: string;
  facilityId: string;
  workspaceId: string | null;
  roleTemplateId: string | null;
  expiresAt: string | null;
  // Phase F8/F9 (mode-aware Work Home switching, POST /work-context/session/mode) — additive.
  // The original mint flow above is facility/workspace-only; a resolved context may instead
  // anchor to an organisation, jurisdiction or programme, so `facilityId` alone cannot carry
  // it. `contextId` is what `useWorkHome` needs to compose the right family's sections.
  contextId?: string;
  workMode?: string;
  /** Org-scoped regulatory duty (POST with organisationId). */
  organisationId?: string;
}

interface WorkSessionState {
  session: WorkSession | null;
  hasSession: boolean;
  setSession: (session: WorkSession) => void;
  clearSession: () => void;
}

const STORAGE_KEY = "exp:work-session";

function readInitial(): WorkSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as WorkSession) : null;
  } catch {
    return null;
  }
}

const initial = readInitial();

export const useWorkSessionStore = create<WorkSessionState>((set) => ({
  session: initial,
  hasSession: initial !== null,

  setSession: (session: WorkSession) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    }
    set({ session, hasSession: true });
  },

  clearSession: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem(STORAGE_KEY);
    }
    set({ session: null, hasSession: false });
  },
}));
