/**
 * API Error types — Structured errors from the API client.
 *
 * All API errors carry the correlation ID for end-to-end tracing.
 */

import type { ApiErrorDetail } from "@impilo/mobile-trust";

export class ApiError extends Error {
  public readonly code: string;
  public readonly status: number;
  public readonly correlationId: string;
  public readonly details?: Record<string, unknown>;

  constructor(params: {
    code: string;
    message: string;
    status: number;
    correlationId: string;
    details?: Record<string, unknown>;
  }) {
    super(params.message);
    this.name = "ApiError";
    this.code = params.code;
    this.status = params.status;
    this.correlationId = params.correlationId;
    this.details = params.details;
  }

  static fromEnvelope(error: ApiErrorDetail, correlationId: string): ApiError {
    return new ApiError({
      code: error.code,
      message: error.message,
      status: error.status,
      correlationId,
      details: error.details,
    });
  }

  /**
   * Build an {@link ApiError} from a raw HTTP response body.
   *
   * Supports five envelope shapes (in order of preference):
   *
   *   1. Experience-BFF v1.2 nested error — `{ error: { code, message }, meta }`.
   *      Emitted by every controller that uses the standard `ok / error`
   *      envelope (e.g. `WalletController` returning
   *      `{ "error": { "code": "WALLET_UPSTREAM_UNAVAILABLE", ... } }` on a 503,
   *      or `ConfidentialCommunityPostnatalController`'s 502
   *      `{ "error": { "code": "PCT_UNAVAILABLE", ... } }`).
   *      Preserves the stable upstream `code` so callers can branch on it.
   *
   *   2. Nested `data` error envelope — `{ data: { code, message }, meta }`.
   *      What a proxying controller emits when it forwards an upstream service's
   *      refusal body byte-for-byte instead of re-wrapping it (e.g. pct-service's
   *      `unprocessable()` helper, forwarded as-is by
   *      `ConfidentialCommunityPostnatalController#forward`, producing
   *      `{ "data": { "code": "PNC_SETTING_REQUIRED", "message": "..." } }` on a 422).
   *      Same shape as tier 1, keyed under `data` instead of `error` because the
   *      body never passed back through the BFF's own envelope-builder.
   *
   *   3. Flat string error with a sibling message — `{ error: "some_code", message: "..." }`.
   *      Distinct from tier 1: here `error` is the code itself (a string), not an
   *      object containing one. Emitted by `MaternalNearMissController` — e.g.
   *      `{ "error": "near_miss_unavailable", "message": "The near-miss criteria
   *      could not be evaluated..." }` on a 502, or `{ "error":
   *      "unrecognised_form_answer", "message": "...", "unrecognised_answers": [...] }`
   *      on a 422. Any other top-level string/array/object fields on the body
   *      (`unrecognised_answers`, `accepted_codes`, `criteria_left_blank`, …) are
   *      captured into `details` so a caller can still read them — losing
   *      "these three answers were unreadable" down to a generic HTTP_422 is how a
   *      clinician resubmits blind.
   *
   *   4. Flat legacy shape — `{ errorCode, errorMessage }`.
   *      Kept for back-compat with older error responses.
   *
   *   5. Unparseable / empty body — defaults to
   *      `code = "HTTP_${status}"` with a generic message.
   *
   * The classic v1.1 `ApiEnvelope<T>` envelope (`{ success: false, error: ... }`)
   * is still handled upstream in `client.ts` via {@link ApiError.fromEnvelope};
   * this helper only runs when the response is not that shape.
   */
  static fromResponse(status: number, correlationId: string, body?: unknown): ApiError {
    const bodyObj = body as Record<string, unknown> | null;

    // Tier 1: BFF v1.2 nested error envelope (`{ error: { code, message } }`).
    const nestedError = bodyObj?.error;
    const fromNestedObject = (nested: unknown): ApiError | null => {
      if (!nested || typeof nested !== "object") return null;
      const nestedRec = nested as Record<string, unknown>;
      const nestedCode = nestedRec.code;
      const nestedMessage = nestedRec.message;
      if (typeof nestedCode !== "string" && typeof nestedMessage !== "string") return null;
      // A sibling `details` object wins where present; any other sibling field (e.g.
      // pct-service's `existing_pregnancy_episode_id`/`reconciliation_task` on a 409, or a
      // `clinical_note` on a 502) is captured too, on the same principle as tier 3's `rest`
      // spread below — a caller that needs one of these to reconcile a conflict or show the
      // real clinical note must not lose it to a generic `code`/`message` pair.
      const { code: _code, message: _message, details: explicitDetails, ...rest } = nestedRec;
      const merged =
        typeof explicitDetails === "object" && explicitDetails !== null
          ? { ...rest, ...(explicitDetails as Record<string, unknown>) }
          : rest;
      return new ApiError({
        code: typeof nestedCode === "string" ? nestedCode : `HTTP_${status}`,
        message:
          typeof nestedMessage === "string"
            ? nestedMessage
            : `Request failed with status ${status}`,
        status,
        correlationId,
        details: Object.keys(merged).length > 0 ? merged : undefined,
      });
    };
    const tier1 = fromNestedObject(nestedError);
    if (tier1) return tier1;

    // Tier 2: a proxying controller forwarded an upstream refusal body verbatim, keyed
    // under `data` rather than `error` (e.g. pct-service's `{ data: { code, message } }`).
    const tier2 = fromNestedObject(bodyObj?.data);
    if (tier2) return tier2;

    // Tier 3: flat string error code with a sibling message (`MaternalNearMissController`).
    if (typeof nestedError === "string" && typeof bodyObj?.message === "string") {
      const { error: _error, message: _message, meta: _meta, ...rest } = bodyObj;
      return new ApiError({
        code: nestedError,
        message: bodyObj.message,
        status,
        correlationId,
        details: Object.keys(rest).length > 0 ? rest : undefined,
      });
    }

    // Tier 4: flat legacy shape.
    return new ApiError({
      code: (bodyObj?.errorCode as string) ?? `HTTP_${status}`,
      message: (bodyObj?.errorMessage as string) ?? `Request failed with status ${status}`,
      status,
      correlationId,
    });
  }
}

export class NetworkError extends Error {
  public readonly correlationId: string;

  constructor(message: string, correlationId: string) {
    super(message);
    this.name = "NetworkError";
    this.correlationId = correlationId;
  }
}

export class TimeoutError extends Error {
  public readonly correlationId: string;
  public readonly timeoutMs: number;

  constructor(timeoutMs: number, correlationId: string) {
    super(`Request timed out after ${timeoutMs}ms`);
    this.name = "TimeoutError";
    this.correlationId = correlationId;
    this.timeoutMs = timeoutMs;
  }
}

export class StepUpRequiredError extends Error {
  public readonly methods: string[];
  public readonly correlationId: string;

  constructor(methods: string[], correlationId: string) {
    super("Step-up authentication required");
    this.name = "StepUpRequiredError";
    this.methods = methods;
    this.correlationId = correlationId;
  }
}

/**
 * Determines if an error is retryable (5xx server error or network error).
 */
export function isRetryable(error: unknown): boolean {
  if (error instanceof NetworkError) return true;
  if (error instanceof TimeoutError) return true;
  if (error instanceof ApiError && error.status >= 500) return true;
  return false;
}
