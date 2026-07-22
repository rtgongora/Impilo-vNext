/**
 * Detects the TM-B6 optimistic-concurrency conflict (409 STALE_WRITE) surfaced by PCT when a
 * referral was edited concurrently. apiClient throws `{ status, error: { code, message } }`, and
 * the BFF A0 passthrough forwards the upstream code verbatim — so both the status and the code
 * are reliable signals. The experience layer uses this to prompt a reload-and-retry rather than
 * showing a generic failure or silently losing the edit.
 */
export function isStaleWrite(error: unknown): boolean {
  if (!error || typeof error !== "object") return false;
  const e = error as { status?: number; error?: { code?: string }; code?: string };
  const code = e.error?.code ?? e.code;
  return code === "STALE_WRITE" || (e.status === 409 && code === "STALE_WRITE");
}
