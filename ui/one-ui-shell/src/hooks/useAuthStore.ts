/**
 * Experience UI — Auth Context Store (Zustand)
 *
 * Aligned with Health OS Identity Doctrine (§5–§6):
 *   "Sign in as a person; practice as a provider only under activated Provider ID."
 *
 * Provider tree position: QueryClient > [AuthProvider] > Facility > Workspace > Shift > Router
 * Persistence keys: exp:auth_user, exp:expires_at,
 *   exp:provider_id (sessionStorage). OAuth tokens never enter the browser.
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
  /** Health ID explicitly linked to this account (may differ from `id` during migration). */
  healthId?: string;
  /** Identity assurance level for this session. */
  assuranceLevel: "UNVERIFIED" | "TEMPORARY" | "VERIFIED";
  /** Linked IDs discovered post-login from the BFF identity endpoint. */
  linkedIds?: {
    providerId?: string;
    staffId?: string;
    caregiverId?: string;
  };
  /** True when the user has opted into professional mode for this session. */
  providerActivated: boolean;
  /** How the user authenticated this session (drives post-login shell resolution). */
  loginMethod?: "health_id" | "provider_id" | "email" | "biometric";
}

interface AuthState {
  user: AuthUser | null;
  token: string | null;
  refreshToken: string | null;
  expiresAt: string | null;
  isAuthenticated: boolean;
  /**
   * True once the provider tree has tried to restore a session from sessionStorage.
   *
   * The store starts logged out and is filled by an effect in `StoreHydrator`, which sits
   * *above* the route guard — so on a full page load the guard's first pass sees an empty
   * store. Before this flag existed that pass could not tell "signed out" from "not read
   * yet", and every deep link into a guarded route was redirected away before the session
   * arrived. Consumers that act on the absence of a session must wait for this.
   */
  sessionRestoreAttempted: boolean;
  /** Called by the hydrator once, on every path including failure, so nothing waits forever. */
  markSessionRestoreAttempted: () => void;
  setAuth: (user: AuthUser, token: string, refreshToken?: string | null, expiresAt?: string | null) => void;
  hydrateSession: (user: AuthUser, refreshToken?: string | null, expiresAt?: string | null) => void;
  setTokens: (token: string, refreshToken?: string | null, expiresAt?: string | null) => void;
  clearAuth: () => void;
  hasRole: (role: string) => boolean;
  /** Health OS §6: true when Provider ID is activated for this session. */
  hasActiveProvider: () => boolean;
  /** Health OS §6: activate Provider ID for regulated professional work. Sets providerActivated + actorType. */
  activateProvider: (providerId?: string) => void;
  /** Health OS §6: deactivate Provider ID (return to person-only/citizen context). */
  deactivateProvider: () => void;
  /** Update linked IDs after post-login identity resolution. */
  setLinkedIds: (linkedIds: { providerId?: string; staffId?: string; caregiverId?: string }) => void;
  isTokenExpired: () => boolean;
}

