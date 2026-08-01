/**
 * Impilo vNext — API Client with Trust Header Injection
 *
 * @deprecated Prefer `@/lib/api-client` (BFF `/internal/v1/*`) for feature screens.
 * This gateway client (`NEXT_PUBLIC_API_GATEWAY_URL`) remains for legacy/policy paths only.
 *
 * Every outbound API request automatically carries the mandatory trust headers
 * from the session and work context stores (Zustand). This ensures that Envoy's
 * ext_authz filter (→ TSHEPO) can evaluate every request.
 *
 * The client also handles:
 *   - STEP_UP_REQUIRED (401) responses by emitting a step-up event
 *   - Correlation ID generation for request tracing
 *   - Normalized error responses
 *
 * Header contract must match: services/tshepo-service/.../core/TrustHeaders.java
 */

import { TRUST_HEADERS } from "./contracts";
import type { ApiError, AuthzResponse, StepUpChallenge } from "./contracts";
import { randomUUID } from "./uuid";

// Re-export for convenience
export { TRUST_HEADERS };

// ============================================================================
// Configuration
// ============================================================================

const API_BASE_URL = process.env.NEXT_PUBLIC_API_GATEWAY_URL || "";

// ============================================================================
// Step-up event bus (simple pub/sub for modal trigger)
// ============================================================================

type StepUpListener = (challenge: StepUpChallenge) => void;
const stepUpListeners: Set<StepUpListener> = new Set();

export function onStepUpRequired(listener: StepUpListener): () => void {
  stepUpListeners.add(listener);
  return () => stepUpListeners.delete(listener);
}

function emitStepUp(challenge: StepUpChallenge): void {
  stepUpListeners.forEach((listener) => listener(challenge));
}

// ============================================================================
// Response wrapper
// ============================================================================

export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Record<string, string>;
  obligations?: {
    maxScope?: string;
    maskFields?: string[];
    loggingLevel?: string;
  };
}

// ============================================================================
// Core fetch wrapper
// ============================================================================

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  options?: { purposeOfUse?: string }
): Promise<ApiResponse<T>> {
  const url = `${API_BASE_URL}${path}`;
  const correlationId = randomUUID();

  // --- Build trust headers from stores ---
  // Context may describe the UI journey, but identity and assurance are always
  // derived by the BFF from its opaque session and overwritten at the gateway.
  const { useWorkContext } = await import("@/hooks/useContext");

  const ctx = useWorkContext.getState();

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    [TRUST_HEADERS.CORRELATION_ID]: correlationId,
  };

  // Inject work context headers
  if (ctx.workContext) {
    headers[TRUST_HEADERS.TENANT_ID] = ctx.workContext.tenantId;
    if (ctx.workContext.facilityId) {
      headers[TRUST_HEADERS.FACILITY_ID] = ctx.workContext.facilityId;
    }
    if (ctx.workContext.workspaceId) {
      headers[TRUST_HEADERS.WORKSPACE_ID] = ctx.workContext.workspaceId;
    }
    if (ctx.workContext.shiftId) {
      headers[TRUST_HEADERS.SHIFT_ID] = ctx.workContext.shiftId;
    }
  }

  // Purpose of use (explicit override or from context)
  headers[TRUST_HEADERS.PURPOSE_OF_USE] =
    options?.purposeOfUse ?? ctx.purposeOfUse;

  // Device fingerprint
  if (ctx.deviceFingerprint) {
    headers[TRUST_HEADERS.DEVICE_FINGERPRINT] = ctx.deviceFingerprint;
  }

  // --- Execute request ---
  const response = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
    credentials: "same-origin",
  });

  // --- Handle STEP_UP_REQUIRED (401) ---
  if (response.status === 401) {
    let authzResponse: AuthzResponse | null = null;
    try {
      authzResponse = await response.json();
    } catch {
      // Response body may not be JSON
    }

    if (authzResponse?.decision === "STEP_UP_REQUIRED") {
      emitStepUp({
        methods: authzResponse.stepUpMethods ?? [],
        correlationId,
        originalRequest: { method, url: path, body },
      });
    }

    throw createApiError(response, authzResponse, correlationId);
  }

  // --- Handle other errors ---
  if (!response.ok) {
    let errorBody: unknown = null;
    try {
      errorBody = await response.json();
    } catch {
      // Non-JSON error response
    }
    throw createApiError(response, errorBody, correlationId);
  }

  // --- Parse successful response ---
  const responseHeaders: Record<string, string> = {};
  response.headers.forEach((value, key) => {
    responseHeaders[key] = value;
  });

  // Extract obligation headers if present
  const obligations = parseObligations(responseHeaders);

  let data: T;
  const contentType = response.headers.get("Content-Type") ?? "";
  if (contentType.includes("application/json")) {
    data = await response.json();
  } else {
    data = (await response.text()) as unknown as T;
  }

  return {
    data,
    status: response.status,
    headers: responseHeaders,
    obligations,
  };
}

// ============================================================================
// Obligation parsing
// ============================================================================

function parseObligations(headers: Record<string, string>) {
  const maxScope = headers[TRUST_HEADERS.MAX_SCOPE];
  const maskFieldsRaw = headers[TRUST_HEADERS.MASK_FIELDS];
  const loggingLevel = headers[TRUST_HEADERS.LOGGING_LEVEL];

  if (!maxScope && !maskFieldsRaw && !loggingLevel) return undefined;

  return {
    maxScope: maxScope || undefined,
    maskFields: maskFieldsRaw ? maskFieldsRaw.split(",") : undefined,
    loggingLevel: loggingLevel || undefined,
  };
}

// ============================================================================
// Error handling
// ============================================================================

function createApiError(
  response: Response,
  body: unknown,
  correlationId: string
): ApiError {
  const errorBody = body as Record<string, unknown> | null;
  return {
    status: response.status,
    code: (errorBody?.errorCode as string) ?? `HTTP_${response.status}`,
    message:
      (errorBody?.errorMessage as string) ??
      response.statusText ??
      "An unexpected error occurred",
    correlationId,
  };
}

// ============================================================================
// Public API — convenience methods
// ============================================================================

export const apiClient = {
  get: <T>(path: string, options?: { purposeOfUse?: string }) =>
    request<T>("GET", path, undefined, options),

  post: <T>(path: string, body?: unknown, options?: { purposeOfUse?: string }) =>
    request<T>("POST", path, body, options),

  put: <T>(path: string, body?: unknown, options?: { purposeOfUse?: string }) =>
    request<T>("PUT", path, body, options),

  patch: <T>(path: string, body?: unknown, options?: { purposeOfUse?: string }) =>
    request<T>("PATCH", path, body, options),

  delete: <T>(path: string, options?: { purposeOfUse?: string }) =>
    request<T>("DELETE", path, undefined, options),
};
