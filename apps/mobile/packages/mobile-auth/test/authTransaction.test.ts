import { describe, expect, it } from "vitest";
import { consumeAuthTransaction, type AuthTransaction } from "../src/authStore";
import { AuthError } from "../src/keycloakClient";
import { STORAGE_KEYS, type SecureStorageAdapter } from "../src/secureStorage";

class TestStorage implements SecureStorageAdapter {
  values = new Map<string, string>();
  async getItem(key: string) { return this.values.get(key) ?? null; }
  async setItem(key: string, value: string) { this.values.set(key, value); }
  async removeItem(key: string) { this.values.delete(key); }
  async clear() { this.values.clear(); }
}

const transaction = (createdAt: number): AuthTransaction => ({
  state: "state-1",
  nonce: "nonce-1",
  codeVerifier: "verifier-1",
  redirectUri: "impilo-citizen://auth/callback",
  createdAt,
});

describe("mobile OAuth transaction", () => {
  it("restores from secure storage and consumes exactly once", async () => {
    const storage = new TestStorage();
    await storage.setItem(STORAGE_KEYS.AUTH_TRANSACTION, JSON.stringify(transaction(1_000)));

    const restored = await consumeAuthTransaction(storage, "state-1", undefined, 2_000);
    expect(restored.codeVerifier).toBe("verifier-1");
    await expect(consumeAuthTransaction(storage, "state-1", undefined, 2_001))
      .rejects.toMatchObject<AuthError>({ code: "CALLBACK_REPLAYED" });
  });

  it("does not consume a legitimate transaction on forged state", async () => {
    const storage = new TestStorage();
    await storage.setItem(STORAGE_KEYS.AUTH_TRANSACTION, JSON.stringify(transaction(1_000)));

    await expect(consumeAuthTransaction(storage, "attacker-state", undefined, 2_000))
      .rejects.toMatchObject<AuthError>({ code: "STATE_MISMATCH" });
    expect(await storage.getItem(STORAGE_KEYS.AUTH_TRANSACTION)).not.toBeNull();
  });

  it("rejects and removes an expired transaction", async () => {
    const storage = new TestStorage();
    await storage.setItem(STORAGE_KEYS.AUTH_TRANSACTION, JSON.stringify(transaction(1_000)));

    await expect(consumeAuthTransaction(storage, "state-1", undefined, 302_000))
      .rejects.toMatchObject<AuthError>({ code: "TRANSACTION_EXPIRED" });
    expect(await storage.getItem(STORAGE_KEYS.AUTH_TRANSACTION)).toBeNull();
  });
});
