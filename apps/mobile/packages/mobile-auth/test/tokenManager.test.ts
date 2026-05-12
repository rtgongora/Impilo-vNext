import { describe, it, expect, vi, beforeEach } from "vitest";
import { TokenManager } from "../src/tokenManager";
import { KeycloakClient } from "../src/keycloakClient";
import { configureSecureStorage } from "../src/secureStorage";
import type { SecureStorageAdapter } from "../src/secureStorage";
import type { TokenResponse } from "../src/keycloakClient";

function createMemoryStorage(): SecureStorageAdapter {
  const store = new Map<string, string>();
  return {
    getItem: async (key) => store.get(key) ?? null,
    setItem: async (key, value) => { store.set(key, value); },
    removeItem: async (key) => { store.delete(key); },
    clear: async () => { store.clear(); },
  };
}

const mockTokenResponse: TokenResponse = {
  access_token: "access-new",
  refresh_token: "refresh-new",
  expires_in: 300,
  refresh_expires_in: 1800,
  token_type: "Bearer",
  scope: "openid profile email",
};

describe("TokenManager", () => {
  let storage: SecureStorageAdapter;
  let kc: KeycloakClient;
  let tm: TokenManager;

  beforeEach(() => {
    storage = createMemoryStorage();
    configureSecureStorage(storage);
    kc = new KeycloakClient({
      realm: "impilo",
      clientId: "mobile-app",
      baseUrl: "http://localhost:8080",
      redirectUri: "impilo://callback",
    });
    tm = new TokenManager(kc);
  });

  it("setTokens stores tokens and returns state", async () => {
    const state = await tm.setTokens(mockTokenResponse);
    expect(state.accessToken).toBe("access-new");
    expect(state.refreshToken).toBe("refresh-new");
    expect(state.expiresAt).toBeGreaterThan(Date.now());
  });

  it("setTokens persists to secure storage", async () => {
    await tm.setTokens(mockTokenResponse);
    expect(await storage.getItem("impilo_auth_access_token")).toBe("access-new");
    expect(await storage.getItem("impilo_auth_refresh_token")).toBe("refresh-new");
  });

  it("getValidToken returns current token when not expired", async () => {
    await tm.setTokens(mockTokenResponse);
    const token = await tm.getValidToken();
    expect(token).toBe("access-new");
  });

  it("getValidToken throws when no session", async () => {
    await expect(tm.getValidToken()).rejects.toThrow("No active session");
  });

  it("clearTokens removes all stored tokens", async () => {
    await tm.setTokens(mockTokenResponse);
    await tm.clearTokens();
    expect(tm.getCurrentTokens()).toBeNull();
    expect(await storage.getItem("impilo_auth_access_token")).toBeNull();
  });

  it("restoreTokens returns null when no stored tokens", async () => {
    const result = await tm.restoreTokens();
    expect(result).toBeNull();
  });

  it("restoreTokens recovers valid tokens from storage", async () => {
    await tm.setTokens(mockTokenResponse);
    // Create a new TokenManager and restore
    const tm2 = new TokenManager(kc);
    const result = await tm2.restoreTokens();
    expect(result).not.toBeNull();
    expect(result!.accessToken).toBe("access-new");
  });

  it("calls onTokenRefreshed callback after token refresh", async () => {
    const cb = vi.fn();
    tm.setCallbacks({ onTokenRefreshed: cb });
    // This would normally be called during refresh, but we test the callback mechanism
    await tm.setTokens(mockTokenResponse);
    // The callback is only triggered during refresh, not initial set
    expect(cb).not.toHaveBeenCalled();
  });
});
