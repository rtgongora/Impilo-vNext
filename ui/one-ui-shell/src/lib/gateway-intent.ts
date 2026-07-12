/**
 * Gateway semantic intent primitive — health-services-gateway doctrine §2 (nine intent
 * pillars) and §4.1 law 3 ("journey context survives every trust transition").
 *
 * An Intent is what the person is trying to do — a first-class primitive of the gateway,
 * not a URL. It is captured on a public surface, survives login/step-up redirects
 * (sessionStorage + a URL-safe token in the `gwi` query param so it can cross the
 * public→authenticated boundary even when storage is unavailable), and is consumed once
 * after auth to restore the person to the exact step they were on.
 *
 * Safety posture mirrors isSafeReturnTo (resolve-post-login-destination.ts): unknown
 * pillars, oversized payloads, and non-relative destinations are rejected on decode —
 * a token is untrusted input.
 */

/** The nine intent pillars (doctrine §2). Every intent attaches to exactly one. */
export const GATEWAY_PILLARS = [
  "get-care",
  "my-health",
  "health-information",
  "find-or-verify",
  "cover-and-payments",
  "applications-licensing",
  "feedback-complaints",
  "products-suppliers",
  "emergency-help",
] as const;

export type GatewayPillar = (typeof GATEWAY_PILLARS)[number];

export interface GatewayIntent {
  /** Owning intent pillar (doctrine §2) — never an internal service name. */
  pillar: GatewayPillar;
  /** Short machine goal within the pillar, e.g. "book-appointment". */
  goal: string;
  /**
   * Journey context (all string values). Reserved keys:
   * - `dest`: relative in-journey destination to restore after auth
   * - `from`: the public page the person came from (for "Continue without signing in")
   */
  params: Record<string, string>;
  /** Epoch millis at capture; stale intents are rejected on read. */
  createdAt: number;
}

/** Query param carrying the encoded intent across redirects. */
export const INTENT_QUERY_PARAM = "gwi";

const STORAGE_KEY = "exp:gateway_intent";

/** Intents older than this are stale — never replay an hours-old journey. */
export const MAX_INTENT_AGE_MS = 60 * 60 * 1000;

const MAX_GOAL_LENGTH = 64;
const MAX_PARAM_COUNT = 12;
const MAX_PARAM_KEY_LENGTH = 32;
const MAX_PARAM_VALUE_LENGTH = 256;
/** Hard cap on an encoded token — anything larger is rejected outright. */
export const MAX_TOKEN_LENGTH = 2048;

const GOAL_PATTERN = /^[a-z0-9][a-z0-9-]*$/;

/** Path prefixes an intent destination may never point back into (mirrors isSafeReturnTo). */
const AUTH_EXEMPT_PREFIXES = ["/auth", "/consent"];

export function isGatewayPillar(value: unknown): value is GatewayPillar {
  return typeof value === "string" && (GATEWAY_PILLARS as readonly string[]).includes(value);
}

/**
 * Same safety posture as isSafeReturnTo: relative, not protocol-relative, and never
 * back into the auth/consent flows. Kept local to avoid an import cycle with
 * resolve-post-login-destination.ts (which imports this module).
 */
export function isSafeIntentDestination(path: string | null | undefined): path is string {
  if (!path || typeof path !== "string") return false;
  if (!path.startsWith("/") || path.startsWith("//")) return false;
  if (path.includes("\\")) return false;
  if (AUTH_EXEMPT_PREFIXES.some((prefix) => path.startsWith(prefix))) return false;
  return true;
}

