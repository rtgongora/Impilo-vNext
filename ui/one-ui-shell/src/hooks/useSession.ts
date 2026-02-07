import { create } from "zustand";
import type { SessionInfo } from "@/lib/contracts";

interface SessionState {
  session: SessionInfo | null;
  isAuthenticated: boolean;
  setSession: (session: SessionInfo) => void;
  clearSession: () => void;
  updateToken: (accessToken: string, expiresAt: number) => void;
}

export const useSession = create<SessionState>((set) => ({
  session: null,
  isAuthenticated: false,

  setSession: (session) =>
    set({ session, isAuthenticated: true }),

  clearSession: () =>
    set({ session: null, isAuthenticated: false }),

  updateToken: (accessToken, expiresAt) =>
    set((state) => ({
      session: state.session
        ? { ...state.session, accessToken, expiresAt }
        : null,
    })),
}));
