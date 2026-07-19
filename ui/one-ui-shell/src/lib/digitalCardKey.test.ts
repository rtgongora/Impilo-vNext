import { describe, it, expect, beforeAll } from "vitest";
import { webcrypto, createPublicKey, createVerify } from "node:crypto";
import {
  rawEcdsaToDer,
  buildCanonicalPayload,
  signWithKey,
} from "./digitalCardKey";

// Ensure the module's global `crypto.subtle` is the real WebCrypto (jsdom omits subtle).
beforeAll(() => {
  const g = globalThis as unknown as { crypto?: Crypto };
  if (!g.crypto || !("subtle" in g.crypto)) {
    Object.defineProperty(globalThis, "crypto", {
      value: webcrypto,
      configurable: true,
    });
  }
});

describe("buildCanonicalPayload", () => {
  it("emits a deterministic canonical JSON string in the mushe field order", () => {
    const json = buildCanonicalPayload({
      amount: "12.50",
      counter: 42,
      nonce: "n-1",
      currency: "USD",
      authMethod: "PIN",
    });
    expect(json).toBe(
      '{"amount":"12.50","counter":42,"nonce":"n-1","currency":"USD","authMethod":"PIN"}',
    );
  });
});

describe("rawEcdsaToDer", () => {
  it("wraps r and s as a DER SEQUENCE of two INTEGERs", () => {
    // r has its high bit set (needs a 0x00 pad); s has leading zeros (all stripped to one byte).
    const r = new Uint8Array(32).fill(0);
    r[0] = 0x80; // high bit set
    const s = new Uint8Array(32).fill(0);
    s[31] = 0x2a; // 31 leading zeros, then 0x2a
    const raw = new Uint8Array([...r, ...s]);

    const der = rawEcdsaToDer(raw);
    expect(der[0]).toBe(0x30); // SEQUENCE
    expect(der[1]).toBe(der.length - 2); // single-byte length holds full body

    // First INTEGER: 0x02, len 33 (32 + 0x00 pad), first value byte 0x00 then 0x80…
    expect(der[2]).toBe(0x02);
    expect(der[3]).toBe(33);
    expect(der[4]).toBe(0x00);
    expect(der[5]).toBe(0x80);

    // Second INTEGER begins after the first (2 header + 33 value = 35 bytes in).
    const sOffset = 2 + 2 + 33;
    expect(der[sOffset]).toBe(0x02);
    // s stripped of its leading zeros → a single value byte 0x2a.
    expect(der[sOffset + 1]).toBe(1);
    expect(der[der.length - 1]).toBe(0x2a);
  });

  it("rejects an odd-length raw signature", () => {
    expect(() => rawEcdsaToDer(new Uint8Array(31))).toThrow();
  });
});

describe("signWithKey (JDK-verifiable DER ECDSA-SHA256)", () => {
  it("produces a base64 DER signature a SHA256withECDSA verifier accepts", async () => {
    const pair = (await webcrypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign", "verify"],
    )) as CryptoKeyPair;

    const canonical = buildCanonicalPayload({
      amount: "5.00",
      counter: Date.now(),
      nonce: "nonce-abc",
      currency: "USD",
      authMethod: "BIOMETRIC",
    });

    const sigB64 = await signWithKey(pair.privateKey, canonical);
    expect(sigB64).toMatch(/^[A-Za-z0-9+/]+=*$/);

    // Verify exactly as mushe does: SHA256withECDSA over the payload bytes, DER signature.
    const spki = Buffer.from(await webcrypto.subtle.exportKey("spki", pair.publicKey));
    const publicKey = createPublicKey({ key: spki, format: "der", type: "spki" });
    const ok = createVerify("SHA256")
      .update(Buffer.from(canonical, "utf8"))
      .verify({ key: publicKey, dsaEncoding: "der" }, Buffer.from(sigB64, "base64"));

    expect(ok).toBe(true);
  });

  it("fails verification when the payload bytes differ from what was signed", async () => {
    const pair = (await webcrypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign", "verify"],
    )) as CryptoKeyPair;
    const signed = buildCanonicalPayload({
      amount: "5.00",
      counter: 1,
      nonce: "n",
      currency: "USD",
      authMethod: "PIN",
    });
    const sigB64 = await signWithKey(pair.privateKey, signed);
    const tampered = signed.replace('"5.00"', '"50.00"');

    const spki = Buffer.from(await webcrypto.subtle.exportKey("spki", pair.publicKey));
    const publicKey = createPublicKey({ key: spki, format: "der", type: "spki" });
    const ok = createVerify("SHA256")
      .update(Buffer.from(tampered, "utf8"))
      .verify({ key: publicKey, dsaEncoding: "der" }, Buffer.from(sigB64, "base64"));

    expect(ok).toBe(false);
  });
});
