/**
 * Runtime validation for tshepo.trust.v1 contracts — TypeScript mirror of the
 * Java record compact-constructor semantics in `libs/tshepo-contracts/.../v1`.
 *
 * These validators are the TS side of the shared fixture conformance suite.
 */
import {
  TRUST_CONTRACT_VERSION_V1,
  type AsyncExecutionReference,
  type AuthenticationAssurance,
  type DelegatedHumanContext,
  type DelegationChain,
  type LawfulBasisDecision,
  type TrustAuditEvent,
  type TrustChallengeDecision,
  type TrustChallengeOutcome,
  type TrustDecisionInput,
} from "./v1";

export class TrustContractViolation extends Error {
  constructor(message: string) {
    super(message);
    this.name = "TrustContractViolation";
  }
}

const CHALLENGE_DECISIONS: readonly TrustChallengeDecision[] = [
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
] as const;

const OPERATING_MODES = [
  "NORMAL",
  "DEGRADED",
  "OFFLINE",
  "RECOVERY",
  "BREAK_GLASS",
  "READ_ONLY_EMERGENCY",
] as const;

const RECOVERY_STATES = ["NONE", "CONSTRAINED_RECOVERY"] as const;

const NON_CONSENT_BASES = [
  "DIRECT_CARE_RELATIONSHIP",
  "STATUTORY_AUTHORITY",
  "PUBLIC_HEALTH_AUTHORITY",
  "EMERGENCY_BREAK_GLASS",
  "OTHER_GOVERNED",
] as const;

/** Matches the Java TOKEN_LIKE / FORBIDDEN patterns. */
const TOKEN_LIKE = /(eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}|bearer\s+|access_token|refresh_token)/i;
const AUDIT_FORBIDDEN =
  /(eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|bearer\s+|password|client_secret|private[_-]?key|recovery[_-]?code|otp\b|refresh_token|access_token|-----BEGIN)/i;
const CHALLENGE_FORBIDDEN =
  /(eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|bearer\s+|password|client_secret|private[_-]?key|recovery[_-]?code|policy_expr|rego\b|deny_detail)/i;
const CLINICAL_KEY = /(clinical|observation|fhir|diagnosis|medication)/i;

const DATE_TIME =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$/;

function fail(message: string): never {
  throw new TrustContractViolation(message);
}

function requireV1(version: unknown, where: string): void {
  if (version !== TRUST_CONTRACT_VERSION_V1) {
    fail(`${where}: unsupported contract version ${String(version)}`);
  }
}

function requireDateTime(value: unknown, where: string): void {
  if (value === undefined || value === null) return;
  if (typeof value !== "string" || !DATE_TIME.test(value)) {
    fail(`${where}: not an RFC3339 date-time: ${String(value)}`);
  }
}

export function validateAuthenticationAssurance(a: AuthenticationAssurance): void {
  requireV1(a.contractVersion, "AuthenticationAssurance");
  if (!Number.isInteger(a.aal) || a.aal < 0 || a.aal > 3) {
    fail("AuthenticationAssurance: aal must be an integer between 0 and 3");
  }
  if (!Array.isArray(a.amr)) fail("AuthenticationAssurance: amr must be an array");
  if (!RECOVERY_STATES.includes(a.recoveryState)) {
    fail(`AuthenticationAssurance: unknown recoveryState ${String(a.recoveryState)}`);
  }
  requireDateTime(a.authenticationTime, "AuthenticationAssurance.authenticationTime");
  requireDateTime(a.stepUpTime, "AuthenticationAssurance.stepUpTime");
}

export function grantsOrdinaryWorkforceAal2Strict(a: AuthenticationAssurance): boolean {
  validateAuthenticationAssurance(a);
  return a.aal >= 2 && a.recoveryState === "NONE";
}

