/**
 * API Client Core — HTTP fetch wrapper with trust headers, retry, and envelope parsing.
 *
 * Features:
 *   - Automatic v1.1 trust header injection from session context
 *   - Idempotency-Key on POST/PUT/PATCH
 *   - ApiEnvelope<T> unwrapping with typed error handling
 *   - Exponential backoff retry on 5xx and network errors (max 3)
 *   - Request timeout with AbortController
 *   - Step-up auth detection
 *   - Correlation ID generation per request
 */

import {
  buildTrustHeaders,
  validateRequiredHeaders,
  generateId,
  type ApiEnvelope,
  type SessionContext,
  type AuthzVerdict,
} from "@impilo/mobile-trust";
import { authStore } from "@impilo/mobile-auth";
import { getApiClientConfig } from "./config";
import {
  ApiError,
  NetworkError,
  TimeoutError,
  StepUpRequiredError,
  isRetryable,
} from "./errors";

export interface RequestOptions {
  /** Override default timeout for this request. */
  timeoutMs?: number;
  /** Override purpose of use for this request. */
  purposeOfUse?: string;
  /** Custom correlation ID (auto-generated if omitted). */
  correlationId?: string;
  /** Disable retry for this request. */
  noRetry?: boolean;
  /** Custom headers to merge. */
  headers?: Record<string, string>;
  /** Signal for manual abort. */
  signal?: AbortSignal;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  correlationId: string;
  headers: Record<string, string>;
  obligations?: {
    maxScope?: string;
    maskFields?: string[];
    loggingLevel?: string;
  };
}

/**
 * Step-up event listener type.
 */
type StepUpListener = (challenge: {
  methods: string[];
  correlationId: string;
  originalRequest: { method: string; url: string; body?: unknown };
}) => void;

const stepUpListeners = new Set<StepUpListener>();

export function onStepUpRequired(listener: StepUpListener): () => void {
  stepUpListeners.add(listener);
  return () => stepUpListeners.delete(listener);
}

/**
 * Core request function with full v1.1 compliance.
 */
async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  options?: RequestOptions
): Promise<ApiResponse<T>> {
  const config = getApiClientConfig();
  const correlationId = options?.correlationId ?? generateId();
  const url = `${config.baseUrl}${path}`;
  const maxRetries = options?.noRetry ? 0 : config.maxRetries;

  let lastError: unknown;

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    if (attempt > 0) {
      // Exponential backoff: 1s, 2s, 4s
      const delay = config.retryBaseDelayMs * Math.pow(2, attempt - 1);
      await sleep(delay);
    }

    try {
      const result = await executeRequest<T>(method, url, path, body, correlationId, options);
      return result;
    } catch (error) {
      lastError = error;
      if (!isRetryable(error) || attempt === maxRetries) {
        throw error;
      }
    }
  }

  throw lastError;
}

