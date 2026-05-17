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
import { configureOfflineStorage, MemoryStorageAdapter, openExpoSqliteOfflineAdapter } from "@impilo/mobile-offline";
import { configureSecureStorage, type SecureStorageAdapter } from "@impilo/mobile-auth";

import * as Linking from "expo-linking";

const extra = Constants.expoConfig?.extra ?? {};

/**
 * Resolved variant — set by EAS profile env (`EXPO_PUBLIC_APP_VARIANT`) and
 * mirrored into `extra.appVariant` by `app.config.ts`. The local default for
 * `expo start` development is "dev".
 */
const APP_VARIANT: "dev" | "preview" | "staging" | "production" =
  (extra.appVariant as never) ?? (process.env.EXPO_PUBLIC_APP_VARIANT as never) ?? "dev";

const ENV = {
  APP_VARIANT,
  KEYCLOAK_URL: extra.keycloakUrl ?? process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "http://192.168.100.211:8080",
  KEYCLOAK_REALM: extra.keycloakRealm ?? process.env.EXPO_PUBLIC_KEYCLOAK_REALM ?? "impilo",
  KEYCLOAK_CLIENT_ID: extra.keycloakClientId ?? process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? "provider-app",
  API_BASE_URL: extra.apiBaseUrl ?? process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://192.168.100.211:8000",
  APP_NAME: extra.appName ?? process.env.EXPO_PUBLIC_APP_NAME ?? "Impilo Provider Dev",
  // Always derive the redirect URI from Linking so it matches the URL that
  // WebBrowser.openAuthSessionAsync intercepts — Expo Go uses exp://…/--/auth/callback
  // and standalone builds use impilo-provider://auth/callback.  A hardcoded value
  // (e.g. "impilo.provider://callback") would both differ from what Linking.createURL
  // generates at runtime AND use the wrong path, causing the "invalid address" error.
  REDIRECT_URI: Linking.createURL("auth/callback"),
};

/**
 * Production safety guard — a release build must never resolve to localhost,
 * a private LAN IP, or http://. We throw at module load so EAS catches the
 * misconfiguration before the binary is published.
 *
 * (Dev/preview builds are allowed to point anywhere, since they are explicitly
 * gated by the EAS distribution profile.)
 */
function assertSafeProductionUrls(): void {
  if (APP_VARIANT !== "production") return;
  const offenders: string[] = [];
  for (const [name, url] of Object.entries({
    API_BASE_URL: ENV.API_BASE_URL,
    KEYCLOAK_URL: ENV.KEYCLOAK_URL,
  })) {
    if (!/^https:\/\//i.test(url)) offenders.push(`${name} must use https:// (got "${url}")`);
    if (/localhost|127\.0\.0\.1|10\.0\.2\.2|192\.168\.|10\./.test(url)) {
      offenders.push(`${name} must not point to a local/LAN host (got "${url}")`);
    }
  }
  if (offenders.length > 0) {
    throw new Error(
      `Refusing to start: production build has unsafe configuration:\n  - ${offenders.join("\n  - ")}`
    );
  }
}

assertSafeProductionUrls();

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
    baseUrl: ENV.KEYCLOAK_URL,
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
}

/**
 * Durable offline store (Expo SQLite). Falls back to in-memory adapter if SQLite is unavailable (tests / web).
 */
export async function initializeOfflinePersistence(): Promise<void> {
  try {
    const adapter = await openExpoSqliteOfflineAdapter("impilo_provider_offline.db");
    configureOfflineStorage(adapter);
  } catch {
    configureOfflineStorage(new MemoryStorageAdapter());
  }
}

export { ENV };
