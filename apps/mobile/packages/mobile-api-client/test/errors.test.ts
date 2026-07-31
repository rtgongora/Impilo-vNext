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

  it("ignores non-object error fields with no sibling message", () => {
    const err = ApiError.fromResponse(500, "corr-bad", { error: "boom" });
    expect(err.code).toBe("HTTP_500");
  });

  it("creates from a proxied upstream refusal nested under `data` (ConfidentialCommunityPostnatalController forwarding pct-service's 422 verbatim)", () => {
    const err = ApiError.fromResponse(422, "corr-pnc", {
      data: {
        code: "PNC_SETTING_REQUIRED",
        message:
          "A postnatal contact must say where it happened. HOME, COMMUNITY and VIRTUAL are " +
          "first-class settings; defaulting to FACILITY would relabel a community visit as a clinic attendance.",
      },
      meta: { correlation_id: "corr-pnc" },
    });
    expect(err.status).toBe(422);
    expect(err.code).toBe("PNC_SETTING_REQUIRED");
    expect(err.message).toContain("must say where it happened");
  });

  it("captures sibling fields on a `data` envelope conflict (pct-service's 409 existing_pregnancy_episode_id/reconciliation_task) into details", () => {
    const err = ApiError.fromResponse(409, "corr-preg", {
      data: {
        code: "PREGNANCY_ALREADY_BOOKED",
        message: "This person already has an ongoing pregnancy episode.",
        existing_pregnancy_episode_id: "11111111-2222-4333-8444-555555555555",
        reconciliation_task: "Open the existing pregnancy episode and reconcile this booking against it.",
      },
      meta: { correlation_id: "corr-preg" },
    });
    expect(err.status).toBe(409);
    expect(err.code).toBe("PREGNANCY_ALREADY_BOOKED");
    expect(err.details?.existing_pregnancy_episode_id).toBe("11111111-2222-4333-8444-555555555555");
    expect(err.details?.reconciliation_task).toBe(
      "Open the existing pregnancy episode and reconcile this booking against it.",
    );
  });

  it("prefers the `error` envelope over a `data` envelope when both are present", () => {
    const err = ApiError.fromResponse(422, "corr-both", {
      error: { code: "FROM_ERROR", message: "from error" },
      data: { code: "FROM_DATA", message: "from data" },
    });
    expect(err.code).toBe("FROM_ERROR");
  });

  it("creates from MaternalNearMissController's flat string-code envelope with a sibling message (502 near_miss_unavailable)", () => {
    const err = ApiError.fromResponse(502, "corr-nm", {
      error: "near_miss_unavailable",
      message:
        "The near-miss criteria could not be evaluated. This is not a finding of 'no near-miss'. " +
        "Classify clinically and resubmit when the service returns.",
      meta: { request_id: "req-1", correlation_id: "corr-nm" },
    });
    expect(err.status).toBe(502);
    expect(err.code).toBe("near_miss_unavailable");
    expect(err.message).toContain("not a finding of 'no near-miss'");
  });

  it("captures extra top-level fields (unrecognised_answers, accepted_codes) into details for MaternalNearMissController's 422", () => {
    const err = ApiError.fromResponse(422, "corr-nm2", {
      error: "unrecognised_form_answer",
      message: "These answers could not be read, and were not treated as negatives.",
      unrecognised_answers: ["nearMiss.shock=MAYBE"],
      accepted_codes: ["PRESENT", "ABSENT"],
      meta: { request_id: "req-2", correlation_id: "corr-nm2" },
    });
    expect(err.code).toBe("unrecognised_form_answer");
    expect(err.message).toContain("could not be read");
    expect(err.details?.unrecognised_answers).toEqual(["nearMiss.shock=MAYBE"]);
    expect(err.details?.accepted_codes).toEqual(["PRESENT", "ABSENT"]);
    expect(err.details?.meta).toBeUndefined();
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
