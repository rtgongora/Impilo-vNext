/**
 * Public (anonymous) API Client — the pre-authentication seam.
 *
 * The authenticated {@link apiClient} throws NO_SESSION before sign-in and always
 * attaches a bearer token via the auth store. Guest gateway surfaces (contact-first
 * OTP onboarding, public health information, practitioner/facility verification,
 * public SOS status) run BEFORE any session exists and hit the gateway's permitAll
 * lanes, so they cannot use the session-gated client.
 *
 * This client builds only the four hard-required platform headers (tenant, pod,
 * request, correlation) plus content-type — via {@link buildPublicTrustHeaders} —
 * with NO Authorization header and NO actor identity. It is a lean sibling of the
 * authenticated client: same base URL, timeout, retry, and error types, but no
 * token refresh and no step-up handling. The authenticated path is untouched.
 *
 * Public lanes it serves:
 *   - GET  /internal/v1/public/gateway/**            (anonymous defaults filter fills GETs)
 *   - POST /internal/v1/public/gateway/sos           (anonymous emergency intake)
 *   - POST /internal/v1/auth/contact/otp/request     (contact-first OTP)
 *   - POST /internal/v1/auth/contact/otp/verify      (REGISTER → auto-login token)
 *   - GET/POST /internal/v1/auth/register*           (legacy readiness + register)
 */

import { buildPublicTrustHeaders, generateId, type PublicHeaderContext } from "@impilo/mobile-trust";
import { getApiClientConfig } from "./config";
import { ApiError, NetworkError, TimeoutError, isRetryable } from "./errors";
import type { RequestOptions, ApiResponse } from "./client";

function publicContext(purposeOfUse?: string): PublicHeaderContext {
  const config = getApiClientConfig();
  return {
    tenantId: config.publicTenantId,
    podId: config.publicPodId,
    purposeOfUse,
  };
}

async function executePublicRequest<T>(
  method: string,
  url: string,
  body: unknown,
  correlationId: string,
  options?: RequestOptions
): Promise<ApiResponse<T>> {
  const config = getApiClientConfig();
  const timeoutMs = options?.timeoutMs ?? config.defaultTimeoutMs;

  const headers = buildPublicTrustHeaders(publicContext(options?.purposeOfUse), {
    method,
    correlationId,
    clientTimeoutMs: timeoutMs,
  });
  if (options?.headers) {
    Object.assign(headers, options.headers);
  }

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
      signal: options?.signal ?? controller.signal,
    });
    clearTimeout(timeoutId);

    const responseHeaders: Record<string, string> = {};
    response.headers.forEach((value: string, key: string) => {
      responseHeaders[key] = value;
    });

    if (!response.ok) {
      let errorBody: unknown = null;
      try {
        errorBody = await response.json();
      } catch {
        // Non-JSON error body (e.g. an upstream 502 with an empty payload).
      }
      throw ApiError.fromResponse(response.status, correlationId, errorBody);
    }

    const contentType = response.headers.get("content-type") ?? "";
    let data: T;
    if (response.status === 204) {
      data = undefined as T;
    } else if (contentType.includes("application/json")) {
      data = (await response.json()) as T;
    } else {
      data = (await response.text()) as unknown as T;
    }

    return { data, status: response.status, correlationId, headers: responseHeaders };
  } catch (error) {
    clearTimeout(timeoutId);
    if (error instanceof ApiError) throw error;
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new TimeoutError(timeoutMs, correlationId);
    }
    throw new NetworkError(
      error instanceof Error ? error.message : "Network request failed",
      correlationId
    );
  }
}

async function publicRequest<T>(
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
      await sleep(config.retryBaseDelayMs * Math.pow(2, attempt - 1));
    }
    try {
      return await executePublicRequest<T>(method, url, body, correlationId, options);
    } catch (error) {
      lastError = error;
      if (!isRetryable(error) || attempt === maxRetries) {
        throw error;
      }
    }
  }
  throw lastError;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Anonymous public API client — the pre-authentication counterpart to {@link apiClient}.
 * Same verb surface; no session, no bearer.
 */
export const publicApiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    publicRequest<T>("GET", path, undefined, options),

  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    publicRequest<T>("POST", path, body, options),
};