/** Validate an untrusted value into a GatewayIntent, or null. */
export function validateIntent(value: unknown, now: number = Date.now()): GatewayIntent | null {
  if (value == null || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;

  if (!isGatewayPillar(raw.pillar)) return null;

  if (typeof raw.goal !== "string" || raw.goal.length === 0 || raw.goal.length > MAX_GOAL_LENGTH) {
    return null;
  }
  if (!GOAL_PATTERN.test(raw.goal)) return null;

  if (typeof raw.createdAt !== "number" || !Number.isFinite(raw.createdAt)) return null;
  if (raw.createdAt > now + 60_000) return null; // clock-skew tolerance; future intents rejected
  if (now - raw.createdAt > MAX_INTENT_AGE_MS) return null;

  const paramsRaw = raw.params ?? {};
  if (typeof paramsRaw !== "object" || Array.isArray(paramsRaw)) return null;
  const entries = Object.entries(paramsRaw as Record<string, unknown>);
  if (entries.length > MAX_PARAM_COUNT) return null;

  const params: Record<string, string> = {};
  for (const [key, val] of entries) {
    if (key.length === 0 || key.length > MAX_PARAM_KEY_LENGTH) return null;
    if (typeof val !== "string" || val.length > MAX_PARAM_VALUE_LENGTH) return null;
    params[key] = val;
  }

  // Reserved routing keys must be safe relative paths — reject the whole intent
  // rather than silently dropping them (a tampered token is not trustworthy).
  if (params.dest !== undefined && !isSafeIntentDestination(params.dest)) return null;
  if (params.from !== undefined && !isSafeIntentDestination(params.from)) return null;

  return { pillar: raw.pillar, goal: raw.goal, params, createdAt: raw.createdAt };
}

/** Build a new intent (createdAt stamped now). Returns null if inputs don't validate. */
export function createIntent(
  pillar: GatewayPillar,
  goal: string,
  params: Record<string, string> = {},
): GatewayIntent | null {
  return validateIntent({ pillar, goal, params, createdAt: Date.now() });
}

// ── URL-safe token encoding ─────────────────────────────────────────────────────────

function toBase64Url(json: string): string {
  const base64 =
    typeof window !== "undefined" && typeof window.btoa === "function"
      ? window.btoa(unescape(encodeURIComponent(json)))
      : Buffer.from(json, "utf-8").toString("base64");
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(token: string): string | null {
  try {
    const base64 = token.replace(/-/g, "+").replace(/_/g, "/");
    if (typeof window !== "undefined" && typeof window.atob === "function") {
      return decodeURIComponent(escape(window.atob(base64)));
    }
    return Buffer.from(base64, "base64").toString("utf-8");
  } catch {
    return null;
  }
}

/** Encode an intent as a compact URL-safe token for the `gwi` query param. */
export function encodeIntentToken(intent: GatewayIntent): string | null {
  const valid = validateIntent(intent);
  if (!valid) return null;
  const token = toBase64Url(JSON.stringify(valid));
  return token.length <= MAX_TOKEN_LENGTH ? token : null;
}

/** Decode + validate an untrusted token. Null on any failure — never throws. */
export function decodeIntentToken(token: string | null | undefined): GatewayIntent | null {
  if (!token || typeof token !== "string" || token.length > MAX_TOKEN_LENGTH) return null;
  if (!/^[A-Za-z0-9_-]+$/.test(token)) return null;
  const json = fromBase64Url(token);
  if (json == null) return null;
  try {
    return validateIntent(JSON.parse(json));
  } catch {
    return null;
  }
}

/** Append the encoded intent to a relative path (e.g. the login redirect). */
export function withIntentToken(path: string, intent: GatewayIntent): string {
  const token = encodeIntentToken(intent);
  if (!token) return path;
  const separator = path.includes("?") ? "&" : "?";
  return `${path}${separator}${INTENT_QUERY_PARAM}=${token}`;
}

// ── Session persistence (survives login redirects within the tab) ───────────────────

function storage(): Storage | null {
  try {
    if (typeof window === "undefined" || !window.sessionStorage) return null;
    return window.sessionStorage;
  } catch {
    return null; // storage blocked (privacy mode) — token-in-URL still works
  }
}

/** Persist a validated intent for the rest of the auth journey. */
export function captureIntent(intent: GatewayIntent): boolean {
  const valid = validateIntent(intent);
  const store = storage();
  if (!valid || !store) return false;
  try {
    store.setItem(STORAGE_KEY, JSON.stringify(valid));
    return true;
  } catch {
    return false;
  }
}

/** Decode a `gwi` token and, when valid, persist it. Returns the intent either way. */
export function captureIntentFromToken(token: string | null | undefined): GatewayIntent | null {
  const intent = decodeIntentToken(token);
  if (intent) captureIntent(intent);
  return intent;
}

/** Read the pending intent without clearing it (e.g. to explain WHY sign-in is asked). */
export function peekIntent(): GatewayIntent | null {
  const store = storage();
  if (!store) return null;
  try {
    const raw = store.getItem(STORAGE_KEY);
    if (!raw) return null;
    const intent = validateIntent(JSON.parse(raw));
    if (!intent) store.removeItem(STORAGE_KEY); // stale/corrupt — clean up
    return intent;
  } catch {
    return null;
  }
}

/** Read AND clear the pending intent — call exactly when the journey is restored. */
export function consumeIntent(): GatewayIntent | null {
  const intent = peekIntent();
  const store = storage();
  if (store) {
    try {
      store.removeItem(STORAGE_KEY);
    } catch {
      /* already unavailable */
    }
  }
  return intent;
}

/**
 * The in-journey destination an intent restores to, or null when the intent carries
 * none (callers then fall back to existing returnTo / default-landing behaviour —
 * this module never fabricates routes).
 */
export function resolveIntentDestination(intent: GatewayIntent | null | undefined): string | null {
  if (!intent) return null;
  const dest = intent.params.dest;
  return isSafeIntentDestination(dest) ? dest : null;
}
