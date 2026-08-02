import { describe, expect, it } from "vitest";
import {
  isTrustChallengeStatus,
  parseTrustChallenge,
  TRUST_CHALLENGE_STATUSES,
} from "../src/challenge";

/**
 * The defect this module fixes is worth restating, because it is the kind that passes review:
 * `mobile-api-client` checked `body.decision === "STEP_UP_REQUIRED"` on a 401, and NEITHER wire
 * emits `decision` — ext_authz emits `verdict`, the BFF nests the outcome under
 * `error.details.trust_challenge`. The branch was unreachable, so mobile has never prompted for
 * verification. The first two tests are the ones that would have caught it.
 */
describe("parseTrustChallenge", () => {
  it("reads the ext_authz wire, whose field is `verdict` and never `decision`", () => {
    const c = parseTrustChallenge({
      verdict: "STEP_UP_REQUIRED",
      stepUpMethods: ["otp", "webauthn"],
      stepUpRequirement: { requiredAal: 3 },
    });

    expect(c?.decision).toBe("STEP_UP_REQUIRED");
    expect(c?.kind).toBe("actionable");
    expect(c?.allowedAuthenticationMethods).toEqual(["otp", "webauthn"]);
    expect(c?.requiredAssurance).toBe(3);
  });

  it("reads the BFF envelope, where the outcome is nested two levels down", () => {
    const c = parseTrustChallenge({
      error: {
        code: "CONSENT_REQUIRED",
        details: {
          trust_challenge: {
            decision: "CONSENT_REQUIRED",
            reason_code: "CONSENT_REQUIRED",
            required_action: "OBTAIN_CONSENT",
            continuation_reference: "cont-xyz",
          },
        },
      },
    });

    expect(c?.decision).toBe("CONSENT_REQUIRED");
    expect(c?.kind).toBe("actionable");
    expect(c?.continuationReference).toBe("cont-xyz");
  });

  it("keeps a refusal a refusal — actionable promotion is an allowlist", () => {
    const c = parseTrustChallenge({ verdict: "DENY", errorCode: "SOME_FUTURE_REFUSAL" });

    expect(c?.decision).toBe("DENY");
    expect(c?.kind).toBe("refusal");
  });

  it("promotes exactly the codes the Java adapter promotes", () => {
    expect(parseTrustChallenge({ verdict: "DENY", errorCode: "CONSENT_REQUIRED" })?.kind).toBe(
      "actionable",
    );
    expect(parseTrustChallenge({ verdict: "DENY", errorCode: "RECOVERY_REQUIRED" })?.kind).toBe(
      "actionable",
    );
  });

  it("classifies an outage as neither a refusal nor an affordance", () => {
    const c = parseTrustChallenge({
      error: { details: { trust_challenge: { decision: "TEMPORARILY_UNAVAILABLE" } } },
    });

    expect(c?.kind).toBe("unavailable");
    expect(c?.kind).not.toBe("refusal");
  });

  it("ALLOW inside an error body is NOT a challenge", () => {
    // A malformed or hostile body must not be able to present an affordance on a request that
    // was refused for some other reason.
    expect(parseTrustChallenge({ error: { details: { trust_challenge: { decision: "ALLOW" } } } }))
      .toBeNull();
  });

  it("an ordinary error body is not a governed refusal", () => {
    expect(parseTrustChallenge({ error: { code: "VALIDATION_FAILED", message: "bad" } })).toBeNull();
    expect(parseTrustChallenge(null)).toBeNull();
    expect(parseTrustChallenge("forbidden")).toBeNull();
    expect(parseTrustChallenge({ decision: "NOT_A_REAL_DECISION" })).toBeNull();
  });

  it("drops policy prose rather than carrying it toward a user", () => {
    // errorMessage is written for a log and has not been through the contract's
    // sensitive-value fence.
    const c = parseTrustChallenge({
      verdict: "DENY",
      errorCode: "NO_MATCHING_RULE",
      errorMessage: "rego rule impilo.authz.deny_detail matched",
    });

    expect(JSON.stringify(c)).not.toContain("rego");
    expect(c?.reasonCode).toBe("NO_MATCHING_RULE");
  });

  it("recognises 503 so an outage is not silently dropped", () => {
    expect(TRUST_CHALLENGE_STATUSES).toEqual([401, 403, 503]);
    expect(isTrustChallengeStatus(503)).toBe(true);
    expect(isTrustChallengeStatus(403)).toBe(true);
    expect(isTrustChallengeStatus(500)).toBe(false);
  });
});
