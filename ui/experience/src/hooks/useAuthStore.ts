/**
 * Experience UI — Auth Context Store (Zustand)
 *
 * Provider tree position: QueryClient > [AuthProvider] > Facility > Workspace > Shift > Router
 * Persistence key: exp:auth (sessionStorage)
 */

import { create } from "zustand";

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  actorType: "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM";
}

interface AuthState {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  setAuth: (user: AuthUser, token: string) => void;
  clearAuth: () => void;
  hasRole: (role: string) => boolean;
}

export const useAuthStore = create<AuthState>((set: (partial: Partial<AuthState>) => void, get: () => AuthState) => ({
  user: null,
  token: null,
  isAuthenticated: false,

  setAuth: (user: AuthUser, token: string) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:auth_token", token);
      sessionStorage.setItem("exp:auth_user", JSON.stringify(user));
    }
    set({ user, token, isAuthenticated: true });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:auth_token");
      sessionStorage.removeItem("exp:auth_user");
    }
    set({ user: null, token: null, isAuthenticated: false });
  },

  hasRole: (role: string) => {
    const { user } = get();
    return user?.roles.includes(role) ?? false;
  },
}));