async function executeRequest<T>(
  method: string,
  url: string,
  path: string,
  body: unknown,
  correlationId: string,
  options?: RequestOptions
): Promise<ApiResponse<T>> {
  const config = getApiClientConfig();
  const timeoutMs = options?.timeoutMs ?? config.defaultTimeoutMs;

  // Get session context for trust headers
  const state = authStore.getState();
  if (!state.session) {
    throw new ApiError({
      code: "NO_SESSION",
      message: "No active session. User must authenticate before making API calls.",
      status: 0,
      correlationId,
    });
  }

  // Ensure fresh token
  const validToken = await state.getValidToken();
  const session: SessionContext = { ...state.session, accessToken: validToken };
  if (options?.purposeOfUse) {
    (session as unknown as Record<string, unknown>).purposeOfUse = options.purposeOfUse;
  }

  // Build trust headers
  const headers = buildTrustHeaders(session, { method, correlationId, clientTimeoutMs: timeoutMs });
  validateRequiredHeaders(headers);

  // Merge custom headers
  if (options?.headers) {
    Object.assign(headers, options.headers);
  }

  // Setup timeout
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  const signal = options?.signal
    ? composeAbortSignals(options.signal, controller.signal)
    : controller.signal;

  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
      signal,
    });

    clearTimeout(timeoutId);

    // Parse response headers
    const responseHeaders: Record<string, string> = {};
    response.headers.forEach((value: string, key: string) => {
      responseHeaders[key] = value;
    });

    // Handle step-up required
    if (response.status === 401) {
      let authzBody: AuthzVerdict | null = null;
      try {
        authzBody = await response.json();
      } catch {
        // Non-JSON 401 response
      }

      if (authzBody?.decision === "STEP_UP_REQUIRED") {
        const methods = authzBody.stepUpMethods ?? [];
        stepUpListeners.forEach((l) =>
          l({ methods, correlationId, originalRequest: { method, url: path, body } })
        );
        throw new StepUpRequiredError(methods, correlationId);
      }

      throw ApiError.fromResponse(401, correlationId, authzBody);
    }

    // Handle error responses
    if (!response.ok) {
      let errorBody: unknown = null;
      try {
        errorBody = await response.json();
      } catch {
        // Non-JSON error
      }

      // Check if it's an ApiEnvelope error
      const envelope = errorBody as ApiEnvelope<unknown> | null;
      if (envelope && envelope.success === false && envelope.error) {
        throw ApiError.fromEnvelope(envelope.error, correlationId);
      }

      throw ApiError.fromResponse(response.status, correlationId, errorBody);
    }

    // Parse successful response
    const contentType = response.headers.get("content-type") ?? "";
    let data: T;

    if (contentType.includes("application/json")) {
      const json = await response.json();

      // Unwrap ApiEnvelope if present
      if (isApiEnvelope(json)) {
        if (!json.success && json.error) {
          throw ApiError.fromEnvelope(json.error, correlationId);
        }
        data = json.data as T;
      } else {
        data = json as T;
      }
    } else if (response.status === 204) {
      data = undefined as T;
    } else {
      data = (await response.text()) as unknown as T;
    }

    // Parse obligation headers
    const obligations = parseObligations(responseHeaders);

    return {
      data,
      status: response.status,
      correlationId,
      headers: responseHeaders,
      obligations,
    };
  } catch (error) {
    clearTimeout(timeoutId);

    if (error instanceof ApiError || error instanceof StepUpRequiredError) {
      throw error;
    }

    if (
      (typeof DOMException !== "undefined" && error instanceof DOMException && error.name === "AbortError") ||
      (error instanceof Error && error.name === "AbortError")
    ) {
      throw new TimeoutError(timeoutMs, correlationId);
    }

    throw new NetworkError(
      error instanceof Error ? error.message : "Network request failed",
      correlationId
    );
  }
}

function isApiEnvelope(json: unknown): json is ApiEnvelope<unknown> {
  return (
    typeof json === "object" &&
    json !== null &&
    "success" in json &&
    typeof (json as Record<string, unknown>).success === "boolean"
  );
}

function parseObligations(headers: Record<string, string>) {
  const maxScope = headers["x-max-scope"];
  const maskFieldsRaw = headers["x-mask-fields"];
  const loggingLevel = headers["x-logging-level"];

  if (!maxScope && !maskFieldsRaw && !loggingLevel) return undefined;

  return {
    maxScope: maxScope || undefined,
    maskFields: maskFieldsRaw ? maskFieldsRaw.split(",") : undefined,
    loggingLevel: loggingLevel || undefined,
  };
}

function composeAbortSignals(...signals: AbortSignal[]): AbortSignal {
  const controller = new AbortController();
  for (const signal of signals) {
    if (signal.aborted) {
      controller.abort(signal.reason);
      return controller.signal;
    }
    signal.addEventListener("abort", () => controller.abort(signal.reason), { once: true });
  }
  return controller.signal;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * The public API client — convenience methods for each HTTP verb.
 */
export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>("GET", path, undefined, options),

  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>("POST", path, body, options),

  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>("PUT", path, body, options),

  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>("PATCH", path, body, options),

  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>("DELETE", path, undefined, options),
};
