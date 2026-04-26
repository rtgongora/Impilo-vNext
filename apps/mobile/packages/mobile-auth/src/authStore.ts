/**
 * Auth Store — Zustand store for authentication state.
 *
 * This is the primary interface for UI components to access auth state.
 * Wraps KeycloakClient + TokenManager into a reactive Zustand store.
 */

import { createStore } from "zustand/vanilla";
import type { ActorType, PurposeOfUse, SessionContext } from "@impilo/mobile-trust";
import { KeycloakClient, generateCodeVerifier, generateCodeChallenge, AuthError } from "./keycloakClient";
import type { KeycloakConfig, UserInfo } from "./keycloakClient";
import { TokenManager } from "./tokenManager";
import type { TokenState } from "./tokenManager";
import { getSecureStorage, STORAGE_KEYS } from "./secureStorage";
import { generateId } from "@impilo/mobile-trust";

export interface AuthState {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: UserInfo | null;
  session: SessionContext | null;
  error: AuthError | null;

  // Actions
  initialize: () => Promise<void>;
  login: () => Promise<{ authUrl: string; state: string }>;
  handleCallback: (code: string, state: string, expectedState: string) => Promise<void>;
  logout: () => Promise<void>;
  getValidToken: () => Promise<string>;
  setTenantContext: (tenantId: string, podId: string) => Promise<void>;
  setFacility: (facilityId: string, facilityName: string) => void;
  setWorkspace: (workspaceId: string, workspaceName: string) => void;
  setShift: (shiftId: string) => void;
  setPurposeOfUse: (purpose: PurposeOfUse) => void;
  setDeviceFingerprint: (fp: string) => void;

  // Health OS §6: Provider ID activation
  /** Activate a Provider ID for regulated professional work. */
  activateProvider: (providerId: string, displayName: string) => void;
  /** Deactivate Provider ID (return to person-only context). */
  deactivateProvider: () => void;
  /** True when Provider ID is activated for this session. */
  hasActiveProvider: () => boolean;
}

let keycloakClient: KeycloakClient | null = null;
let tokenManager: TokenManager | null = null;
let pendingCodeVerifier: string | null = null;

export function configureAuth(config: KeycloakConfig): void {
  keycloakClient = new KeycloakClient(config);
  tokenManager = new TokenManager(keycloakClient);
}

function requireKeycloak(): KeycloakClient {
  if (!keycloakClient) throw new AuthError("NOT_CONFIGURED", "Auth not configured. Call configureAuth() first.");
  return keycloakClient;
}

function requireTokenManager(): TokenManager {
  if (!tokenManager) throw new AuthError("NOT_CONFIGURED", "Auth not configured. Call configureAuth() first.");
  return tokenManager;
}