export function validateTrustChallengeOutcome(o: TrustChallengeOutcome): void {
  requireV1(o.contract_version, "TrustChallengeOutcome");
  if (!CHALLENGE_DECISIONS.includes(o.decision)) {
    fail(`TrustChallengeOutcome: unknown decision ${String(o.decision)}`);
  }
  const allowedKeys = new Set([
    "contract_version",
    "decision",
    "reason_code",
    "user_message_key",
    "required_action",
    "required_assurance",
    "allowed_authentication_methods",
    "context_options",
    "consent_request_reference",
    "approval_reference",
    "continuation_reference",
    "decision_id",
    "policy_version",
    "expires_at",
    "support_reference",
  ]);
  // Declared before the additional-property scan so both loops share one widening. The scan
  // previously used a direct `o as Record<string, unknown>`, which TS rejects because
  // TrustChallengeOutcome has no index signature — the same widening two lines below was already
  // written correctly as a double cast.
  const record = o as unknown as Record<string, unknown>;
  for (const key of Object.keys(record)) {
    if (!allowedKeys.has(key)) {
      fail(`TrustChallengeOutcome: unsafe additional property '${key}'`);
    }
  }
  for (const key of allowedKeys) {
    const value = record[key];
    if (typeof value === "string" && CHALLENGE_FORBIDDEN.test(value)) {
      fail(`TrustChallengeOutcome: sensitive material in '${key}'`);
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        if (typeof item === "string" && CHALLENGE_FORBIDDEN.test(item)) {
          fail(`TrustChallengeOutcome: sensitive material in '${key}[]'`);
        }
      }
    }
  }
  if (
    o.required_assurance !== undefined &&
    (!Number.isInteger(o.required_assurance) ||
      o.required_assurance < 0 ||
      o.required_assurance > 3)
  ) {
    fail("TrustChallengeOutcome: required_assurance must be 0..3");
  }
  requireDateTime(o.expires_at, "TrustChallengeOutcome.expires_at");
}

export function validateDelegatedHumanContext(d: DelegatedHumanContext): void {
  requireV1(d.contractVersion, "DelegatedHumanContext");
  if (!d.humanSubject || typeof d.humanSubject.healthId !== "string" || !d.humanSubject.healthId) {
    fail("DelegatedHumanContext: humanSubject.healthId is required");
  }
  if (!Number.isInteger(d.delegationDepth) || d.delegationDepth < 0) {
    fail("DelegatedHumanContext: delegationDepth must be >= 0");
  }
  requireDateTime(d.expiresAt, "DelegatedHumanContext.expiresAt");
  if (d.authenticationAssurance) validateAuthenticationAssurance(d.authenticationAssurance);
}

function subset(candidate: string[], parent: string[]): boolean {
  if (!candidate || candidate.length === 0) return true;
  if (!parent || parent.length === 0) return false;
  return candidate.every((c) => parent.includes(c));
}

/** Mirror of Java DelegatedHumanContext.permitsDownstream. */
export function permitsDownstream(
  parent: DelegatedHumanContext,
  child: DelegatedHumanContext,
): boolean {
  if (!child) return false;
  if (child.delegationDepth < parent.delegationDepth) return false;
  if (child.delegationDepth > parent.delegationDepth + 1) return false;
  if (!subset(child.allowedActions, parent.allowedActions)) return false;
  if (!subset(child.allowedScopes, parent.allowedScopes)) return false;
  if (
    parent.expiresAt &&
    child.expiresAt &&
    new Date(child.expiresAt).getTime() > new Date(parent.expiresAt).getTime()
  ) {
    return false;
  }
  if (parent.authenticationAssurance && child.authenticationAssurance) {
    if (child.authenticationAssurance.aal > parent.authenticationAssurance.aal) return false;
    if (
      parent.authenticationAssurance.recoveryState === "CONSTRAINED_RECOVERY" &&
      child.authenticationAssurance.recoveryState !== "CONSTRAINED_RECOVERY"
    ) {
      return false;
    }
  }
  return true;
}

export function validateDelegationChain(chain: DelegationChain): void {
  requireV1(chain.contractVersion, "DelegationChain");
  if (!Number.isInteger(chain.maxDepth) || chain.maxDepth < 0) {
    fail("DelegationChain: maxDepth must be >= 0");
  }
  if (chain.hops.length > chain.maxDepth) {
    fail("DelegationChain: chain exceeds maxDepth");
  }
  chain.hops.forEach(validateDelegatedHumanContext);
  for (let i = 1; i < chain.hops.length; i++) {
    if (!permitsDownstream(chain.hops[i - 1], chain.hops[i])) {
      fail(`DelegationChain: hop ${i} increases privilege relative to predecessor`);
    }
  }
}

export function isDelegationExpired(d: DelegatedHumanContext, now: Date): boolean {
  validateDelegatedHumanContext(d);
  return !!d.expiresAt && new Date(d.expiresAt).getTime() < now.getTime();
}

