/**
 * Experience UI — Auth Context Store (Zustand)
 *
 * Provider tree position: QueryClient > [AuthProvider] > Facility > Workspace > Shift > Router
 * Persistence keys: exp:auth_token, exp:auth_user, exp:refresh_token, exp:expires_at (sessionStorage)
 */

import { create } from "zustand";
import { hasPersistedExperienceContinuity, resetExperienceContinuity } from "@/lib/session-continuity";

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
  refreshToken: string | null;
  expiresAt: string | null;
  isAuthenticated: boolean;
  setAuth: (user: AuthUser, token: string, refreshToken?: string | null, expiresAt?: string | null) => void;
  setTokens: (token: string, refreshToken?: string | null, expiresAt?: string | null) => void;
  clearAuth: () => void;
  hasRole: (role: string) => boolean;
  isTokenExpired: () => boolean;
  getRefreshToken: () => string | null;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  token: null,
  refreshToken: null,
  expiresAt: null,
  isAuthenticated: false,

  setAuth: (user, token, refreshToken, expiresAt) => {
    const currentUser = get().user;
    const shouldResetContinuity = currentUser
      ? currentUser.id !== user.id
      : typeof window !== "undefined" &&
        !sessionStorage.getItem("exp:auth_user") &&
        hasPersistedExperienceContinuity();

    if (shouldResetContinuity) {
      resetExperienceContinuity();
    }

    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:auth_token", token);
      sessionStorage.setItem("exp:auth_user", JSON.stringify(user));
      if (refreshToken) sessionStorage.setItem("exp:refresh_token", refreshToken);
      if (expiresAt) sessionStorage.setItem("exp:expires_at", expiresAt);
    }
    set({ user, token, refreshToken: refreshToken ?? null, expiresAt: expiresAt ?? null, isAuthenticated: true });
  },

  setTokens: (token, refreshToken, expiresAt) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:auth_token", token);
      if (refreshToken) sessionStorage.setItem("exp:refresh_token", refreshToken);
      if (expiresAt) sessionStorage.setItem("exp:expires_at", expiresAt);
    }
    set({ token, refreshToken: refreshToken ?? get().refreshToken, expiresAt: expiresAt ?? get().expiresAt });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:auth_token");
      sessionStorage.removeItem("exp:auth_user");
      sessionStorage.removeItem("exp:refresh_token");
      sessionStorage.removeItem("exp:expires_at");
    }
    resetExperienceContinuity();
    set({ user: null, token: null, refreshToken: null, expiresAt: null, isAuthenticated: false });
  },

  hasRole: (role) => {
    const { user } = get();
    return user?.roles.includes(role) ?? false;
  },

  isTokenExpired: () => {
    const { expiresAt } = get();
    if (!expiresAt) return false;
    return new Date(expiresAt).getTime() - 60000 < Date.now();
  },

  getRefreshToken: () => {
    const { refreshToken } = get();
    if (refreshToken) return refreshToken;
    if (typeof window !== "undefined") {
      return sessionStorage.getItem("exp:refresh_token");
    }
    return null;
  },
}));