export const authStore = createStore<AuthState>((set, get) => ({
  isAuthenticated: false,
  isLoading: true,
  user: null,
  session: null,
  error: null,

  initialize: async () => {
    const tm = requireTokenManager();
    const kc = requireKeycloak();
    set({ isLoading: true, error: null });

    try {
      tm.setCallbacks({
        onTokenRefreshed: (tokens: TokenState) => {
          const current = get().session;
          if (current) {
            set({
              session: {
                ...current,
                accessToken: tokens.accessToken,
                refreshToken: tokens.refreshToken,
                expiresAt: tokens.expiresAt,
              },
            });
          }
        },
        onTokenExpired: () => {
          set({ isAuthenticated: false, user: null, session: null });
        },
      });

      const tokens = await tm.restoreTokens();
      if (!tokens) {
        set({ isLoading: false, isAuthenticated: false });
        return;
      }

      const userInfo = await kc.getUserInfo(tokens.accessToken);
      const storage = getSecureStorage();
      const storedSession = await storage.getItem(STORAGE_KEYS.SESSION_DATA);
      const sessionData = storedSession ? JSON.parse(storedSession) : {};
      const tenantId = (await storage.getItem(STORAGE_KEYS.TENANT_ID)) ?? "tenant-moh-zw";

      const session: SessionContext = {
        tenantId,
        podId: sessionData.podId ?? "national-spine",
        actorId: userInfo.sub,
        actorType: resolveActorType(userInfo),
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        expiresAt: tokens.expiresAt,
        providerId: sessionData.providerId,
        facilityId: sessionData.facilityId,
        facilityName: sessionData.facilityName,
        workspaceId: sessionData.workspaceId,
        workspaceName: sessionData.workspaceName,
        shiftId: sessionData.shiftId,
        purposeOfUse: sessionData.purposeOfUse ?? "TREATMENT",
        deviceFingerprint: sessionData.deviceFingerprint,
      };

      set({ isAuthenticated: true, isLoading: false, user: userInfo, session });
    } catch (error) {
      set({
        isLoading: false,
        isAuthenticated: false,
        error: error instanceof AuthError ? error : new AuthError("INIT_FAILED", String(error)),
      });
    }
  },

  login: async () => {
    const kc = requireKeycloak();
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = await generateCodeChallenge(codeVerifier);
    const state = generateId();
    pendingCodeVerifier = codeVerifier;
    const authUrl = kc.buildAuthorizationUrl(codeChallenge, state);
    return { authUrl, state };
  },

  handleCallback: async (code: string, state: string, expectedState: string) => {
    if (state !== expectedState) {
      throw new AuthError("STATE_MISMATCH", "OAuth state parameter mismatch — possible CSRF attack");
    }
    if (!pendingCodeVerifier) {
      throw new AuthError("NO_VERIFIER", "No pending PKCE code verifier");
    }

    const kc = requireKeycloak();
    const tm = requireTokenManager();

    set({ isLoading: true, error: null });

    try {
      const tokenResponse = await kc.exchangeCode(code, pendingCodeVerifier);
      pendingCodeVerifier = null;

      const tokens = await tm.setTokens(tokenResponse);
      const userInfo = await kc.getUserInfo(tokens.accessToken);
      const storage = getSecureStorage();
      const tenantId = (await storage.getItem(STORAGE_KEYS.TENANT_ID)) ?? "tenant-moh-zw";

      const session: SessionContext = {
        tenantId,
        podId: "national-spine",
        actorId: userInfo.sub,
        actorType: resolveActorType(userInfo),
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        expiresAt: tokens.expiresAt,
        purposeOfUse: "TREATMENT",
      };

      await storage.setItem(STORAGE_KEYS.SESSION_DATA, JSON.stringify({
        podId: session.podId,
        purposeOfUse: session.purposeOfUse,
      }));

      set({ isAuthenticated: true, isLoading: false, user: userInfo, session, error: null });
    } catch (error) {
      set({
        isLoading: false,
        error: error instanceof AuthError ? error : new AuthError("CALLBACK_FAILED", String(error)),
      });
      throw error;
    }
  },

  logout: async () => {
    const tm = requireTokenManager();
    const kc = requireKeycloak();
    const tokens = tm.getCurrentTokens();

    // Revoke tokens server-side
    if (tokens) {
      await Promise.allSettled([
        kc.revokeToken(tokens.accessToken, "access_token"),
        kc.revokeToken(tokens.refreshToken, "refresh_token"),
      ]);
    }

    // Clear local state
    await tm.clearTokens();
    const storage = getSecureStorage();
    await storage.clear();

    set({ isAuthenticated: false, user: null, session: null, isLoading: false, error: null });
  },

  getValidToken: async () => {
    const tm = requireTokenManager();
    return tm.getValidToken();
  },

  setTenantContext: async (tenantId: string, podId: string) => {
    const storage = getSecureStorage();
    await storage.setItem(STORAGE_KEYS.TENANT_ID, tenantId);
    const current = get().session;
    if (current) {
      const updated = { ...current, tenantId, podId };
      set({ session: updated });
      await persistSessionData(updated);
    }
  },

  setFacility: (facilityId: string, facilityName: string) => {
    const current = get().session;
    if (current) {
      const updated = { ...current, facilityId, facilityName };
      set({ session: updated });
      persistSessionData(updated);
    }
  },

  setWorkspace: (workspaceId: string, workspaceName: string) => {
    const current = get().session;
    if (current) {
      const updated = { ...current, workspaceId, workspaceName };
      set({ session: updated });
      persistSessionData(updated);
    }
  },

  setShift: (shiftId: string) => {
    const current = get().session;
    if (current) {
      const updated = { ...current, shiftId };
      set({ session: updated });
      persistSessionData(updated);
    }
  },

  setPurposeOfUse: (purpose: PurposeOfUse) => {
    const current = get().session;
    if (current) {
      const updated = { ...current, purposeOfUse: purpose };
      set({ session: updated });
      persistSessionData(updated);
    }
  },

  setDeviceFingerprint: (fp: string) => {
    const current = get().session;
    if (current) {
      const updated = { ...current, deviceFingerprint: fp };
      set({ session: updated });
      persistSessionData(updated);
    }
  },

  // Health OS §6: "Sign in as a person; practice as a provider only under activated Provider ID."
  activateProvider: (providerId: string, _displayName: string) => {
    const current = get().session;
    if (current) {
      const updated = { ...current, providerId };
      set({ session: updated });
      persistSessionData(updated);
    }
  },

  deactivateProvider: () => {
    const current = get().session;
    if (current) {
      const { providerId: _, ...rest } = current as SessionContext & { providerId?: string };
      set({ session: rest as SessionContext });
      persistSessionData(rest as SessionContext);
    }
  },

  hasActiveProvider: () => {
    const session = get().session;
    return !!(session as SessionContext & { providerId?: string })?.providerId;
  },
}));

function resolveActorType(userInfo: UserInfo): ActorType {
  const roles = userInfo.realm_access?.roles ?? [];
  if (roles.includes("CLINICIAN") || roles.includes("NURSE") || roles.includes("PHARMACIST")) return "PROVIDER";
  if (roles.includes("SYSTEM_ADMIN") || roles.includes("FACILITY_ADMIN") || roles.includes("FINANCE")) return "OPERATOR";
  return "CITIZEN";
}

async function persistSessionData(session: SessionContext): Promise<void> {
  const storage = getSecureStorage();
  await storage.setItem(STORAGE_KEYS.SESSION_DATA, JSON.stringify({
    podId: session.podId,
    facilityId: session.facilityId,
    facilityName: session.facilityName,
    workspaceId: session.workspaceId,
    workspaceName: session.workspaceName,
    shiftId: session.shiftId,
    providerId: (session as SessionContext & { providerId?: string }).providerId,
    purposeOfUse: session.purposeOfUse,
    deviceFingerprint: session.deviceFingerprint,
  }));
}
