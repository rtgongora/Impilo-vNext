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

  it("creates from response", () => {
    const err = ApiError.fromResponse(500, "corr-456");
    expect(err.status).toBe(500);
    expect(err.code).toBe("HTTP_500");
    expect(err.correlationId).toBe("corr-456");
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
