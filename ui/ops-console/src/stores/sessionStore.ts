/**
 * Ops Console — Session & Work Context Store
 *
 * Minimal Zustand store for operator session state.
 * Used by apiClient to inject trust headers on every request.
 */

import { create } from "zustand";

interface OperatorSession {
  actorId: string;
  actorType: "OPERATOR";
  displayName: string;
  accessToken: string;
  expiresAt: number;
}

interface WorkContext {
  tenantId: string;
  facilityId?: string;
}

interface SessionState {
  session: OperatorSession | null;
  workContext: WorkContext | null;
  purposeOfUse: string;

  setSession: (session: OperatorSession | null) => void;
  setWorkContext: (ctx: WorkContext | null) => void;
}

export const useOpsSession = create<SessionState>((set) => ({
  session: null,
  workContext: null,
  purposeOfUse: "OPERATIONS",

  setSession: (session) => set({ session }),
  setWorkContext: (workContext) => set({ workContext }),
}));
