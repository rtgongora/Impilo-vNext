import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  MAX_BUFFER,
  __peekBufferForTest,
  __resetForTest,
  recordClientObservation,
  sanitizeObservation,
} from "./client-observability";

describe("client-observability sink — PII allow-list", () => {
  it("keeps ONLY the allow-listed fields and drops everything else", () => {
    const clean = sanitizeObservation({
      route: "/welcome/find-care",
      code: "HTTP_503",
      correlationId: "corr-1",
      requestId: "req-1",
      status: 503,
      at: 1234,
      // Everything below is disallowed and must never survive:
      message: "patient John Doe could not be found",
      healthId: "ZW-HID-000123",
      body: { note: "free text" },
      name: "Jane",
      action: "Book a service",
    });
    expect(clean).toEqual({
      route: "/welcome/find-care",
      code: "HTTP_503",
      correlationId: "corr-1",
      requestId: "req-1",
      status: 503,
      at: 1234,
    });
    // No leaked keys.
    expect(Object.keys(clean ?? {}).sort()).toEqual(
      ["at", "code", "correlationId", "requestId", "route", "status"].sort(),
    );
  });

  it("strips query string and hash from the route (pathname only)", () => {
    expect(sanitizeObservation({ route: "/records?patient=abc#tab", code: "X" })?.route).toBe(
      "/records",
    );
  });

  it("strips a full-URL origin down to the pathname", () => {
    expect(
      sanitizeObservation({ route: "https://impilo.example/status?x=1", code: "X" })?.route,
    ).toBe("/status");
  });

  it("returns null when there is no machine code (nothing shippable)", () => {
    expect(sanitizeObservation({ route: "/x" })).toBeNull();
    expect(sanitizeObservation(null)).toBeNull();
    expect(sanitizeObservation("nope")).toBeNull();
  });

  it("drops non-string ids and non-number status defensively", () => {
    const clean = sanitizeObservation({
      route: "/x",
      code: "Y",
      correlationId: 42,
      requestId: {},
      status: "503",
    });
    expect(clean).toEqual({ route: "/x", code: "Y", at: expect.anything() });
  });
});

describe("client-observability sink — buffer cap", () => {
  beforeEach(() => __resetForTest());
  afterEach(() => __resetForTest());

  it("caps the buffer at MAX_BUFFER, dropping oldest", () => {
    const total = MAX_BUFFER + 10;
    for (let i = 0; i < total; i++) {
      recordClientObservation({ route: "/p", code: `C${i}` });
    }
    const buf = __peekBufferForTest();
    expect(buf.length).toBe(MAX_BUFFER);
    // Oldest dropped: first retained code is C10, last is C(total-1).
    expect(buf[0].code).toBe("C10");
    expect(buf[buf.length - 1].code).toBe(`C${total - 1}`);
  });

  it("silently ignores unshippable signals (no code) without buffering", () => {
    recordClientObservation({ route: "/p" });
    expect(__peekBufferForTest().length).toBe(0);
  });
});
