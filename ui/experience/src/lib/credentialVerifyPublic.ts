/**
 * Public credential verification service base URL (no auth).
 * Calls GET {base}/v1/public/verify/{token} (credential-verification-service).
 *
 * Configure via NEXT_PUBLIC_CREDENTIAL_VERIFY_PUBLIC_URL (e.g. http://localhost:8094 or gateway origin).
 */

export function normalizeCredentialVerifyBaseUrl(raw: string | undefined | null): string {
  if (raw == null || !raw.trim()) return "";
  return raw.trim().replace(/\/$/, "");
}

export function getCredentialVerifyPublicBaseUrl(): string {
  const raw = typeof process !== "undefined" ? process.env.NEXT_PUBLIC_CREDENTIAL_VERIFY_PUBLIC_URL : undefined;
  return normalizeCredentialVerifyBaseUrl(raw);
}

/** UI shape after normalizing API response (ApiResponse wrapper or flat JSON). */
export interface CredentialVerificationDisplay {
  status: string;
  credentialType: string;
  subjectName: string;
  title: string;
  issuedBy: string;
  validFrom: string;
  validTo: string | null;
  verifiedAt: string;
}

function readString(v: unknown): string {
  return v == null ? "" : String(v);
}

/**
 * Maps credential-verification-service VerificationResult (JSON) to display fields.
 * Accepts either ApiResponse shape `{ data: { ... } }` or a flat result object.
 */
export function parseCredentialVerificationResponse(json: unknown): CredentialVerificationDisplay | null {
  if (json == null || typeof json !== "object") return null;
  const root = json as Record<string, unknown>;
  const payload =
    root.data != null && typeof root.data === "object"
      ? (root.data as Record<string, unknown>)
      : root;

  if (payload.status == null && root.status == null) return null;

  const status = readString(payload.status ?? root.status);
  const validToRaw = payload.validTo ?? root.validTo;
  return {
    status,
    credentialType: readString(payload.credentialType),
    subjectName: readString(payload.subjectName),
    title: readString(payload.title),
    issuedBy: readString(payload.issuedBy),
    validFrom: readString(payload.validFrom),
    validTo: validToRaw == null || validToRaw === "" ? null : readString(validToRaw),
    verifiedAt: readString(payload.verifiedAt),
  };
}

export function credentialVerifyUrlForToken(base: string, token: string): string {
  const b = normalizeCredentialVerifyBaseUrl(base);
  const t = encodeURIComponent(token.trim());
  return `${b}/v1/public/verify/${t}`;
}
