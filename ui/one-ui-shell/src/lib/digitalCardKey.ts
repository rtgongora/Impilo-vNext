"use client";

/**
 * Digital SMART-card device signing key — persistence, signing, and offline counter.
 *
 * The citizen's digital card is a browser wallet-pass: a NON-EXTRACTABLE P-256 ECDSA key
 * is generated in this browser and kept in IndexedDB, so the private key never leaves the
 * device. Its SPKI public key is enrolled with VITO (the VIRTUAL card credential) and cached
 * by mushe so offline / biometric pay can verify signatures.
 *
 * Function 3 (pay) works because this module signs the canonical payload
 * {amount,counter,nonce,currency,authMethod} EXACTLY as mushe's OfflineTransactionService
 * verifies it (services/mushe-wallet-service/.../card/OfflineTransactionService.java):
 *   SHA256withECDSA over the exact payload bytes, DER-encoded signature, base64.
 *
 * WebCrypto's ECDSA sign emits the raw IEEE-P1363 (r‖s) signature (64 bytes for P-256); the
 * JDK verifier expects DER (SEQUENCE{INTEGER r, INTEGER s}). {@link rawEcdsaToDer} bridges the
 * two — this is the load-bearing correctness detail for pay.
 *
 * Note: the mushe card response does not expose lastOfflineCounter, so the monotonic offline
 * counter is tracked device-side ({@link nextOfflineCounter}), seeded from Date.now() so it is
 * always strictly greater than any counter mushe previously accepted for this card.
 */

const DB_NAME = "impilo-digital-card";
const DB_VERSION = 1;
const STORE = "keys";
const KEYPAIR_ID = "device-signing-key";
const COUNTER_ID = "offline-counter";

export interface StoredKeyPair {
  publicKey: CryptoKey;
  privateKey: CryptoKey;
  publicKeyPem: string;
}

export interface CanonicalPayloadInput {
  amount: string;
  counter: number;
  nonce: string;
  currency: string;
  authMethod: string;
}

// ── IndexedDB (structured-clone stores CryptoKey; non-extractable keys persist) ──────

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") {
      reject(new Error("IndexedDB is unavailable in this environment"));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("IndexedDB open failed"));
  });
}

function idbPut(key: string, value: unknown): Promise<void> {
  return openDb().then(
    (db) =>
      new Promise<void>((resolve, reject) => {
        const tx = db.transaction(STORE, "readwrite");
        tx.objectStore(STORE).put(value, key);
        tx.oncomplete = () => {
          db.close();
          resolve();
        };
        tx.onerror = () => {
          db.close();
          reject(tx.error ?? new Error("IndexedDB write failed"));
        };
      }),
  );
}

function idbGet<T>(key: string): Promise<T | null> {
  return openDb().then(
    (db) =>
      new Promise<T | null>((resolve, reject) => {
        const tx = db.transaction(STORE, "readonly");
        const r = tx.objectStore(STORE).get(key);
        r.onsuccess = () => {
          db.close();
          resolve((r.result as T) ?? null);
        };
        r.onerror = () => {
          db.close();
          reject(r.error ?? new Error("IndexedDB read failed"));
        };
      }),
  );
}

function idbDelete(key: string): Promise<void> {
  return openDb().then(
    (db) =>
      new Promise<void>((resolve, reject) => {
        const tx = db.transaction(STORE, "readwrite");
        tx.objectStore(STORE).delete(key);
        tx.oncomplete = () => {
          db.close();
          resolve();
        };
        tx.onerror = () => {
          db.close();
          reject(tx.error ?? new Error("IndexedDB delete failed"));
        };
      }),
  );
}

// ── Byte helpers ─────────────────────────────────────────────────────────────────

function base64(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s);
}

async function spkiPem(publicKey: CryptoKey): Promise<string> {
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", publicKey));
  const b64 = base64(spki);
  const lines = b64.match(/.{1,64}/g) ?? [b64];
  return `-----BEGIN PUBLIC KEY-----\n${lines.join("\n")}\n-----END PUBLIC KEY-----`;
}

/**
 * Encode one big-endian integer as a DER INTEGER (0x02 len value): strip leading zero bytes,
 * and prepend 0x00 when the high bit is set so it is not read as negative.
 */
function derInteger(bytes: Uint8Array): Uint8Array {
  let i = 0;
  while (i < bytes.length - 1 && bytes[i] === 0) i++;
  let v = bytes.slice(i);
  if ((v[0] & 0x80) !== 0) {
    const padded = new Uint8Array(v.length + 1);
    padded[0] = 0;
    padded.set(v, 1);
    v = padded;
  }
  const out = new Uint8Array(2 + v.length);
  out[0] = 0x02;
  out[1] = v.length;
  out.set(v, 2);
  return out;
}

/**
 * Convert a WebCrypto raw ECDSA signature (IEEE-P1363, r‖s, even length) into the DER
 * SEQUENCE{INTEGER r, INTEGER s} that the JDK's SHA256withECDSA verifier expects.
 * For P-256 the total DER length is <128 bytes, so a single-byte SEQUENCE length is always valid.
 */
