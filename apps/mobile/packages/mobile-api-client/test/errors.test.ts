import { describe, it, expect } from "vitest";
import { ApiError, NetworkError, TimeoutError, StepUpRequiredError, isRetryable } from "../src/errors";

describe("ApiError", () => {
  it("creates from envelope", () => {
    const err = ApiError.fromEnvelope(
      { code: "VALIDATION_ERROR", message: "Invalid input", status: 400 },
      "corr-123"
    );
    expect(err.code).toBe("VALIDATION_ERROR");
    expect(err.message).toBe("Invalid input");
    expect(err.status).toBe(400);
    expect(err.correlationId).toBe("corr-123");
    expect(err).toBeInstanceOf(Error);
  });

  it("creates from response with no body (defaults to HTTP_<status>)", () => {
    const err = ApiError.fromResponse(500, "corr-456");
    expect(err.status).toBe(500);
    expect(err.code).toBe("HTTP_500");
    expect(err.message).toBe("Request failed with status 500");
    expect(err.correlationId).toBe("corr-456");
  });

  it("creates from flat legacy body (errorCode / errorMessage)", () => {
    const err = ApiError.fromResponse(400, "corr-legacy", {
      errorCode: "LEGACY_ERR",
      errorMessage: "Legacy failure",
    });
    expect(err.code).toBe("LEGACY_ERR");
    expect(err.message).toBe("Legacy failure");
    expect(err.status).toBe(400);
  });

  it("creates from BFF v1.2 nested error envelope and preserves stable code", () => {
    // Matches WalletController's WALLET_UPSTREAM_UNAVAILABLE response.
    const err = ApiError.fromResponse(503, "corr-wallet", {
      error: {
        code: "WALLET_UPSTREAM_UNAVAILABLE",
        message: "The wallet service is temporarily unavailable. Please try again shortly.",
      },
      meta: { request_id: "req-1", correlation_id: "corr-wallet" },
    });
    expect(err.status).toBe(503);
    expect(err.code).toBe("WALLET_UPSTREAM_UNAVAILABLE");
    expect(err.message).toContain("temporarily unavailable");
    expect(err.correlationId).toBe("corr-wallet");
  });

  it("creates from BFF v1.2 envelope with details object", () => {
    const err = ApiError.fromResponse(400, "corr-details", {
      error: {
        code: "VALIDATION",
        message: "amount must be positive",
        details: { field: "amount" },
      },
    });
    expect(err.code).toBe("VALIDATION");
    expect(err.message).toBe("amount must be positive");
    expect(err.details).toEqual({ field: "amount" });
  });

  it("falls back to HTTP_<status> when nested error has no code or message", () => {
    const err = ApiError.fromResponse(500, "corr-empty", { error: {} });
    expect(err.code).toBe("HTTP_500");
    expect(err.message).toBe("Request failed with status 500");
  });

  it("ignores non-object error fields", () => {
    const err = ApiError.fromResponse(500, "corr-bad", { error: "boom" });
    expect(err.code).toBe("HTTP_500");
  });
});

describe("isRetryable", () => {
  it("returns true for NetworkError", () => {
    expect(isRetryable(new NetworkError("fail", "c1"))).toBe(true);
  });

  it("returns true for TimeoutError", () => {
    expect(isRetryable(new TimeoutError(5000, "c2"))).toBe(true);
  });

  it("returns true for 5xx ApiError", () => {
    expect(isRetryable(new ApiError({ code: "X", message: "X", status: 502, correlationId: "c3" }))).toBe(true);
    expect(isRetryable(new ApiError({ code: "X", message: "X", status: 503, correlationId: "c4" }))).toBe(true);
  });

  it("returns false for 4xx ApiError", () => {
    expect(isRetryable(new ApiError({ code: "X", message: "X", status: 400, correlationId: "c5" }))).toBe(false);
    expect(isRetryable(new ApiError({ code: "X", message: "X", status: 404, correlationId: "c6" }))).toBe(false);
  });

  it("returns false for generic Error", () => {
    expect(isRetryable(new Error("generic"))).toBe(false);
  });
});

describe("StepUpRequiredError", () => {
  it("captures methods and correlationId", () => {
    const err = new StepUpRequiredError(["TOTP", "SMS"], "corr-789");
    expect(err.methods).toEqual(["TOTP", "SMS"]);
    expect(err.correlationId).toBe("corr-789");
    expect(err.name).toBe("StepUpRequiredError");
  });
});
