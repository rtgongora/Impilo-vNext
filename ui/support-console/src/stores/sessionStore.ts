/**
 * Support Console — Session & Work Context Store
 *
 * Zustand store for support agent session state.
 * Used by apiClient to inject trust headers on every request.
 */

import { create } from "zustand";

export type SupportRole = "SUPPORT_AGENT" | "SUPPORT_LEAD" | "INCIDENT_COORDINATOR" | "HELPDESK_ADMIN";

interface SupportSession {
  actorId: string;
  actorType: "OPERATOR";
  displayName: string;
  accessToken: string;
  expiresAt: number;
  roles: SupportRole[];
}

interface WorkContext {
  tenantId: string;
  facilityId?: string;
}

interface SessionState {
  session: SupportSession | null;
  workContext: WorkContext | null;
  purposeOfUse: string;

  setSession: (session: SupportSession | null) => void;
  setWorkContext: (ctx: WorkContext | null) => void;
  hasRole: (role: SupportRole) => boolean;
  isAuthenticated: () => boolean;
}

export const useSupportSession = create<SessionState>((set, get) => ({
  session: null,
  workContext: null,
  purposeOfUse: "OPERATIONS",

  setSession: (session) => set({ session }),
  setWorkContext: (workContext) => set({ workContext }),
  hasRole: (role) => get().session?.roles.includes(role) ?? false,
  isAuthenticated: () => {
    const s = get().session;
    return s !== null && s.expiresAt > Date.now();
  },
}));
