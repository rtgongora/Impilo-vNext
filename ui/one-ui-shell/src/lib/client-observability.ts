/**
 * Client-observability sink — a best-effort, BATCHED, PII-free network sink for client-side
 * failure signals.
 *
 * WHY: `nompilo-failure.ts` records failures into an in-memory Zustand feed (for Nompilo
 * recovery), but that feed is NEVER networked. This module is the one place that ships those
 * same signals to the platform so operators can see dead-ends, render errors, and API failures
 * that citizens hit in the wild. It does NOT own the feed — it is fanned out from
 * `recordNompiloFailure` so there is a single record site per event.
 *
 * PRIVACY — allow-list ONLY. A shipped event carries exactly, and only:
 *   - route          : the page pathname (query string and hash are stripped defensively),
 *   - code           : a machine error code (e.g. UI_DEAD_END, UI_RENDER_ERROR, HTTP_503),
 *   - correlationId? : the wire correlation id already stamped by the api-client,
 *   - requestId?     : the wire request id already stamped by the api-client,
 *   - status?        : the HTTP status, when it was an HTTP failure,
 *   - at             : a timestamp (ms epoch or ISO string).
 * It NEVER carries request/response bodies, free text, messages, credentials, or any Health-ID
 * / personal / clinical content. Unknown keys on the input are dropped by construction.
 *
 * BEST-EFFORT — this must never break a page: every path swallows its own errors, there are no
 * retries that could storm the backend, the buffer is capped (oldest dropped), and everything is
 * SSR-safe (no-op without `window`).
 */

/** The PII-free shipped shape — `NompiloFailureSignal` minus `action`. */
export interface ClientObservation {
  route: string;
  code: string;
  correlationId?: string;
  requestId?: string;
  status?: number;
  /** ms epoch or ISO-8601 string. */
  at: number | string;
}

/** Frozen upstream contract (experience-bff public gateway lane). */
const TELEMETRY_PATH = "/internal/v1/public/gateway/client-telemetry";

/** Debounce window for a non-urgent flush of a non-empty buffer. */
const FLUSH_INTERVAL_MS = 10_000;

/** Hard cap on buffered events; oldest are dropped once exceeded (no unbounded growth). */
export const MAX_BUFFER = 50;

let buffer: ClientObservation[] = [];
let flushTimer: ReturnType<typeof setTimeout> | null = null;
let listenersBound = false;

/**
 * Resolve the BFF origin the same way api-client does: an explicit NEXT_PUBLIC_BFF_URL wins,
 * otherwise (in the browser) use a relative path so the request proxies through the Next.js
 * server and stays same-origin.
 */
function bffBaseUrl(): string {
  const explicit = typeof process !== "undefined" ? process.env.NEXT_PUBLIC_BFF_URL : undefined;
  return explicit || "";
}

/** Strip a route down to a bare pathname — no origin, no query string, no hash. */
function toPathname(raw: unknown): string {
  if (typeof raw !== "string") return "/";
  let s = raw.trim();
  if (!s) return "/";
  // Drop hash then query.
  s = s.split("#")[0].split("?")[0];
  // Drop any scheme://host prefix if a full URL slipped through.
  const schemeIdx = s.indexOf("://");
  if (schemeIdx !== -1) {
    const afterScheme = s.slice(schemeIdx + 3);
    const slash = afterScheme.indexOf("/");
    s = slash === -1 ? "/" : afterScheme.slice(slash);
  }
  return s || "/";
}

/**
 * Reduce an arbitrary input to the PII-free allow-list. Returns null if the signal cannot form a
 * valid event (no code). This is the single defensive choke point — unknown keys never survive.
 */
export function sanitizeObservation(signal: unknown): ClientObservation | null {
  if (!signal || typeof signal !== "object") return null;
  const input = signal as Record<string, unknown>;

  const code = typeof input.code === "string" ? input.code.trim() : "";
  if (!code) return null;

  const clean: ClientObservation = {
    route: toPathname(input.route),
    code,
    at: typeof input.at === "number" || typeof input.at === "string" ? input.at : Date.now(),
  };
  if (typeof input.correlationId === "string" && input.correlationId) {
    clean.correlationId = input.correlationId;
  }
  if (typeof input.requestId === "string" && input.requestId) {
    clean.requestId = input.requestId;
  }
  if (typeof input.status === "number") {
    clean.status = input.status;
  }
  return clean;
}

function ensureListeners(): void {
  if (listenersBound || typeof window === "undefined" || typeof document === "undefined") return;
  listenersBound = true;
  try {
    // Flush the moment the tab is backgrounded/closed — the last chance to ship buffered events.
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "hidden") flush();
    });
    window.addEventListener("pagehide", () => flush());
  } catch {
    // If listener binding fails we still flush on the debounce timer.
  }
}

function scheduleFlush(): void {
  if (flushTimer !== null || typeof window === "undefined") return;
  flushTimer = setTimeout(() => {
    flushTimer = null;
    flush();
  }, FLUSH_INTERVAL_MS);
}

/**
 * Ship whatever is buffered, best-effort. Uses `navigator.sendBeacon` when available (survives
 * unload); falls back to a keepalive `fetch`. All errors are swallowed and there is no retry.
 */
export function flush(): void {
  if (typeof window === "undefined") return;
  if (buffer.length === 0) return;

  // Drain first so a failing send never re-buffers into a storm.
  const events = buffer;
  buffer = [];
  if (flushTimer !== null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }

  const url = `${bffBaseUrl()}${TELEMETRY_PATH}`;
  const payload = JSON.stringify({ events });

  try {
    if (typeof navigator !== "undefined" && typeof navigator.sendBeacon === "function") {
      const blob = new Blob([payload], { type: "application/json" });
      navigator.sendBeacon(url, blob);
      return;
    }
  } catch {
    // sendBeacon unavailable/failed — fall through to fetch.
  }

  try {
    if (typeof fetch === "function") {
      void fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: payload,
        keepalive: true,
        credentials: "same-origin",
      }).catch(() => {
        // Best-effort: drop on the floor. No retry.
      });
    }
  } catch {
    // No transport available — drop silently.
  }
}

/**
 * Enqueue a PII-free observation for batched shipping. Safe to call from any non-React context.
 * No-op on the server or when the input reduces to nothing. Swallows all of its own errors.
 */
export function recordClientObservation(signal: unknown): void {
  try {
    if (typeof window === "undefined") return;
    const clean = sanitizeObservation(signal);
    if (!clean) return;

    buffer.push(clean);
    if (buffer.length > MAX_BUFFER) {
      // Drop oldest to keep the buffer bounded.
      buffer.splice(0, buffer.length - MAX_BUFFER);
    }

    ensureListeners();
    scheduleFlush();
  } catch {
    // Telemetry must never break the caller's primary path.
  }
}

/* ── test-only hooks (not part of the public contract) ─────────────────────────────────────── */

/** @internal */
export function __peekBufferForTest(): ClientObservation[] {
  return [...buffer];
}

/** @internal */
export function __resetForTest(): void {
  buffer = [];
  if (flushTimer !== null) {
    clearTimeout(flushTimer);
    flushTimer = null;
  }
}
