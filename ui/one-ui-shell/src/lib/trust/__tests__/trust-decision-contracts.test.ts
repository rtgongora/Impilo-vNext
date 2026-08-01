/**
 * TypeScript side of the shared tshepo.trust.v1 fixture conformance suite.
 *
 * The same fixture manifest is validated against the Java records
 * (TrustContractFixturesTest), a real JSON Schema validator, and
 * OpenAPI-compatible models (scripts/guard/validate-trust-decision-fixtures.py).
 */
import { describe, expect, it } from "vitest";

import manifest from "../../../../../../contracts/trust-decision/fixtures/trust-decision-v1.fixtures.json";
import type {
  AsyncExecutionReference,
  AuthenticationAssurance,
  DelegatedHumanContext,
  DelegationChain,
  LawfulBasisDecision,
  TrustAuditEvent,
  TrustChallengeDecision,
  TrustChallengeOutcome,
  TrustDecisionInput,
} from "../../../../../../contracts/trust-decision/v1";
import {
  TrustContractViolation,
  grantsOrdinaryWorkforceAal2Strict,
  isAsyncReferenceExecutable,
  isDelegationExpired,
  rejectClientHeaderPopulation,
  validateAsyncExecutionReference,
  validateAuthenticationAssurance,
  validateDelegatedHumanContext,
  validateDelegationChain,
  validateLawfulBasisDecision,
  validateTrustAuditEvent,
  validateTrustChallengeOutcome,
  validateTrustDecisionInput,
} from "../../../../../../contracts/trust-decision/validate";

type Fixture = {
  name: string;
  type: string;
  rejects?: string[];
  logicExpectation?: string;
  data: unknown;
};

const FIXED_NOW = new Date("2026-08-01T12:00:00Z");

const VALIDATORS: Record<string, (data: never) => void> = {
  TrustChallengeOutcome: validateTrustChallengeOutcome,
  TrustDecisionInput: validateTrustDecisionInput,
  AuthenticationAssurance: validateAuthenticationAssurance,
  AsyncExecutionReference: validateAsyncExecutionReference,
  DelegationChain: validateDelegationChain,
  DelegatedHumanContext: validateDelegatedHumanContext,
  LawfulBasisDecision: validateLawfulBasisDecision,
  TrustAuditEvent: validateTrustAuditEvent,
};

const valid = manifest.valid as Fixture[];
const invalid = manifest.invalid as Fixture[];

function fixture(bucket: Fixture[], name: string): Fixture {
  const found = bucket.find((f) => f.name === name);
  if (!found) throw new Error(`fixture not found: ${name}`);
  return found;
}

