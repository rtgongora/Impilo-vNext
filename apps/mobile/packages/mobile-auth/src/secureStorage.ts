/**
 * Secure Storage — Encrypted key-value persistence for tokens and session data.
 *
 * On mobile (React Native), this backs onto:
 *   - iOS: Keychain Services (via expo-secure-store or react-native-keychain)
 *   - Android: Android Keystore + EncryptedSharedPreferences
 *
 * This module provides the abstraction layer. The platform adapter is injected
 * at initialization time via configureSecureStorage().
 */

export interface SecureStorageAdapter {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
  clear(): Promise<void>;
}

/**
 * In-memory secure storage for environments without native Keychain/Keystore.
 * Used during testing and SSR. Data is lost on process restart.
 */
class MemorySecureStorage implements SecureStorageAdapter {
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

let activeAdapter: SecureStorageAdapter = new MemorySecureStorage();

/**
 * Configure the secure storage backend.
 * Call this once at app startup with the platform-specific adapter.
 *
 * Example (React Native with expo-secure-store):
 * ```ts
 * import * as ExpoSecureStore from 'expo-secure-store';
 * configureSecureStorage({
 *   getItem: (key) => ExpoSecureStore.getItemAsync(key),
 *   setItem: (key, value) => ExpoSecureStore.setItemAsync(key, value),
 *   removeItem: (key) => ExpoSecureStore.deleteItemAsync(key),
 *   clear: async () => { /* clear known keys *\/ },
 * });
 * ```
 */
export function configureSecureStorage(adapter: SecureStorageAdapter): void {
  activeAdapter = adapter;
}

export function getSecureStorage(): SecureStorageAdapter {
  return activeAdapter;
}

// Storage keys used by the auth module
export const STORAGE_KEYS = {
  ACCESS_TOKEN: "impilo:auth:access_token",
  REFRESH_TOKEN: "impilo:auth:refresh_token",
  TOKEN_EXPIRY: "impilo:auth:token_expiry",
  SESSION_DATA: "impilo:auth:session_data",
  TENANT_ID: "impilo:auth:tenant_id",
  BIOMETRIC_ENABLED: "impilo:auth:biometric_enabled",
} as const;
