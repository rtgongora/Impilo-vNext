/**
 * Two error envelopes exist on this BFF and both reach the shell.
 *
 * The honest-degradation register's shape is **flat** — `{ error: "<snake_case_code>", message,
 * meta }` with no `data` key — and is what `EmergencyHonesty` and the structured-history and
 * clinical-timeline controllers emit. Spring's `ResponseStatusException`, which the 31 ED routes
 * still raise, produces a **nested** shape instead: `{ error: { code, message, request_id,
 * correlation_id } }`.
 *
 * A panel that only understands one of them renders "something went wrong" for the other, losing
 * the upstream's own message — which in this lane is frequently the clinically important part
 * ("critical result not acted on", "decline_reason is required"). This module reads both and
 * returns one shape, so a caller never has to know which controller answered.
 *
 * It deliberately does not invent a message when neither envelope carries one. A generic
 * "Request failed" is exactly the false reassurance the register forbids; callers get `null` and
 * must say what they could not read.
 */

export interface BffError {
  /** Machine code, e.g. `emergency_alerts_unavailable` or `HTTP_ERROR`. */
  code: string;
  /** The upstream's own message. Never synthesised. */
  message: string | null;
  /** Present on the flat register envelope. */
  meta?: Record<string, unknown>;
  correlationId?: string;
  requestId?: string;
  /** True when the envelope explicitly declared the read degraded rather than empty. */
  degraded: boolean;
}

interface FlatEnvelope {
  error: string;
  message?: unknown;
  meta?: Record<string, unknown>;
}

interface NestedEnvelope {
  error: {
    code?: unknown;
    message?: unknown;
    request_id?: unknown;
    correlation_id?: unknown;
  };
}

function asString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * Read either envelope. Returns `null` when the body is not an error envelope at all, so a caller
 * can distinguish "the server said why" from "the server said nothing we can quote".
 */
export function parseBffError(body: unknown): BffError | null {
  if (!isRecord(body) || !("error" in body)) return null;

  const raw = (body as { error: unknown }).error;

  if (typeof raw === "string") {
    const flat = body as unknown as FlatEnvelope;
    const meta = isRecord(flat.meta) ? flat.meta : undefined;
    return {
      code: raw,
      message: asString(flat.message),
      meta,
      correlationId: asString(meta?.correlation_id) ?? undefined,
      requestId: asString(meta?.request_id) ?? undefined,
      degraded: meta?.degraded === true,
    };
  }

  if (isRecord(raw)) {
    const nested = (body as unknown as NestedEnvelope).error;
    return {
      code: asString(nested.code) ?? "HTTP_ERROR",
      message: asString(nested.message),
      correlationId: asString(nested.correlation_id) ?? undefined,
      requestId: asString(nested.request_id) ?? undefined,
      degraded: false,
    };
  }

  return null;
}

/**
 * The sentence to put in front of a clinician. Prefers the upstream's own words; when there are
 * none, states the limit rather than inventing a reassurance.
 *
 * @param subject what could not be read, e.g. "emergency alerts" — used only in the fallback
 */
export function bffErrorMessage(body: unknown, subject: string): string {
  const parsed = parseBffError(body);
  if (parsed?.message) return parsed.message;
  const sentenceCase = subject.charAt(0).toUpperCase() + subject.slice(1);
  return `${sentenceCase} could not be retrieved. Do not treat this as an absence of ${subject}.`;
}

/**
 * A composite surface returns `{ items, failures }` and a 200 even when a source is blind, so the
 * board renders and each dead tile states its own limit. This reads the `failures` list.
 */
export interface BffSourceFailure {
  source: string;
  code: string;
  message: string;
}

export function parseBffFailures(body: unknown): BffSourceFailure[] {
  if (!isRecord(body) || !Array.isArray(body.failures)) return [];
  return body.failures.flatMap((entry) => {
    if (!isRecord(entry)) return [];
    const source = asString(entry.source);
    if (!source) return [];
    return [
      {
        source,
        code: asString(entry.error) ?? "unknown_error",
        message: asString(entry.message) ?? `${source} could not be retrieved.`,
      },
    ];
  });
}

/** True when the response explicitly declared itself degraded — never inferred from an empty list. */
export function isDegradedResponse(body: unknown): boolean {
  if (!isRecord(body)) return false;
  if (parseBffFailures(body).length > 0) return true;
  const meta = isRecord(body.meta) ? body.meta : undefined;
  return meta?.degraded === true;
}