describe("tshepo.trust.v1 shared fixtures (TypeScript runtime validation)", () => {
  it("has full coverage inputs", () => {
    expect(valid.length).toBeGreaterThanOrEqual(27);
    expect(invalid.length).toBeGreaterThanOrEqual(17);
  });

  it.each(valid.map((f) => [f.name, f] as const))(
    "accepts valid fixture %s",
    (_name, f) => {
      const validate = VALIDATORS[f.type];
      expect(validate, `validator for ${f.type}`).toBeDefined();
      expect(() => validate(f.data as never)).not.toThrow();
    },
  );

  it.each(invalid.map((f) => [f.name, f] as const))(
    "rejects invalid fixture %s",
    (_name, f) => {
      expect(f.rejects).toContain("ts");
      const validate = VALIDATORS[f.type];
      expect(validate, `validator for ${f.type}`).toBeDefined();
      expect(() => validate(f.data as never)).toThrow(TrustContractViolation);
    },
  );

  it("covers all 12 TrustChallengeOutcome decisions with valid fixtures", () => {
    const decisions = valid
      .filter((f) => f.type === "TrustChallengeOutcome")
      .map((f) => (f.data as TrustChallengeOutcome).decision);
    const expected: TrustChallengeDecision[] = [
      "ALLOW",
      "DENY",
      "AUTHENTICATION_REQUIRED",
      "STEP_UP_REQUIRED",
      "CONTEXT_REQUIRED",
      "AUTHORITY_REQUIRED",
      "CONSENT_REQUIRED",
      "APPROVAL_REQUIRED",
      "BREAK_GLASS_AVAILABLE",
      "REAUTHENTICATION_REQUIRED",
      "RECOVERY_REQUIRED",
      "TEMPORARILY_UNAVAILABLE",
    ];
    expect([...decisions].sort()).toEqual([...expected].sort());
  });

  it("covers the required TrustDecisionInput variants", () => {
    const names = valid.filter((f) => f.type === "TrustDecisionInput").map((f) => f.name);
    for (const required of [
      "input-anonymous-actor",
      "input-authenticated-citizen",
      "input-workforce-with-context-and-authority",
      "input-workload-acting-for-itself",
      "input-workload-acting-for-human",
      "input-with-consent",
      "input-non-consent-lawful-basis",
      "input-constrained-recovery",
      "input-break-glass",
    ]) {
      expect(names).toContain(required);
    }
  });

  it("treats the expired async delegation as structurally valid but not executable", () => {
    const expired = fixture(valid, "async-delegation-expired").data as AsyncExecutionReference;
    expect(isAsyncReferenceExecutable(expired, FIXED_NOW)).toBe(false);
    const live = fixture(valid, "async-delegation-executable").data as AsyncExecutionReference;
    expect(isAsyncReferenceExecutable(live, FIXED_NOW)).toBe(true);
  });

  it("flags the expired delegation hop at the fixed evaluation instant", () => {
    const hop = fixture(valid, "expired-delegation-hop").data as DelegatedHumanContext;
    expect(isDelegationExpired(hop, FIXED_NOW)).toBe(true);
  });

  it("never grants ordinary workforce AAL2 for constrained recovery", () => {
    const recovery = fixture(valid, "assurance-constrained-recovery")
      .data as AuthenticationAssurance;
    expect(recovery.aal).toBe(2);
    expect(grantsOrdinaryWorkforceAal2Strict(recovery)).toBe(false);
  });

  it("keeps workload and human identity separate in the workload-for-human variant", () => {
    const input = fixture(valid, "input-workload-acting-for-human").data as TrustDecisionInput;
    expect(input.callingWorkload.workloadId).toBe("experience-bff");
    expect(input.humanSubject?.healthId).toBe("HID-PRV-004410");
    expect(input.callingWorkload).not.toHaveProperty("healthId");
    expect(input.delegationChain?.hops[0]?.delegationDepth).toBe(0);
  });

  it("keeps consent and lawful basis as separate models", () => {
    const consent = fixture(valid, "input-with-consent").data as TrustDecisionInput;
    expect(consent.consentDecision?.permitted).toBe(true);
    expect(consent.lawfulBasisDecision).toBeUndefined();

    const lawful = fixture(valid, "input-non-consent-lawful-basis").data as TrustDecisionInput;
    expect(lawful.lawfulBasisDecision?.basisType).toBe("PUBLIC_HEALTH_AUTHORITY");
    expect(lawful.consentDecision).toBeUndefined();

    const explicit = fixture(invalid, "lawful-basis-explicit-consent")
      .data as LawfulBasisDecision;
    expect(() => validateLawfulBasisDecision(explicit)).toThrow(TrustContractViolation);
  });

  it("rejects client-header population of authoritative fields", () => {
    expect(() =>
      rejectClientHeaderPopulation({ "X-Assurance-Level": "3" }),
    ).toThrow(TrustContractViolation);
    expect(() => rejectClientHeaderPopulation({})).not.toThrow();
  });

  it("validates the non-increasing delegation chain fixture end to end", () => {
    const chain = fixture(valid, "delegation-chain-two-hops-non-increasing")
      .data as DelegationChain;
    expect(() => validateDelegationChain(chain)).not.toThrow();
    expect(chain.hops[1].allowedActions.every((a) => chain.hops[0].allowedActions.includes(a))).toBe(
      true,
    );
  });

  it("keeps the safe audit fixture free of secret and clinical material", () => {
    const audit = fixture(valid, "audit-event-safe").data as TrustAuditEvent;
    expect(() => validateTrustAuditEvent(audit)).not.toThrow();
    expect(JSON.stringify(audit)).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}\.|Bearer |-----BEGIN/);
  });
});
