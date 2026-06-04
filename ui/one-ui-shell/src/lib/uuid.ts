/**
 * Insecure-context-safe UUID generation.
 *
 * `crypto.randomUUID()` is only available in a secure context (HTTPS or
 * localhost). The preview is served over plain HTTP on a bare IP
 * (http://41.57.127.235), where `crypto.randomUUID` is `undefined` — calling it
 * throws and breaks every request that needs a request id / Idempotency-Key
 * (including login). `crypto.getRandomValues()` IS available in insecure
 * contexts, so we use it to build an RFC 4122 v4 UUID, with a final Math.random
 * fallback for environments without Web Crypto at all.
 */
export function randomUUID(): string {
  const c: Crypto | undefined =
    typeof globalThis !== "undefined" ? (globalThis.crypto as Crypto | undefined) : undefined;

  if (c && typeof c.randomUUID === "function") {
    try {
      return c.randomUUID();
    } catch {
      // fall through to getRandomValues
    }
  }

  if (c && typeof c.getRandomValues === "function") {
    const bytes = new Uint8Array(16);
    c.getRandomValues(bytes);
    // Per RFC 4122 §4.4: set version (4) and variant (10xx) bits.
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex: string[] = [];
    for (let i = 0; i < 256; i++) hex.push((i + 0x100).toString(16).slice(1));
    return (
      hex[bytes[0]] + hex[bytes[1]] + hex[bytes[2]] + hex[bytes[3]] + "-" +
      hex[bytes[4]] + hex[bytes[5]] + "-" +
      hex[bytes[6]] + hex[bytes[7]] + "-" +
      hex[bytes[8]] + hex[bytes[9]] + "-" +
      hex[bytes[10]] + hex[bytes[11]] + hex[bytes[12]] + hex[bytes[13]] + hex[bytes[14]] + hex[bytes[15]]
    );
  }

  // Last-resort fallback (non-cryptographic). Adequate for correlation ids.
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    const v = ch === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
