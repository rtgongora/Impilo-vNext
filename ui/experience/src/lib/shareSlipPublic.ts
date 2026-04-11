/**
 * Public share-slip service base URL (no auth). Used for claim/verify flows.
 * Example: https://share-slip.example.com or http://localhost:PORT
 */
export function normalizeShareSlipBaseUrl(raw: string | undefined | null): string {
  if (raw == null || !raw.trim()) return "";
  return raw.trim().replace(/\/$/, "");
}

export function getShareSlipPublicBaseUrl(): string {
  const raw = typeof process !== "undefined" ? process.env.NEXT_PUBLIC_SHARE_SLIP_PUBLIC_URL : undefined;
  return normalizeShareSlipBaseUrl(raw);
}
