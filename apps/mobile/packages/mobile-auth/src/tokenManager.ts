/**
 * Token Manager — Handles token lifecycle, automatic refresh, and secure persistence.
 *
 * Responsibilities:
 *   - Store tokens in secure storage (Keychain/Keystore)
 *   - Automatically refresh tokens before expiry
 *   - Queue requests during token refresh (prevent concurrent refresh)
 *   - Provide current valid access token
 */

import { KeycloakClient, AuthError } from "./keycloakClient";
import type { TokenResponse } from "./keycloakClient";
import { getSecureStorage, STORAGE_KEYS } from "./secureStorage";

/** Buffer in milliseconds before token expiry to trigger refresh (60 seconds). */
const REFRESH_BUFFER_MS = 60_000;

/** Minimum interval between refresh attempts (5 seconds). */
const MIN_REFRESH_INTERVAL_MS = 5_000;

export interface TokenState {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

export class TokenManager {
  private readonly keycloakClient: KeycloakClient;
  private currentTokens: TokenState | null = null;
  private refreshPromise: Promise<TokenState> | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private lastRefreshAttempt = 0;
  private onTokenRefreshed: ((tokens: TokenState) => void) | null = null;
  private onTokenExpired: (() => void) | null = null;

  constructor(keycloakClient: KeycloakClient) {
    this.keycloakClient = keycloakClient;
  }

  /**
   * Register callbacks for token lifecycle events.
   */
  setCallbacks(callbacks: {
    onTokenRefreshed?: (tokens: TokenState) => void;
    onTokenExpired?: () => void;
  }): void {
    this.onTokenRefreshed = callbacks.onTokenRefreshed ?? null;
    this.onTokenExpired = callbacks.onTokenExpired ?? null;
  }

  /**
   * Initialize token state from a fresh token response (after login).
   */
  async setTokens(response: TokenResponse): Promise<TokenState> {
    const tokens: TokenState = {
      accessToken: response.access_token,
      refreshToken: response.refresh_token,
      expiresAt: Date.now() + response.expires_in * 1000,
    };
    this.currentTokens = tokens;
    await this.persistTokens(tokens);
    this.scheduleRefresh(response.expires_in * 1000);
    return tokens;
  }

  /**
   * Restore tokens from secure storage on app start.
   * Returns null if no stored tokens or if they are expired and unrefreshable.
   */
  async restoreTokens(): Promise<TokenState | null> {
    const storage = getSecureStorage();
    const [accessToken, refreshToken, expiryStr] = await Promise.all([
      storage.getItem(STORAGE_KEYS.ACCESS_TOKEN),
      storage.getItem(STORAGE_KEYS.REFRESH_TOKEN),
      storage.getItem(STORAGE_KEYS.TOKEN_EXPIRY),
    ]);

    if (!accessToken || !refreshToken || !expiryStr) {
      return null;
    }

    const expiresAt = parseInt(expiryStr, 10);
    const tokens: TokenState = { accessToken, refreshToken, expiresAt };

    // If access token is still valid, use it
    if (Date.now() < expiresAt - REFRESH_BUFFER_MS) {
      this.currentTokens = tokens;
      this.scheduleRefresh(expiresAt - Date.now());
      return tokens;
    }

    // Access token expired or near expiry — try refresh
    try {
      return await this.refreshAccessToken(refreshToken);
    } catch {
      // Refresh token also expired — user must re-authenticate
      await this.clearTokens();
      return null;
    }
  }

  /**
   * Get a valid access token. If the current token is near expiry, refreshes first.
   * Multiple concurrent callers will share the same refresh promise.
   */
  async getValidToken(): Promise<string> {
    if (!this.currentTokens) {
      throw new AuthError("NO_SESSION", "No active session. User must authenticate.");
    }

    // Token still valid
    if (Date.now() < this.currentTokens.expiresAt - REFRESH_BUFFER_MS) {
      return this.currentTokens.accessToken;
    }

    // Token near expiry or expired — refresh
    const refreshed = await this.refreshAccessToken(this.currentTokens.refreshToken);
    return refreshed.accessToken;
  }

  /**
   * Clear all stored tokens (logout).
   */
  async clearTokens(): Promise<void> {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    this.currentTokens = null;
    this.refreshPromise = null;

    const storage = getSecureStorage();
    await Promise.all([
      storage.removeItem(STORAGE_KEYS.ACCESS_TOKEN),
      storage.removeItem(STORAGE_KEYS.REFRESH_TOKEN),
      storage.removeItem(STORAGE_KEYS.TOKEN_EXPIRY),
      storage.removeItem(STORAGE_KEYS.SESSION_DATA),
    ]);
  }

  /**
   * Returns current token state without triggering refresh.
   */
  getCurrentTokens(): TokenState | null {
    return this.currentTokens;
  }

  private async refreshAccessToken(refreshToken: string): Promise<TokenState> {
    // Prevent concurrent refresh — share the same promise
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    // Rate-limit refresh attempts
    const now = Date.now();
    if (now - this.lastRefreshAttempt < MIN_REFRESH_INTERVAL_MS) {
      throw new AuthError("REFRESH_RATE_LIMITED", "Token refresh rate limited");
    }
    this.lastRefreshAttempt = now;

    this.refreshPromise = this.executeRefresh(refreshToken);
    try {
      return await this.refreshPromise;
    } finally {
      this.refreshPromise = null;
    }
  }

  private async executeRefresh(refreshToken: string): Promise<TokenState> {
    try {
      const response = await this.keycloakClient.refreshToken(refreshToken);
      const tokens = await this.setTokens(response);
      this.onTokenRefreshed?.(tokens);
      return tokens;
    } catch (error) {
      this.onTokenExpired?.();
      throw error;
    }
  }

  private scheduleRefresh(expiresInMs: number): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }

    // Schedule refresh at 75% of token lifetime
    const refreshDelay = Math.max(expiresInMs * 0.75, MIN_REFRESH_INTERVAL_MS);
    this.refreshTimer = setTimeout(() => {
      if (this.currentTokens?.refreshToken) {
        this.refreshAccessToken(this.currentTokens.refreshToken).catch(() => {
          this.onTokenExpired?.();
        });
      }
    }, refreshDelay);
  }

  private async persistTokens(tokens: TokenState): Promise<void> {
    const storage = getSecureStorage();
    await Promise.all([
      storage.setItem(STORAGE_KEYS.ACCESS_TOKEN, tokens.accessToken),
      storage.setItem(STORAGE_KEYS.REFRESH_TOKEN, tokens.refreshToken),
      storage.setItem(STORAGE_KEYS.TOKEN_EXPIRY, String(tokens.expiresAt)),
    ]);
  }
}
