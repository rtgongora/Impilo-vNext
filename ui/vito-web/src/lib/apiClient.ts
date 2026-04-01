/**
 * VITO API Client — Trust-header-aware fetch wrapper.
 *
 * Injects all required Impilo trust headers on every request.
 * Reads auth state directly from sessionStorage (no React dependency).
 *
 * Trust headers injected:
 *   x-tenant-id        — from sessionStorage["vito:tenant_id"]
 *   x-actor-id         — from stored auth user
 *   x-actor-type       — OPERATOR (admin) | CITIZEN (portal)
 *   x-purpose-of-use   — OPERATIONS (admin) | SELF_SERVICE (portal)
 *   x-correlation-id   — crypto.randomUUID() per request
 *   x-access-mode      — INTERNAL (admin) | EXTERNAL (portal)
 *   x-step-up-token    — from sessionStorage["vito:step_up_token"] (portal)
 *   Authorization      — Bearer <token>
 *   Idempotency-Key    — crypto.randomUUID() (POST / PUT / PATCH only)
 *
 * Header names sourced from shared-ui/lib/contracts.ts (TRUST_HEADERS).
 * x-access-mode is a vito-web extension not yet in shared-ui.
 */

import { VITO_STORAGE_KEYS } from "./constants";

const TRUST_HEADERS = {
  TENANT_ID: "x-tenant-id",
  ACTOR_ID: "x-actor-id",
  ACTOR_TYPE: "x-actor-type",
  PURPOSE_OF_USE: "x-purpose-of-use",
  CORRELATION_ID: "x-correlation-id",
} as const;

// In the browser, use same-origin so Next.js rewrites proxy /v1/* → Envoy (avoids CORS).
// In SSR or when overridden by env var, use the absolute URL.
const VITO_API_BASE =
  process.env.NEXT_PUBLIC_VITO_API_URL ||
  (typeof window !== "undefined" ? "" : "http://localhost:10000");

const X_ACCESS_MODE = "x-access-mode";
const X_STEP_UP_TOKEN = "x-step-up-token";

export interface VitoAuthUser {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  actorType: "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM";
}

export class VitoApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly correlationId: string
  ) {
    super(message);
    this.name = "VitoApiError";
  }
}

export interface RequestOptions {
  purposeOfUse?: string;
  accessMode?: "INTERNAL" | "EXTERNAL";
}

type StepUpListener = (correlationId: string) => void;
const stepUpListeners: Set<StepUpListener> = new Set();

export function onStepUpRequired(listener: StepUpListener): () => void {
  stepUpListeners.add(listener);
  return () => stepUpListeners.delete(listener);
}

function emitStepUp(correlationId: string): void {
  stepUpListeners.forEach((listener) => listener(correlationId));
}

function readAuthUser(): VitoAuthUser | null {
  try {
    const raw = sessionStorage.getItem(VITO_STORAGE_KEYS.AUTH_USER);
    return raw ? (JSON.parse(raw) as VitoAuthUser) : null;
  } catch {
    return null;
  }
}

function readToken(): string | null {
  return sessionStorage.getItem(VITO_STORAGE_KEYS.AUTH_TOKEN);
}

function readTenantId(): string {
  return sessionStorage.getItem(VITO_STORAGE_KEYS.TENANT_ID) || "00000000-0000-0000-0000-000000000001";
}

function readStepUpToken(): string | null {
  return sessionStorage.getItem(VITO_STORAGE_KEYS.STEP_UP_TOKEN);
}

function deriveAccessMode(actorType: string): "INTERNAL" | "EXTERNAL" {
  return actorType === "CITIZEN" ? "EXTERNAL" : "INTERNAL";
}

function derivePurposeOfUse(actorType: string): string {
  return actorType === "CITIZEN" ? "SELF_SERVICE" : "OPERATIONS";
}

