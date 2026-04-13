/**
 * Experience UI — Auth Context Store (Zustand)
 *
 * Aligned with Health OS Identity Doctrine (§5–§6):
 *   "Sign in as a person; practice as a provider only under activated Provider ID."
 *
 * Provider tree position: QueryClient > [AuthProvider] > Facility > Workspace > Shift > Router
 * Persistence keys: exp:auth_token, exp:auth_user, exp:refresh_token, exp:expires_at,
 *   exp:provider_id (sessionStorage)
 */

import { create } from "zustand";
import { hasPersistedExperienceContinuity, resetExperienceContinuity } from "@/lib/session-continuity";

/**
 * Authenticated user — the person anchor within the Health Operating System.
 *
 * Per Health OS Identity Doctrine (§5–§6):
 * - `id` is the Health ID (person anchor / canonical health identity)
 * - `providerId` is the optional Provider ID (regulated professional role)
 * - A person signs in with their Health ID; professional work requires
 *   explicit activation of a valid Provider ID
 */
export interface AuthUser {
  /** Health ID — canonical person anchor (VITO-issued). */
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  actorType: "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM" | "CAREGIVER";
  /** Provider ID — regulated professional role identifier (VARAPI-issued). Present only when activated. */
  providerId?: string;
  /** Staff / employee ID within the current organization. */
  staffId?: string;
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
  /** Health OS §6: true when Provider ID is activated for this session. */
  hasActiveProvider: () => boolean;
  /** Health OS §6: activate a Provider ID for regulated professional work. */
  activateProvider: (providerId: string) => void;
  /** Health OS §6: deactivate Provider ID (return to person-only context). */
  deactivateProvider: () => void;
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
      // Health OS §6: persist Provider ID for header injection (api-client.ts reads exp:provider_id)
      if (user.providerId) {
        sessionStorage.setItem("exp:provider_id", user.providerId);
      } else {
        sessionStorage.removeItem("exp:provider_id");
      }
    }
    if (typeof document !== "undefined") {
      document.cookie = "exp_has_session=1;path=/;SameSite=Lax";
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
      sessionStorage.removeItem("exp:consent_accepted");
      sessionStorage.removeItem("exp:consent_version");
    }
    if (typeof document !== "undefined") {
      document.cookie = "exp_has_session=;path=/;expires=Thu, 01 Jan 1970 00:00:00 GMT";
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

  hasActiveProvider: () => {
    const { user } = get();
    if (user?.providerId) return true;
    if (typeof window !== "undefined") {
      return !!sessionStorage.getItem("exp:provider_id");
    }
    return false;
  },

  activateProvider: (providerId: string) => {
    const { user, token, refreshToken, expiresAt } = get();
    if (!user || !token) return;
    const updated = { ...user, providerId };
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:provider_id", providerId);
      sessionStorage.setItem("exp:auth_user", JSON.stringify(updated));
    }
    set({ user: updated });
  },

  deactivateProvider: () => {
    const { user } = get();
    if (!user) return;
    const updated = { ...user, providerId: undefined };
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:provider_id");
      sessionStorage.removeItem("exp:provider_display");
      sessionStorage.removeItem("exp:provider_cadre");
      sessionStorage.setItem("exp:auth_user", JSON.stringify(updated));
    }
    set({ user: updated });
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
