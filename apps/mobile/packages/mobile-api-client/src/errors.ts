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

  static fromResponse(status: number, correlationId: string, body?: unknown): ApiError {
    const bodyObj = body as Record<string, unknown> | null;
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