function buildHeaders(
  method: string,
  correlationId: string,
  opts?: RequestOptions
): Record<string, string> {
  const user = readAuthUser();
  const token = readToken();
  const tenantId = readTenantId();
  const stepUpToken = readStepUpToken();

  const actorType = user?.actorType ?? "OPERATOR";
  const accessMode = opts?.accessMode ?? deriveAccessMode(actorType);
  const purposeOfUse = opts?.purposeOfUse ?? derivePurposeOfUse(actorType);

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    [TRUST_HEADERS.TENANT_ID]: tenantId,
    [TRUST_HEADERS.CORRELATION_ID]: correlationId,
    [TRUST_HEADERS.PURPOSE_OF_USE]: purposeOfUse,
    [X_ACCESS_MODE]: accessMode,
    "X-Pod-ID": "national",
    "X-Request-ID": crypto.randomUUID(),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  if (user) {
    headers[TRUST_HEADERS.ACTOR_ID] = user.id;
    headers[TRUST_HEADERS.ACTOR_TYPE] = user.actorType;
  }

  if (stepUpToken) {
    headers[X_STEP_UP_TOKEN] = stepUpToken;
  }

  const mutatingMethods = ["POST", "PUT", "PATCH"];
  if (mutatingMethods.includes(method.toUpperCase())) {
    headers["Idempotency-Key"] = crypto.randomUUID();
  }

  return headers;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  opts?: RequestOptions
): Promise<T> {
  const correlationId = crypto.randomUUID();
  const url = `${VITO_API_BASE}${path}`;
  const headers = buildHeaders(method, correlationId, opts);

  const response = await fetch(url, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 401) {
    let errorBody: Record<string, unknown> | null = null;
    try {
      errorBody = await response.json();
    } catch {
      // non-JSON body
    }

    if (errorBody?.decision === "STEP_UP_REQUIRED") {
      emitStepUp(correlationId);
    }

    throw new VitoApiError(
      401,
      (errorBody?.errorCode as string) ?? "UNAUTHORIZED",
      (errorBody?.message as string) ?? "Authentication required",
      correlationId
    );
  }

  if (!response.ok) {
    let errorBody: Record<string, unknown> | null = null;
    try {
      errorBody = await response.json();
    } catch {
      // non-JSON body
    }

    throw new VitoApiError(
      response.status,
      (errorBody?.error as string) ?? `HTTP_${response.status}`,
      (errorBody?.message as string) ?? response.statusText ?? "Unexpected error",
      correlationId
    );
  }

  if (response.status === 204) {
    return undefined as unknown as T;
  }

  const contentType = response.headers.get("Content-Type") ?? "";
  if (contentType.includes("application/json")) {
    const envelope = await response.json() as { success?: boolean; data?: T; [k: string]: unknown };
    return (envelope?.data !== undefined ? envelope.data : envelope) as T;
  }

  return response.text() as unknown as Promise<T>;
}

async function requestBlob(path: string, opts?: RequestOptions): Promise<Blob> {
  const correlationId = crypto.randomUUID();
  const url = `${VITO_API_BASE}${path}`;
  const headers = buildHeaders("GET", correlationId, opts);
  delete headers["Content-Type"];

  const response = await fetch(url, { method: "GET", headers });

  if (!response.ok) {
    let errorBody: Record<string, unknown> | null = null;
    try {
      errorBody = await response.json();
    } catch {
      // non-JSON body
    }

    throw new VitoApiError(
      response.status,
      (errorBody?.error as string) ?? `HTTP_${response.status}`,
      (errorBody?.message as string) ?? response.statusText ?? "Unexpected error",
      correlationId
    );
  }

  return response.blob();
}

export const vitoApiClient = {
  get: <T>(path: string, opts?: RequestOptions) =>
    request<T>("GET", path, undefined, opts),

  post: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>("POST", path, body, opts),

  put: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>("PUT", path, body, opts),

  patch: <T>(path: string, body?: unknown, opts?: RequestOptions) =>
    request<T>("PATCH", path, body, opts),

  delete: <T>(path: string, opts?: RequestOptions) =>
    request<T>("DELETE", path, undefined, opts),

  blob: (path: string, opts?: RequestOptions) => requestBlob(path, opts),
};
