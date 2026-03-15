/**
 * Citizen App Configuration
 *
 * Centralizes Keycloak, API base URL, and feature toggles.
 * Values are loaded from environment variables at build time.
 */

import { configureAuth } from "@impilo/mobile-auth";
import { configureApiClient } from "@impilo/mobile-api-client";
import { configureOfflineStorage, MemoryStorageAdapter } from "@impilo/mobile-offline";
import { configureSecureStorage, type SecureStorageAdapter } from "@impilo/mobile-auth";

const ENV = {
  KEYCLOAK_URL: process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "https://auth.impilo.gov.zw",
  KEYCLOAK_REALM: process.env.EXPO_PUBLIC_KEYCLOAK_REALM ?? "impilo",
  KEYCLOAK_CLIENT_ID: process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? "citizen-app",
  API_BASE_URL: process.env.EXPO_PUBLIC_API_BASE_URL ?? "https://api.impilo.gov.zw",
  REDIRECT_URI: process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo.citizen://callback",
};

class InMemorySecureStorage implements SecureStorageAdapter {
  private store = new Map<string, string>();

  async getItem(key: string): Promise<string | null> {
    return this.store.get(key) ?? null;
  }

  async setItem(key: string, value: string): Promise<void> {
    this.store.set(key, value);
  }

  async removeItem(key: string): Promise<void> {
    this.store.delete(key);
  }

  async clear(): Promise<void> {
    this.store.clear();
  }
}

export function initializeApp(): void {
  configureSecureStorage(new InMemorySecureStorage());

  configureAuth({
    url: ENV.KEYCLOAK_URL,
    realm: ENV.KEYCLOAK_REALM,
    clientId: ENV.KEYCLOAK_CLIENT_ID,
    redirectUri: ENV.REDIRECT_URI,
  });

  configureApiClient({
    baseUrl: ENV.API_BASE_URL,
    defaultTimeoutMs: 30_000,
    maxRetries: 3,
    retryBaseDelayMs: 1000,
  });

  configureOfflineStorage(new MemoryStorageAdapter());
}

export { ENV };
