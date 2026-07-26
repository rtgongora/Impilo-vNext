/**
 * Return hint — a deliberately minimal, masked "remember me on this device" cookie
 * that lets the signed-out landing greet a returning user ("Welcome back, {name}").
 *
 * Privacy stance (approved design):
 *  - Stores ONLY a masked first name + a timestamp. No IDs, no tokens, no health
 *    data, no workspace/patient/facility detail. Never enough to identify or act.
 *  - Opt-in only ("Remember me on this device", default off).
 *  - Durable (30 days) so it survives browser close — this is the whole point;
 *    it is therefore NOT cleared by routine session-expiry cleanup, only by an
 *    explicit logout or "Use another account".
 *  - SameSite=Lax, path=/. Not HttpOnly by necessity (the client reads it to render
 *    the greeting), which is acceptable precisely because the payload is non-sensitive.
 */

const COOKIE = "impilo_return_hint";
const MAX_AGE_SECONDS = 60 * 60 * 24 * 30; // 30 days

export interface ReturnHint {
  name: string;
  ts: string;
}

/** Write the masked hint from a display name. First name only, capped, opt-in gated by caller. */
export function writeReturnHint(displayName: string | undefined | null): void {
  if (typeof document === "undefined") return;
  const first = (displayName ?? "").trim().split(/\s+/)[0]?.slice(0, 40) ?? "";
  if (!first) return;
  const payload = { n: first, t: new Date().toISOString() };
  const value = encodeURIComponent(JSON.stringify(payload));
  document.cookie = `${COOKIE}=${value};path=/;max-age=${MAX_AGE_SECONDS};SameSite=Lax`;
}

export function readReturnHint(): ReturnHint | null {
  if (typeof document === "undefined") return null;
  const entry = document.cookie.split("; ").find((c) => c.startsWith(`${COOKIE}=`));
  if (!entry) return null;
  try {
    const parsed = JSON.parse(decodeURIComponent(entry.slice(COOKIE.length + 1)));
    if (!parsed || typeof parsed.n !== "string" || !parsed.n) return null;
    return { name: parsed.n, ts: typeof parsed.t === "string" ? parsed.t : "" };
  } catch {
    return null;
  }
}

export function clearReturnHint(): void {
  if (typeof document === "undefined") return;
  document.cookie = `${COOKIE}=;path=/;max-age=0;SameSite=Lax`;
}

/** "2 days ago" style relative label; empty string if unknown/invalid. */
export function relativeSince(iso: string): string {
  if (!iso) return "";
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const days = Math.floor((Date.now() - then) / 86_400_000);
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days < 30) return `${days} days ago`;
  return "a while ago";
}
