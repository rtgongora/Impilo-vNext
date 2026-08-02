import { describe, expect, it } from "vitest";
import { parseTrustChallenge } from "@impilo/mobile-trust";
import { ApiError } from "../src/errors";

/**
 * Guards the wire mismatch that made mobile's step-up branch unreachable.
 *
 * `client.ts` checked `body.decision === "STEP_UP_REQUIRED"` on a 401. Neither producer emits
 * `decision`: ext_authz serialises `AuthzResponse`, whose field is `verdict`, and the BFF nests
 * the canonical outcome at `error.details.trust_challenge`. The condition was therefore never
 * true, and the app has never shown a verification prompt for a real challenge.
 *
 * These tests pin the two producer shapes as data. If a producer changes its wire, they fail
 * here rather than silently restoring the dead branch.
 */
describe("trust challenge recognition on the real producer wires", () => {
  it("the ext_authz body a 401 actually carries is recognised", () => {
    // Exactly what AuthorizeController returns for STEP_UP_REQUIRED: an AuthzResponse.
    const extAuthzBody = {
      verdict: "STEP_UP_REQUIRED",
      obligations: null,
      errorCode: null,
      errorMessage: null,
      stepUpMethods: ["otp"],
      riskScore: 40,
      stepUpRequirement: { requiredAal: 2 },
    };

    expect(
      (extAuthzBody as Record<string, unknown>).decision,
      "if a producer ever adds `decision`, the original check was not the bug we think it was",
    ).toBeUndefined();
    expect(parseTrustChallenge(extAuthzBody)?.decision).toBe("STEP_UP_REQUIRED");
  });

  it("the BFF envelope a 403 actually carries is recognised", () => {
    const bffBody = {
      error: {
        code: "CONSENT_REQUIRED",
        message: "trust.consent.required",
        details: {
          trust_challenge: {
            contract_version: "tshepo.trust.v1",
            decision: "CONSENT_REQUIRED",
            reason_code: "CONSENT_REQUIRED",
            user_message_key: "trust.consent.required",
            required_action: "OBTAIN_CONSENT",
            continuation_reference: "cont-abc",
          },
        },
        request_id: "r-1",
        correlation_id: "c-1",
      },
    };

    const challenge = parseTrustChallenge(bffBody);
    expect(challenge?.decision).toBe("CONSENT_REQUIRED");
    expect(challenge?.continuationReference).toBe("cont-abc");
  });

  it("ApiError.withTrustChallenge carries the decision without changing its own contract", () => {
    const challenge = parseTrustChallenge({
      error: {
        details: {
          trust_challenge: {
            decision: "CONSENT_REQUIRED",
            reason_code: "CONSENT_REQUIRED",
            user_message_key: "trust.consent.required",
          },
        },
      },
    })!;

    const error = ApiError.withTrustChallenge(403, "corr-1", {}, challenge);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(403);
    expect(error.correlationId).toBe("corr-1");
    // The reason code is a better code than HTTP_403, and the message is a KEY, not prose.
    expect(error.code).toBe("CONSENT_REQUIRED");
    expect(error.message).toBe("trust.consent.required");
    expect(error.trustChallenge?.kind).toBe("actionable");
  });

  it("an ordinary error still produces an ApiError with no challenge attached", () => {
    // A parser that saw a challenge everywhere would satisfy every test above.
    expect(parseTrustChallenge({ error: { code: "VALIDATION_FAILED" } })).toBeNull();
    expect(ApiError.fromResponse(403, "c-1", { error: { code: "VALIDATION_FAILED" } })
      .trustChallenge).toBeUndefined();
  });

  it("an outage is not reported to the app as a refusal", () => {
    const challenge = parseTrustChallenge({
      error: { details: { trust_challenge: { decision: "TEMPORARILY_UNAVAILABLE" } } },
    })!;

    expect(challenge.kind).toBe("unavailable");
    expect(ApiError.withTrustChallenge(503, "c-1", {}, challenge).trustChallenge?.kind)
      .toBe("unavailable");
  });
});
