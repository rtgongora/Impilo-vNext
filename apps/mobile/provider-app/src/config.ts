/**
 * Provider App Configuration
 *
 * Centralizes Keycloak, API base URL, and feature toggles.
 * Uses expo-constants for build-time env vars and expo-secure-store for secure storage.
 */

import Constants from "expo-constants";
import * as SecureStore from "expo-secure-store";
import { configureAuth } from "@impilo/mobile-auth";
import { configureApiClient } from "@impilo/mobile-api-client";
import { configureOfflineStorage, MemoryStorageAdapter } from "@impilo/mobile-offline";
import { configureSecureStorage, type SecureStorageAdapter } from "@impilo/mobile-auth";

const extra = Constants.expoConfig?.extra ?? {};

const ENV = {
  KEYCLOAK_URL: extra.keycloakUrl ?? process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "https://auth.impilo.gov.zw",
  KEYCLOAK_REALM: extra.keycloakRealm ?? process.env.EXPO_PUBLIC_KEYCLOAK_REALM ?? "impilo",
  KEYCLOAK_CLIENT_ID: extra.keycloakClientId ?? process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? "provider-app",
  API_BASE_URL: extra.apiBaseUrl ?? process.env.EXPO_PUBLIC_API_BASE_URL ?? "https://api.impilo.gov.zw",
  REDIRECT_URI: extra.redirectUri ?? process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo.provider://callback",
};

/**
 * Secure storage adapter backed by expo-secure-store (Keychain on iOS, EncryptedSharedPreferences on Android).
 */
class ExpoSecureStorage implements SecureStorageAdapter {
  async getItem(key: string): Promise<string | null> {
    return SecureStore.getItemAsync(key);
  }

  async setItem(key: string, value: string): Promise<void> {
    await SecureStore.setItemAsync(key, value);
  }

  async removeItem(key: string): Promise<void> {
    await SecureStore.deleteItemAsync(key);
  }

  async clear(): Promise<void> {
    // expo-secure-store does not support bulk clear; managed keys are cleared individually by auth module
  }
}

export function initializeApp(): void {
  configureSecureStorage(new ExpoSecureStorage());

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