export function rawEcdsaToDer(raw: Uint8Array): Uint8Array {
  if (raw.length === 0 || raw.length % 2 !== 0) {
    throw new Error("Invalid raw ECDSA signature length: " + raw.length);
  }
  const half = raw.length / 2;
  const r = derInteger(raw.slice(0, half));
  const s = derInteger(raw.slice(half));
  const body = new Uint8Array(r.length + s.length);
  body.set(r, 0);
  body.set(s, r.length);
  const seq = new Uint8Array(2 + body.length);
  seq[0] = 0x30;
  seq[1] = body.length;
  seq.set(body, 2);
  return seq;
}

// ── Canonical payload ────────────────────────────────────────────────────────────

/**
 * Build the exact canonical JSON string that is both signed and sent as the `payload` field.
 * mushe re-reads fields from these exact bytes and verifies the signature over them, so the
 * string produced here must be the same string handed to {@link signCardPayload} and POSTed.
 */
export function buildCanonicalPayload(input: CanonicalPayloadInput): string {
  return JSON.stringify({
    amount: input.amount,
    counter: input.counter,
    nonce: input.nonce,
    currency: input.currency,
    authMethod: input.authMethod,
  });
}

// ── Key lifecycle ────────────────────────────────────────────────────────────────

/**
 * Generate a NON-EXTRACTABLE P-256 ECDSA key pair, persist the pair in IndexedDB (so the
 * private key survives for later signing but can never be exported), and return the SPKI PEM
 * public key for VITO enrollment.
 */
export async function generateAndStoreKey(): Promise<string> {
  const pair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    false, // non-extractable: the private key can never leave this browser
    ["sign", "verify"],
  );
  const publicKeyPem = await spkiPem(pair.publicKey); // public keys stay exportable even when the pair is non-extractable
  const stored: StoredKeyPair = {
    publicKey: pair.publicKey,
    privateKey: pair.privateKey,
    publicKeyPem,
  };
  await idbPut(KEYPAIR_ID, stored);
  return publicKeyPem;
}

/** The stored device key pair, or null if this browser has not enrolled a key. */
export async function getStoredKeyPair(): Promise<StoredKeyPair | null> {
  try {
    return await idbGet<StoredKeyPair>(KEYPAIR_ID);
  } catch {
    return null;
  }
}

/** Whether this browser holds an enrolled device signing key. */
export async function hasEnrolledKey(): Promise<boolean> {
  const stored = await getStoredKeyPair();
  return stored != null && !!stored.privateKey;
}

/** Forget the device key and reset the offline counter (e.g. on report-lost / sign-out). */
export async function clearKey(): Promise<void> {
  await idbDelete(KEYPAIR_ID).catch(() => undefined);
  await idbDelete(COUNTER_ID).catch(() => undefined);
}

// ── Signing ──────────────────────────────────────────────────────────────────────

/**
 * Sign the canonical JSON with a P-256 private key and return the base64 DER signature that
 * mushe verifies. Exported (independent of storage) so the exact sign→DER→base64 format is
 * unit-testable against a JDK-equivalent verifier.
 */
export async function signWithKey(privateKey: CryptoKey, canonicalJson: string): Promise<string> {
  const raw = new Uint8Array(
    await crypto.subtle.sign(
      { name: "ECDSA", hash: "SHA-256" },
      privateKey,
      new TextEncoder().encode(canonicalJson),
    ),
  );
  return base64(rawEcdsaToDer(raw));
}

/**
 * Sign a canonical card payload with this browser's stored device key. Returns the base64 DER
 * ECDSA-SHA256 signature. Throws if no key is enrolled on this browser.
 */
export async function signCardPayload(canonicalJson: string): Promise<string> {
  const stored = await getStoredKeyPair();
  if (!stored || !stored.privateKey) {
    throw new Error("No enrolled device key on this browser — enroll this device first.");
  }
  return signWithKey(stored.privateKey, canonicalJson);
}

// ── Monotonic offline counter ──────────────────────────────────────────────────────

/**
 * Next strictly-monotonic offline counter for this device. Seeded from Date.now() (always
 * greater than any earlier counter mushe accepted for this card) and persisted so rapid
 * successive payments still strictly increase. `serverLastCounter` (when a future card
 * response exposes it) raises the floor as an extra guard.
 */
export async function nextOfflineCounter(serverLastCounter = 0): Promise<number> {
  let last = 0;
  try {
    const stored = await idbGet<number>(COUNTER_ID);
    if (typeof stored === "number") last = stored;
  } catch {
    /* best-effort — fall through to Date.now() floor */
  }
  const next = Math.max(Date.now(), last + 1, (serverLastCounter || 0) + 1);
  try {
    await idbPut(COUNTER_ID, next);
  } catch {
    /* best-effort persistence; Date.now() still guarantees forward progress */
  }
  return next;
}
