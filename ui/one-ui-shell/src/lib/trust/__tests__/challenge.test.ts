/**
 * Checkpoint 6 — a trust decision must arrive in the browser as a decision, not as an
 * opaque failure; and an ordinary error must NOT be dressed up as a governed decision.
 */
import { describe, expect, it } from "vitest";

import { TRUST_CONTRACT_VERSION_V1 } from "../../../../../../contracts/trust-decision/v1";
import {
  challengeAuthenticationMethods,
  challengeContextOptions,
  isTrustChallengeError,
  parseTrustChallenge,
  trustChallengeCopy,
} from "../challenge";

describe("parseTrustChallenge — canonical bodies", () => {
  it("parses a canonical CONSENT_REQUIRED challenge from a 403 body", () => {
    const parsed = parseTrustChallenge({
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "CONSENT_REQUIRED",
      reason_code: "CONSENT_REQUIRED",
      user_message_key: "trust.consent.required",
      required_action: "OBTAIN_CONSENT",
      support_reference: "SUP-77",
    });

    expect(parsed).not.toBeNull();
    expect(parsed!.kind).toBe("actionable");
    expect(parsed!.decision).toBe("CONSENT_REQUIRED");
    expect(parsed!.outcome.support_reference).toBe("SUP-77");
  });

  it("parses a canonical challenge nested under the BFF error envelope", () => {
    const parsed = parseTrustChallenge({
      error: {
        code: "FORBIDDEN",
        message: "denied",
        details: {
          trust_challenge: {
            contract_version: TRUST_CONTRACT_VERSION_V1,
            decision: "CONTEXT_REQUIRED",
            user_message_key: "trust.context.required",
            context_options: ["FACILITY_A", "FACILITY_B"],
          },
        },
      },
    });

    expect(parsed?.kind).toBe("actionable");
    expect(parsed?.decision).toBe("CONTEXT_REQUIRED");
    expect(challengeContextOptions(parsed!.outcome)).toEqual(["FACILITY_A", "FACILITY_B"]);
  });

  it("classifies DENY as a refusal and TEMPORARILY_UNAVAILABLE as unavailable", () => {
    const deny = parseTrustChallenge({
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "DENY",
      reason_code: "POLICY_DENY",
    });
    const unavailable = parseTrustChallenge({
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "TEMPORARILY_UNAVAILABLE",
    });

    expect(deny?.kind).toBe("refusal");
    expect(unavailable?.kind).toBe("unavailable");
  });

  it("strips the api-client rejection envelope so a thrown error still parses", () => {
    const thrown = {
      status: 403,
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "AUTHORITY_REQUIRED",
    };
    expect(parseTrustChallenge(thrown)?.decision).toBe("AUTHORITY_REQUIRED");
  });

  it("returns an already-attached challenge unchanged", () => {
    const attached = parseTrustChallenge({
      status: 403,
      isTrustChallenge: true,
      trustChallenge: {
        kind: "refusal",
        decision: "DENY",
        outcome: { contract_version: TRUST_CONTRACT_VERSION_V1, decision: "DENY" },
      },
    });
    expect(attached?.kind).toBe("refusal");
  });

  it("refuses a body that claims to be a challenge but fails contract validation", () => {
    // Unknown decision.
    expect(
      parseTrustChallenge({
        contract_version: TRUST_CONTRACT_VERSION_V1,
        decision: "MAYBE",
      }),
    ).toBeNull();

    // Unsafe additional property — the canonical validator's fence must hold here too.
    expect(
      parseTrustChallenge({
        contract_version: TRUST_CONTRACT_VERSION_V1,
        decision: "DENY",
        policy_expression: "input.role == \"admin\"",
      }),
    ).toBeNull();
  });

  it("treats ALLOW inside an error body as incoherent, not as a challenge", () => {
    expect(
      parseTrustChallenge({ contract_version: TRUST_CONTRACT_VERSION_V1, decision: "ALLOW" }),
    ).toBeNull();
  });
});

