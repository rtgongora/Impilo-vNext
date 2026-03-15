/**
 * Developer Console — Session & Work Context Store
 *
 * Zustand store for developer/partner session state.
 * Used by apiClient to inject trust headers on every request.
 */

import { create } from "zustand";

export type DeveloperRole = "DEVELOPER" | "PARTNER_ADMIN" | "PLATFORM_ADMIN";

interface DeveloperSession {
  actorId: string;
  actorType: "OPERATOR";
  displayName: string;
  accessToken: string;
  expiresAt: number;
  roles: DeveloperRole[];
}

interface WorkContext {
  tenantId: string;
  facilityId?: string;
}

interface SessionState {
  session: DeveloperSession | null;
  workContext: WorkContext | null;
  purposeOfUse: string;
  environment: "SANDBOX" | "PRODUCTION";

  setSession: (session: DeveloperSession | null) => void;
  setWorkContext: (ctx: WorkContext | null) => void;
  setEnvironment: (env: "SANDBOX" | "PRODUCTION") => void;
  hasRole: (role: DeveloperRole) => boolean;
  isAuthenticated: () => boolean;
}

export const useDeveloperSession = create<SessionState>((set, get) => ({
  session: null,
  workContext: null,
  purposeOfUse: "OPERATIONS",
  environment: "SANDBOX",

  setSession: (session) => set({ session }),
  setWorkContext: (workContext) => set({ workContext }),
  setEnvironment: (environment) => set({ environment }),
  hasRole: (role) => get().session?.roles.includes(role) ?? false,
  isAuthenticated: () => {
    const s = get().session;
    return s !== null && s.expiresAt > Date.now();
  },
}));
