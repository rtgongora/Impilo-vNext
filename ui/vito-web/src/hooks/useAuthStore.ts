import { create } from "zustand";
import { VITO_STORAGE_KEYS } from "@/lib/constants";

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  actorType: "OPERATOR" | "CITIZEN" | "SYSTEM" | "PROVIDER";
}

interface AuthState {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  actorType: AuthUser["actorType"] | null;
  setAuth: (user: AuthUser, token: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  actorType: null,

  setAuth: (user: AuthUser, token: string) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem(VITO_STORAGE_KEYS.AUTH_TOKEN, token);
      sessionStorage.setItem(VITO_STORAGE_KEYS.AUTH_USER, JSON.stringify(user));
    }
    set({ user, token, isAuthenticated: true, actorType: user.actorType });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem(VITO_STORAGE_KEYS.AUTH_TOKEN);
      sessionStorage.removeItem(VITO_STORAGE_KEYS.AUTH_USER);
      sessionStorage.removeItem(VITO_STORAGE_KEYS.TENANT_ID);
      sessionStorage.removeItem(VITO_STORAGE_KEYS.STEP_UP_TOKEN);
    }
    set({ user: null, token: null, isAuthenticated: false, actorType: null });
  },
}));
