/**
 * Decode JWT payload (middle segment) without signature verification.
 * Used after Keycloak token exchange over TLS for display and trust headers.
 *
 * Tries `base64url` first, then RFC 4648-style base64 with padding (some stacks
 * emit payloads that decode more reliably via the padded path).
 */
export function decodeJwtPayload<T extends Record<string, unknown>>(
  token: string
): T | null {
  const parts = token.split(".");
  if (parts.length < 2) return null;
  const segment = parts[1];
  if (!segment) return null;

  const attempts: Array<() => string> = [
    () => Buffer.from(segment, "base64url").toString("utf8"),
    () => {
      const b64 = segment.replace(/-/g, "+").replace(/_/g, "/");
      const pad = (4 - (b64.length % 4)) % 4;
      return Buffer.from(b64 + "=".repeat(pad), "base64").toString("utf8");
    },
  ];

  for (const toUtf8 of attempts) {
    try {
      return JSON.parse(toUtf8()) as T;
    } catch {
      /* try next */
    }
  }
  return null;
}

/** Read JWT exp claim as Unix seconds; accepts number or numeric string. */
export function readJwtExpSeconds(payload: Record<string, unknown>): number | null {
  const e = payload.exp;
  if (typeof e === "number" && Number.isFinite(e)) return e;
  if (typeof e === "string" && /^\d+$/.test(e)) {
    const n = Number(e);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}