function deriveDeactivatedActorType(user: AuthUser): AuthUser["actorType"] {
  if (user.linkedIds?.caregiverId || user.roles.includes("CAREGIVER") || user.roles.includes("CARE_PARTNER")) {
    return "CAREGIVER";
  }

  if (user.roles.includes("SYSTEM_ADMIN") || user.roles.includes("DEVELOPER")) {
    return "SYSTEM";
  }

  const operatorRoles = [
    "FACILITY_ADMIN",
    "FINANCE",
    "SUPPORT_AGENT",
    "PUBLIC_HEALTH_OFFICER",
    "ENV_HEALTH",
    "CHW",
    "PHARMACIST",
  ];
  if (user.staffId || user.linkedIds?.staffId || user.roles.some((role) => operatorRoles.includes(role))) {
    return "OPERATOR";
  }

  return "CITIZEN";
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  token: null,
  refreshToken: null,
  expiresAt: null,
  isAuthenticated: false,
  sessionRestoreAttempted: false,

  markSessionRestoreAttempted: () => set({ sessionRestoreAttempted: true }),

  setAuth: (user, _token, _refreshToken, expiresAt) => {
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
      // Legacy callers may still pass a token while their journey is being migrated.
      // Deliberately discard it: browser OAuth material is prohibited; the BFF-held
      // opaque session cookie is the only authentication credential.
      sessionStorage.removeItem("exp:auth_token");
      sessionStorage.setItem("exp:auth_user", JSON.stringify(user));
      if (expiresAt) sessionStorage.setItem("exp:expires_at", expiresAt);
      else sessionStorage.removeItem("exp:expires_at");
      // Health OS §6: persist Provider ID for header injection (api-client.ts reads exp:provider_id)
      if (user.providerId) {
        sessionStorage.setItem("exp:provider_id", user.providerId);
      } else {
        sessionStorage.removeItem("exp:provider_id");
      }
      // Health OS §5: persist assurance level for header injection (api-client.ts reads exp:assurance_level)
      if (user.assuranceLevel) {
        sessionStorage.setItem("exp:assurance_level", user.assuranceLevel);
      }
    }
    set({ user, token: null, refreshToken: null, expiresAt: expiresAt ?? null, isAuthenticated: true });
  },

  hydrateSession: (user, _refreshToken, expiresAt) => {
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
      sessionStorage.setItem("exp:auth_user", JSON.stringify(user));
      if (expiresAt) sessionStorage.setItem("exp:expires_at", expiresAt);
      else sessionStorage.removeItem("exp:expires_at");
      if (user.providerId) {
        sessionStorage.setItem("exp:provider_id", user.providerId);
      } else {
        sessionStorage.removeItem("exp:provider_id");
      }
      if (user.assuranceLevel) {
        sessionStorage.setItem("exp:assurance_level", user.assuranceLevel);
      }
    }
    set({ user, token: null, refreshToken: null, expiresAt: expiresAt ?? null, isAuthenticated: true });
  },

  setTokens: (_token, _refreshToken, expiresAt) => {
    if (typeof window !== "undefined") {
      if (expiresAt) sessionStorage.setItem("exp:expires_at", expiresAt);
      else sessionStorage.removeItem("exp:expires_at");
    }
    // Retained temporarily for source compatibility only. Never retain OAuth tokens.
    set({ token: null, refreshToken: null, expiresAt: expiresAt ?? get().expiresAt });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:auth_token");
      sessionStorage.removeItem("exp:auth_user");
      sessionStorage.removeItem("exp:expires_at");
      sessionStorage.removeItem("exp:consent_accepted");
      sessionStorage.removeItem("exp:consent_version");
    }
    resetExperienceContinuity();
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:home-tab");
    }
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
    if (user?.providerActivated && user?.providerId) return true;
    if (typeof window !== "undefined") {
      return !!sessionStorage.getItem("exp:provider_id");
    }
    return false;
  },

  activateProvider: (providerId?: string) => {
    const { user } = get();
    if (!user) return;
    // Use the passed providerId, or fall back to linkedIds.providerId, or existing providerId
    const resolvedProviderId = providerId ?? user.linkedIds?.providerId ?? user.providerId;
    if (!resolvedProviderId) return;
    const updated: AuthUser = {
      ...user,
      providerId: resolvedProviderId,
      providerActivated: true,
      actorType: "PROVIDER",
    };
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:provider_id", resolvedProviderId);
      sessionStorage.setItem("exp:auth_user", JSON.stringify(updated));
    }
    set({ user: updated });
  },

  deactivateProvider: () => {
    const { user } = get();
    if (!user) return;
    const updated: AuthUser = {
      ...user,
      providerId: undefined,
      providerActivated: false,
      actorType: deriveDeactivatedActorType(user),
    };
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:provider_id");
      sessionStorage.removeItem("exp:provider_display");
      sessionStorage.removeItem("exp:provider_cadre");
      sessionStorage.setItem("exp:auth_user", JSON.stringify(updated));
    }
    set({ user: updated });
  },

  setLinkedIds: (linkedIds) => {
    const { user } = get();
    if (!user) return;
    const updated: AuthUser = { ...user, linkedIds };
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:auth_user", JSON.stringify(updated));
    }
    set({ user: updated });
  },
}));
