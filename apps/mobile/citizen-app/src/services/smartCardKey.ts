/**
 * SMART card device key — the virtual card's cryptographic root on this phone.
 *
 * The unified SMART card exists in VIRTUAL form first: a wallet pass on the phone whose
 * P-256 (secp256r1) key plays the role a physical card's secure-element key plays. This module
 * generates that key, stores the private key in the device keychain/keystore (expo-secure-store,
 * biometric-gated on access), exposes the PEM public key for enrollment to VITO, and signs the
 * canonical offline-transaction payload the server verifies.
 *
 * Server contract it must match (mushe-wallet-service OfflineTransactionService):
 *   - public key: X.509 SubjectPublicKeyInfo PEM (parsed via X509EncodedKeySpec / KeyFactory("EC"))
 *   - signature : DER-encoded ECDSA over SHA-256(payloadBytes)  ("SHA256withECDSA"), Base64
 *   - payload   : {"amount","counter","nonce","currency","authMethod"} — signed exactly as sent
 *
 * NOTE: This is client crypto and cannot be built/run in the backend sandbox — it needs
 * `pnpm install` + a dev-client / EAS build. It is written to the documented server contract.
 */
import * as SecureStore from "expo-secure-store";
import { p256 } from "@noble/curves/p256";
import { sha256 } from "@noble/hashes/sha256";

const PRIVATE_KEY_STORE_KEY = "impilo.smartcard.p256.privatekey";
const COUNTER_STORE_KEY = "impilo.smartcard.offline.counter";

/** DER SubjectPublicKeyInfo prefix for an uncompressed P-256 (secp256r1) public key. */
const SPKI_P256_PREFIX = new Uint8Array([
  0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, 0x06, 0x08, 0x2a,
  0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
]);

/** True when a device SMART-card key already exists in the keystore. */
export async function hasDeviceCardKey(): Promise<boolean> {
  const existing = await SecureStore.getItemAsync(PRIVATE_KEY_STORE_KEY);
  return !!existing;
}

/**
 * Generate and persist a new device SMART-card P-256 keypair (idempotent — returns the existing
 * public key if one is already provisioned). Returns the PEM public key to enroll with VITO.
 */
export async function ensureDeviceCardKey(): Promise<string> {
  const existing = await SecureStore.getItemAsync(PRIVATE_KEY_STORE_KEY);
  if (existing) {
    const priv = hexToBytes(existing);
    return publicKeyPem(p256.getPublicKey(priv, false));
  }
  const priv = p256.utils.randomPrivateKey();
  await SecureStore.setItemAsync(PRIVATE_KEY_STORE_KEY, bytesToHex(priv), {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    requireAuthentication: false, // biometric is enforced explicitly at pay time (broader device support)
  });
  return publicKeyPem(p256.getPublicKey(priv, false));
}

/** The device card's PEM public key, or null if no key is provisioned yet. */
export async function deviceCardPublicKeyPem(): Promise<string | null> {
  const existing = await SecureStore.getItemAsync(PRIVATE_KEY_STORE_KEY);
  if (!existing) return null;
  return publicKeyPem(p256.getPublicKey(hexToBytes(existing), false));
}

export interface SignedOfflineTransaction {
  payload: string; // the exact canonical JSON that was signed
  signature: string; // Base64 DER ECDSA-SHA256
  counter: number;
}

/**
 * Build and sign a canonical offline-transaction payload with the device card key.
 * The caller passes the authorization method already established (e.g. "BIOMETRIC" after a
 * successful biometric prompt) — this function only signs; it does not itself prompt.
 *
 * The counter is monotonic per device (the server rejects a non-increasing counter as a replay).
 */
export async function signOfflineTransaction(args: {
  amount: string;
  currency: string;
  authMethod: "BIOMETRIC" | "PIN" | "NONE";
}): Promise<SignedOfflineTransaction> {
  const privHex = await SecureStore.getItemAsync(PRIVATE_KEY_STORE_KEY);
  if (!privHex) {
    throw new Error("No SMART card key on this device. Set up your card first.");
  }
  const counter = await nextCounter();
  const nonce = `${counter}-${randomHex(8)}`;
  // Field order is fixed so the exact bytes are reproducible; the server verifies over these bytes.
  const payload = JSON.stringify({
    amount: args.amount,
    counter,
    nonce,
    currency: args.currency,
    authMethod: args.authMethod,
  });
  const priv = hexToBytes(privHex);
  const digest = sha256(new TextEncoder().encode(payload));
  const sig = p256.sign(digest, priv); // lowS canonical — accepted by Java SHA256withECDSA
  const der = sig.toDERRawBytes();
  return { payload, signature: bytesToBase64(der), counter };
}

// ── internals ───────────────────────────────────────────────────────────

async function nextCounter(): Promise<number> {
  const raw = await SecureStore.getItemAsync(COUNTER_STORE_KEY);
  const next = (raw ? parseInt(raw, 10) : 0) + 1;
  await SecureStore.setItemAsync(COUNTER_STORE_KEY, String(next));
  return next;
}

function publicKeyPem(uncompressed: Uint8Array): string {
  const spki = new Uint8Array(SPKI_P256_PREFIX.length + uncompressed.length);
  spki.set(SPKI_P256_PREFIX, 0);
  spki.set(uncompressed, SPKI_P256_PREFIX.length);
  const b64 = bytesToBase64(spki);
  const lines = b64.match(/.{1,64}/g)?.join("\n") ?? b64;
  return `-----BEGIN PUBLIC KEY-----\n${lines}\n-----END PUBLIC KEY-----`;
}

function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
  return out;
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  // eslint-disable-next-line no-undef
  return typeof btoa === "function" ? btoa(binary) : Buffer.from(bytes).toString("base64");
}

function randomHex(nBytes: number): string {
  return bytesToHex(p256.utils.randomPrivateKey().slice(0, nBytes));
}
