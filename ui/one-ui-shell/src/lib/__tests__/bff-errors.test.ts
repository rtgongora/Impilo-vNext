import { describe, expect, it } from "vitest";

import {
  bffErrorMessage,
  isDegradedResponse,
  parseBffError,
  parseBffFailures,
} from "../bff-errors";

describe("parseBffError", () => {
  it("reads the flat register envelope emitted by EmergencyHonesty", () => {
    const parsed = parseBffError({
      error: "emergency_alerts_unavailable",
      message:
        "Emergency alerts could not be retrieved. Do not treat this as an absence of emergency alerts.",
      meta: { degraded: true, correlation_id: "corr-1", request_id: "req-1" },
    });

    expect(parsed?.code).toBe("emergency_alerts_unavailable");
    expect(parsed?.message).toContain("Do not treat this as an absence of");
    expect(parsed?.degraded).toBe(true);
    expect(parsed?.correlationId).toBe("corr-1");
    expect(parsed?.requestId).toBe("req-1");
  });

  it("reads the nested ResponseStatusException envelope the 31 ED routes still emit", () => {
    const parsed = parseBffError({
      error: {
        code: "HTTP_ERROR",
        message: "critical result not acted on",
        request_id: "req-2",
        correlation_id: "corr-2",
      },
    });

    expect(parsed?.code).toBe("HTTP_ERROR");
    expect(parsed?.message).toBe("critical result not acted on");
    expect(parsed?.correlationId).toBe("corr-2");
  });

  it("returns null for a body that is not an error envelope", () => {
    expect(parseBffError({ data: [] })).toBeNull();
    expect(parseBffError(null)).toBeNull();
    expect(parseBffError("boom")).toBeNull();
  });

  it("does not invent a message when the envelope carries none", () => {
    expect(parseBffError({ error: "x_unavailable" })?.message).toBeNull();
  });
});

describe("bffErrorMessage", () => {
  it("prefers the upstream's own words over any local phrasing", () => {
    const message = bffErrorMessage(
      { error: { code: "HTTP_ERROR", message: "decline_reason is required" } },
      "order set items",
    );
    expect(message).toBe("decline_reason is required");
  });

  it("states the limit rather than reassuring when the server said nothing quotable", () => {
    expect(bffErrorMessage({}, "emergency alerts")).toBe(
      "Emergency alerts could not be retrieved. Do not treat this as an absence of emergency alerts.",
    );
  });
});

describe("composite failures", () => {
  it("names each blind source so one dead upstream costs one tile, not the board", () => {
    const failures = parseBffFailures({
      items: { episodes: [] },
      failures: [
        {
          source: "alerts",
          error: "emergency_alerts_unavailable",
          message: "Do not treat this as an absence of emergency alerts.",
        },
      ],
    });

    expect(failures).toHaveLength(1);
    expect(failures[0].source).toBe("alerts");
  });

  it("treats an empty items list on a healthy read as genuinely empty, not degraded", () => {
    expect(isDegradedResponse({ items: { episodes: [] }, failures: [], meta: { degraded: false } })).toBe(
      false,
    );
  });

  it("reports degraded when the envelope said so", () => {
    expect(isDegradedResponse({ items: {}, failures: [], meta: { degraded: true } })).toBe(true);
  });
});
