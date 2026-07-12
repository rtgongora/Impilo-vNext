import { afterEach, describe, expect, it } from "vitest";
import {
  GATEWAY_PILLARS,
  INTENT_QUERY_PARAM,
  MAX_INTENT_AGE_MS,
  captureIntent,
  captureIntentFromToken,
  consumeIntent,
  createIntent,
  decodeIntentToken,
  encodeIntentToken,
  isGatewayPillar,
  isSafeIntentDestination,
  peekIntent,
  resolveIntentDestination,
  validateIntent,
  withIntentToken,
  type GatewayIntent,
} from "@/lib/gateway-intent";

function bookingIntent(overrides: Partial<GatewayIntent> = {}): GatewayIntent {
  return {
    pillar: "get-care",
    goal: "book-appointment",
    params: { dest: "/citizen/appointments/new", from: "/welcome/find-care", facility: "F-001" },
    createdAt: Date.now(),
    ...overrides,
  };
}

afterEach(() => {
  sessionStorage.clear();
});

describe("gateway intent validation", () => {
  it("declares exactly the nine doctrine pillars", () => {
    expect(GATEWAY_PILLARS).toHaveLength(9);
    expect(isGatewayPillar("get-care")).toBe(true);
    expect(isGatewayPillar("emergency-help")).toBe(true);
    expect(isGatewayPillar("tshepo")).toBe(false); // internal names are not pillars
  });

  it("accepts a well-formed intent", () => {
    expect(validateIntent(bookingIntent())).not.toBeNull();
  });

  it("rejects unknown pillars", () => {
    expect(validateIntent({ ...bookingIntent(), pillar: "unknown-pillar" })).toBeNull();
  });

  it("rejects malformed goals", () => {
    expect(validateIntent(bookingIntent({ goal: "" }))).toBeNull();
    expect(validateIntent(bookingIntent({ goal: "Bad Goal!" }))).toBeNull();
    expect(validateIntent(bookingIntent({ goal: "x".repeat(65) }))).toBeNull();
  });

  it("rejects oversized params", () => {
    const tooMany = Object.fromEntries(
      Array.from({ length: 13 }, (_, i) => [`k${i}`, "v"]),
    );
    expect(validateIntent(bookingIntent({ params: tooMany }))).toBeNull();
    expect(
      validateIntent(bookingIntent({ params: { note: "x".repeat(257) } })),
    ).toBeNull();
    expect(
      validateIntent({ ...bookingIntent(), params: { n: 42 } as unknown as Record<string, string> }),
    ).toBeNull();
  });

  it("rejects non-relative or auth-flow destinations (same posture as isSafeReturnTo)", () => {
    expect(validateIntent(bookingIntent({ params: { dest: "https://evil.example" } }))).toBeNull();
    expect(validateIntent(bookingIntent({ params: { dest: "//evil.example" } }))).toBeNull();
    expect(validateIntent(bookingIntent({ params: { dest: "/auth/login" } }))).toBeNull();
    expect(validateIntent(bookingIntent({ params: { from: "//evil.example" } }))).toBeNull();
    expect(isSafeIntentDestination("/citizen/appointments/new")).toBe(true);
    expect(isSafeIntentDestination("/consent")).toBe(false);
  });

  it("rejects stale and future-dated intents", () => {
    const now = Date.now();
    expect(
      validateIntent(bookingIntent({ createdAt: now - MAX_INTENT_AGE_MS - 1 }), now),
    ).toBeNull();
    expect(validateIntent(bookingIntent({ createdAt: now + 120_000 }), now)).toBeNull();
  });

  it("createIntent stamps createdAt and validates", () => {
    const intent = createIntent("get-care", "book-appointment", { dest: "/scheduling" });
    expect(intent).not.toBeNull();
    expect(createIntent("get-care", "BAD GOAL")).toBeNull();
  });
});

describe("gateway intent token round-trip", () => {
  it("encodes to a URL-safe token and decodes back", () => {
    const intent = bookingIntent();
    const token = encodeIntentToken(intent);
    expect(token).toBeTruthy();
    expect(token).toMatch(/^[A-Za-z0-9_-]+$/);
    const decoded = decodeIntentToken(token);
    expect(decoded).toEqual(intent);
  });

  it("rejects garbage, tampered, and oversized tokens without throwing", () => {
    expect(decodeIntentToken(null)).toBeNull();
    expect(decodeIntentToken("not/base64url+chars")).toBeNull();
    expect(decodeIntentToken("dGFtcGVyZWQ")).toBeNull(); // valid base64url, not an intent
    expect(decodeIntentToken("A".repeat(5000))).toBeNull();
  });

  it("withIntentToken appends the gwi param with correct separator", () => {
    const intent = bookingIntent();
    expect(withIntentToken("/auth/login", intent)).toMatch(
      new RegExp(`^/auth/login\\?${INTENT_QUERY_PARAM}=`),
    );
    expect(withIntentToken("/auth/login?returnTo=%2Fhome", intent)).toContain(
      `&${INTENT_QUERY_PARAM}=`,
    );
  });
});

describe("gateway intent persistence", () => {
  it("captures, peeks (non-destructive) and consumes (clearing)", () => {
    const intent = bookingIntent();
    expect(captureIntent(intent)).toBe(true);

    expect(peekIntent()).toEqual(intent);
    expect(peekIntent()).toEqual(intent); // peek does not clear

    expect(consumeIntent()).toEqual(intent);
    expect(peekIntent()).toBeNull(); // consume clears
    expect(consumeIntent()).toBeNull();
  });

  it("refuses to capture an invalid intent", () => {
    expect(
      captureIntent({ ...bookingIntent(), pillar: "nope" as GatewayIntent["pillar"] }),
    ).toBe(false);
    expect(peekIntent()).toBeNull();
  });

  it("captureIntentFromToken stores only valid tokens", () => {
    const token = encodeIntentToken(bookingIntent());
    const intent = captureIntentFromToken(token);
    expect(intent).not.toBeNull();
    expect(peekIntent()).toEqual(intent);

    sessionStorage.clear();
    expect(captureIntentFromToken("garbage!!")).toBeNull();
    expect(peekIntent()).toBeNull();
  });

  it("expired stored intents are dropped on read", () => {
    const stale = bookingIntent({ createdAt: Date.now() - MAX_INTENT_AGE_MS - 1000 });
    sessionStorage.setItem("exp:gateway_intent", JSON.stringify(stale));
    expect(peekIntent()).toBeNull();
    expect(sessionStorage.getItem("exp:gateway_intent")).toBeNull(); // cleaned up
  });
});

describe("resolveIntentDestination", () => {
  it("returns the safe dest param", () => {
    expect(resolveIntentDestination(bookingIntent())).toBe("/citizen/appointments/new");
  });

  it("returns null when no dest is carried — never fabricates a route", () => {
    expect(resolveIntentDestination(bookingIntent({ params: {} }))).toBeNull();
    expect(resolveIntentDestination(null)).toBeNull();
  });
});