describe("parseTrustChallenge — legacy trust wire", () => {
  it("bridges the ext_authz CONSENT_REQUIRED 403 that tshepo-authz emits today", () => {
    const parsed = parseTrustChallenge({
      error: "CONSENT_REQUIRED",
      message:
        "This record is protected by the person's consent, and no consent covering this access has been granted yet.",
    });

    expect(parsed?.kind).toBe("actionable");
    expect(parsed?.decision).toBe("CONSENT_REQUIRED");
    expect(parsed?.outcome.user_message_key).toBe("trust.consent.required");
  });

  it("bridges the ext_authz STEP_UP_REQUIRED 401 with only the methods it permits", () => {
    const parsed = parseTrustChallenge({ error: "STEP_UP_REQUIRED", methods: ["MFA", "SMS_OTP"] });

    expect(parsed?.decision).toBe("STEP_UP_REQUIRED");
    expect(challengeAuthenticationMethods(parsed!.outcome)).toEqual(["MFA", "SMS_OTP"]);
  });

  it("keeps an unrecognised ext_authz deny code a DENY (never promoted to actionable)", () => {
    const parsed = parseTrustChallenge({ error: "RBAC_NO_MATCHING_RULE", message: "denied" });

    expect(parsed?.kind).toBe("refusal");
    expect(parsed?.decision).toBe("DENY");
  });

  it("does NOT claim a governed refusal from an ordinary service 403 envelope", () => {
    expect(
      parseTrustChallenge({
        error: { code: "VALIDATION_FAILED", message: "bad request", request_id: "r-1" },
      }),
    ).toBeNull();
    expect(parseTrustChallenge({ error: { code: "NOT_FOUND" } })).toBeNull();
    expect(parseTrustChallenge({ message: "boom" })).toBeNull();
    expect(parseTrustChallenge(null)).toBeNull();
    expect(parseTrustChallenge("forbidden")).toBeNull();
  });

  it("does not treat a free-text error string as a policy code", () => {
    expect(parseTrustChallenge({ error: "Forbidden by upstream proxy" })).toBeNull();
  });

  it("bridges an actionable code carried in the BFF error envelope", () => {
    const parsed = parseTrustChallenge({ error: { code: "CONSENT_REQUIRED", message: "no consent" } });
    expect(parsed?.decision).toBe("CONSENT_REQUIRED");
  });
});

describe("isTrustChallengeError", () => {
  it("recognises only rejections the api-client marked", () => {
    expect(
      isTrustChallengeError({
        status: 403,
        isTrustChallenge: true,
        trustChallenge: { kind: "refusal", decision: "DENY", outcome: {} },
      }),
    ).toBe(true);
    expect(isTrustChallengeError({ status: 403 })).toBe(false);
    expect(isTrustChallengeError(undefined)).toBe(false);
  });
});

describe("trustChallengeCopy", () => {
  it("never falls back to the reason_code when no message key is known", () => {
    const copy = trustChallengeCopy({
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "AUTHORITY_REQUIRED",
      reason_code: "OPA_RULE_7X_NO_LICENCE",
    });

    expect(copy.title).not.toContain("OPA_RULE_7X_NO_LICENCE");
    expect(copy.explanation).not.toContain("OPA_RULE_7X_NO_LICENCE");
    expect(copy.explanation.length).toBeGreaterThan(20);
  });

  it("gives DENY, CONSENT_REQUIRED and TEMPORARILY_UNAVAILABLE different explanations", () => {
    const deny = trustChallengeCopy({
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "DENY",
    });
    const consent = trustChallengeCopy({
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "CONSENT_REQUIRED",
    });
    const unavailable = trustChallengeCopy({
      contract_version: TRUST_CONTRACT_VERSION_V1,
      decision: "TEMPORARILY_UNAVAILABLE",
    });

    expect(new Set([deny.explanation, consent.explanation, unavailable.explanation]).size).toBe(3);
    expect(deny.actionHint).toBeUndefined();
    expect(consent.actionHint).toBeTruthy();
  });
});
