import { create } from "zustand";

interface SessionState {
  accessToken: string | null;
  tenantId: string | null;
  actorId: string | null;
  actorType: string | null;
  facilityId: string | null;
  workspaceId: string | null;
  purposeOfUse: string;
  setSession: (s: Partial<SessionState>) => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  accessToken: null,
  tenantId: null,
  actorId: null,
  actorType: null,
  facilityId: null,
  workspaceId: null,
  purposeOfUse: "ADMIN",
  setSession: (s) => set((prev) => ({ ...prev, ...s })),
}));