export function validateAsyncExecutionReference(ref: AsyncExecutionReference): void {
  requireV1(ref.contractVersion, "AsyncExecutionReference");
  if (typeof ref.referenceId !== "string" || !ref.referenceId) {
    fail("AsyncExecutionReference: referenceId is required");
  }
  for (const value of [ref.referenceId, ...ref.allowedActions, ...ref.allowedScopes]) {
    if (TOKEN_LIKE.test(value)) {
      fail("AsyncExecutionReference: must not carry access-token material");
    }
  }
  if (!Number.isInteger(ref.delegationDepth) || ref.delegationDepth < 0) {
    fail("AsyncExecutionReference: delegationDepth must be >= 0");
  }
  requireDateTime(ref.expiresAt, "AsyncExecutionReference.expiresAt");
}

export function isAsyncReferenceExecutable(ref: AsyncExecutionReference, now: Date): boolean {
  validateAsyncExecutionReference(ref);
  if (ref.revoked) return false;
  return !ref.expiresAt || new Date(ref.expiresAt).getTime() >= now.getTime();
}

export function validateLawfulBasisDecision(d: LawfulBasisDecision): void {
  requireV1(d.contractVersion, "LawfulBasisDecision");
  if (!(NON_CONSENT_BASES as readonly string[]).includes(d.basisType)) {
    fail(
      `LawfulBasisDecision: basisType '${String(d.basisType)}' invalid — explicit consent is a ConsentDecision`,
    );
  }
}

export function validateTrustAuditEvent(e: TrustAuditEvent): void {
  requireV1(e.contractVersion, "TrustAuditEvent");
  if (!e.eventId) fail("TrustAuditEvent: eventId is required");
  if (!e.timestamp) fail("TrustAuditEvent: timestamp is required");
  requireDateTime(e.timestamp, "TrustAuditEvent.timestamp");
  const record = e as unknown as Record<string, unknown>;
  for (const [key, value] of Object.entries(record)) {
    if (typeof value === "string" && AUDIT_FORBIDDEN.test(value)) {
      fail(`TrustAuditEvent: forbidden secret material in '${key}'`);
    }
  }
  for (const [key, value] of Object.entries(e.attributes ?? {})) {
    if (AUDIT_FORBIDDEN.test(key) || AUDIT_FORBIDDEN.test(value)) {
      fail(`TrustAuditEvent: forbidden secret material in attribute '${key}'`);
    }
    if (CLINICAL_KEY.test(key)) {
      fail(`TrustAuditEvent: clinical payload key '${key}' not allowed`);
    }
  }
}

export function validateTrustDecisionInput(input: TrustDecisionInput): void {
  requireV1(input.contractVersion, "TrustDecisionInput");
  if (!input.callingWorkload || !input.callingWorkload.workloadId) {
    fail("TrustDecisionInput: callingWorkload.workloadId is required");
  }
  if (!input.destination || !input.destination.destinationServiceId) {
    fail("TrustDecisionInput: destination.destinationServiceId is required");
  }
  if (!(OPERATING_MODES as readonly string[]).includes(input.operatingMode)) {
    fail(`TrustDecisionInput: unknown operatingMode ${String(input.operatingMode)}`);
  }
  if (input.authenticationAssurance) validateAuthenticationAssurance(input.authenticationAssurance);
  if (input.identityAssurance) {
    requireV1(input.identityAssurance.contractVersion, "IdentityAssurance");
    const ial = input.identityAssurance.ial;
    if (!Number.isInteger(ial) || ial < 0 || ial > 4) {
      fail("IdentityAssurance: ial must be 0..4");
    }
  }
  if (input.lawfulBasisDecision) validateLawfulBasisDecision(input.lawfulBasisDecision);
  if (input.delegationChain) validateDelegationChain(input.delegationChain);
}

/**
 * Guard mirror of Java TrustDecisionInput.rejectClientHeaderPopulation: authoritative
 * fields must never be assembled from raw client-supplied headers.
 */
export function rejectClientHeaderPopulation(clientHeaders: Record<string, string>): void {
  if (clientHeaders && Object.keys(clientHeaders).length > 0) {
    fail(
      "TrustDecisionInput authoritative fields must not be populated from unvalidated client headers",
    );
  }
}
