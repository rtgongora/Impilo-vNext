/**
 * Produce a short, non-sensitive detail string for token-endpoint failures.
 * Never forwards raw upstream bodies (avoids accidental token leakage).
 */
const JWT_LIKE =
  /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_.-]{10,}\.[A-Za-z0-9_.-]{10,}\b/g;

function redactJwtLike(s: string): string {
  return s.replace(JWT_LIKE, "[redacted]");
}

export function safeKeycloakTokenErrorDetail(
  status: number,
  bodyText: string
): string {
  const capped = redactJwtLike(bodyText.slice(0, 4096));
  try {
    const j = JSON.parse(capped) as {
      error?: unknown;
      error_description?: unknown;
    };
    const code = typeof j.error === "string" ? j.error.trim() : "";
    const descRaw =
      typeof j.error_description === "string"
        ? j.error_description.trim()
        : "";
    const desc = redactJwtLike(descRaw).slice(0, 240);
    if (code && desc) return `${code}: ${desc}`;
    if (code) return code.slice(0, 120);
    if (desc) return desc;
  } catch {
    /* not JSON */
  }
  return `token_exchange_failed_http_${status}`;
}
