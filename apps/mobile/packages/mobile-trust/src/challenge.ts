/**
 * Trust-challenge recognition for the mobile client.
 *
 * ## Why this exists
 *
 * `mobile-api-client` already had a step-up path: on a 401 it read `body.decision` and, if it
 * equalled `"STEP_UP_REQUIRED"`, notified listeners and threw `StepUpRequiredError`.
 *
 * That check could never be true. Neither wire emits `decision`:
 *
 * - **ext_authz** (`AuthorizeController`) serialises `AuthzResponse`, whose field is `verdict`.
 * - **the BFF** nests the canonical outcome at `error.details.trust_challenge`.
 *
 * So every step-up on mobile fell through to a generic `ApiError`, and the app has never been
 * able to prompt for verification. It was not a disabled feature — it was an unreachable branch.
 *
 * This module reads all three shapes and classifies the result, so a refusal, an actionable
 * challenge and an outage stay three different things on mobile exactly as they do in the shell.
 */

/** The canonical decisions, as carried on the wire. */
export type TrustChallengeDecisionName =
  | "ALLOW"
  | "DENY"
  | "AUTHENTICATION_REQUIRED"
  | "STEP_UP_REQUIRED"
  | "REAUTHENTICATION_REQUIRED"
  | "CONTEXT_REQUIRED"
  | "AUTHORITY_REQUIRED"
  | "CONSENT_REQUIRED"
  | "APPROVAL_REQUIRED"
  | "BREAK_GLASS_AVAILABLE"
  | "RECOVERY_REQUIRED"
  | "TEMPORARILY_UNAVAILABLE";

export type TrustChallengeKind = "actionable" | "refusal" | "unavailable";

export interface TrustChallenge {
  kind: TrustChallengeKind;
  decision: TrustChallengeDecisionName;
  reasonCode?: string;
  userMessageKey?: string;
  requiredAction?: string;
  requiredAssurance?: number;
  allowedAuthenticationMethods: string[];
  contextOptions: string[];
  /** Opaque, single-use. Survives process death, which is the whole point on mobile. */
  continuationReference?: string;
  supportReference?: string;
}

/**
 * Statuses that may carry a challenge.
 *
 * 503 is included for the same reason the shell includes it: the BFF maps
 * TEMPORARILY_UNAVAILABLE to 503 so an outage is not read as a refusal, and a client that only
 * inspected 401/403 would silently drop exactly that distinction. A plain 503 carries no
 * challenge body and is unaffected.
 */
export const TRUST_CHALLENGE_STATUSES = [401, 403, 503] as const;

/**
 * Legacy deny codes that name an actionable outcome.
 *
 * Mirrors `AuthzResponseChallengeAdapter.ACTIONABLE_DENY_CODES` on the Java side. It is an
 * ALLOWLIST: an unrecognised refusal reason stays a refusal, so a new deny code can never
 * silently acquire a call to action in the app.
 */
const ACTIONABLE_DENY_CODES: Record<string, TrustChallengeDecisionName> = {
  CONSENT_REQUIRED: "CONSENT_REQUIRED",
  RECOVERY_REQUIRED: "RECOVERY_REQUIRED",
};

const ACTIONABLE_DECISIONS: ReadonlySet<string> = new Set([
  "AUTHENTICATION_REQUIRED",
  "STEP_UP_REQUIRED",
  "REAUTHENTICATION_REQUIRED",
  "CONTEXT_REQUIRED",
  "AUTHORITY_REQUIRED",
  "CONSENT_REQUIRED",
  "APPROVAL_REQUIRED",
  "BREAK_GLASS_AVAILABLE",
  "RECOVERY_REQUIRED",
]);

const KNOWN_DECISIONS: ReadonlySet<string> = new Set([
  "ALLOW",
  "DENY",
  "TEMPORARILY_UNAVAILABLE",
  ...ACTIONABLE_DECISIONS,
]);

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : [];
}

function text(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function classify(decision: TrustChallengeDecisionName): TrustChallengeKind {
  if (decision === "TEMPORARILY_UNAVAILABLE") return "unavailable";
  return ACTIONABLE_DECISIONS.has(decision) ? "actionable" : "refusal";
}

/** Reads the canonical `TrustChallengeOutcome` shape, wherever the envelope puts it. */
function fromCanonical(node: Record<string, unknown>): TrustChallenge | null {
  const decision = node.decision;
  if (typeof decision !== "string" || !KNOWN_DECISIONS.has(decision)) return null;
  // ALLOW in an error body is not a challenge. Treating it as one would let a malformed or
  // hostile body present an affordance on a request that was refused for another reason.
  if (decision === "ALLOW") return null;

  const name = decision as TrustChallengeDecisionName;
  return {
    kind: classify(name),
    decision: name,
    reasonCode: text(node.reason_code),
    userMessageKey: text(node.user_message_key),
    requiredAction: text(node.required_action),
    requiredAssurance:
      typeof node.required_assurance === "number" ? node.required_assurance : undefined,
    allowedAuthenticationMethods: stringList(node.allowed_authentication_methods),
    contextOptions: stringList(node.context_options),
    continuationReference: text(node.continuation_reference),
    supportReference: text(node.support_reference),
  };
}

/**
 * Reads the ext_authz wire (`AuthzResponse`), whose verdict field is `verdict` — the exact
 * mismatch that made the previous check unreachable.
 */
function fromLegacyVerdict(node: Record<string, unknown>): TrustChallenge | null {
  const verdict = text(node.verdict);
  if (!verdict) return null;

  if (verdict === "STEP_UP_REQUIRED") {
    const requirement = record(node.stepUpRequirement);
    return {
      kind: "actionable",
      decision: "STEP_UP_REQUIRED",
      reasonCode: "STEP_UP_REQUIRED",
      requiredAssurance:
        requirement && typeof requirement.requiredAal === "number"
          ? requirement.requiredAal
          : undefined,
      allowedAuthenticationMethods: stringList(node.stepUpMethods),
      contextOptions: [],
    };
  }

  if (verdict === "DENY") {
    const code = text(node.errorCode);
    const promoted = code ? ACTIONABLE_DENY_CODES[code] : undefined;
    const decision: TrustChallengeDecisionName = promoted ?? "DENY";
    return {
      kind: classify(decision),
      decision,
      reasonCode: code ?? "DENIED",
      // errorMessage is deliberately dropped: it is policy prose written for a log, and has
      // not been through the contract's sensitive-value fence.
      allowedAuthenticationMethods: [],
      contextOptions: [],
    };
  }

  return null;
}

/**
 * Extracts a trust challenge from a response body, or null if it is an ordinary error.
 *
 * Fail-closed by construction: an unrecognised body is NOT a challenge, so nothing gains an
 * affordance by accident. Callers must still treat null as a refusal.
 */
export function parseTrustChallenge(body: unknown): TrustChallenge | null {
  const root = record(body);
  if (!root) return null;

  const error = record(root.error);
  const details = error ? record(error.details) : null;

  const candidates = [
    // The BFF envelope, which is where a challenge actually lives today.
    details ? record(details.trust_challenge) : null,
    error ? record(error.trust_challenge) : null,
    record(root.trust_challenge),
    // A bare canonical outcome, and the legacy ext_authz body.
    root,
    error,
  ];

  for (const candidate of candidates) {
    if (!candidate) continue;
    const canonical = fromCanonical(candidate);
    if (canonical) return canonical;
    const legacy = fromLegacyVerdict(candidate);
    if (legacy) return legacy;
  }
  return null;
}

/** Whether a status may carry a challenge at all. */
export function isTrustChallengeStatus(status: number): boolean {
  return (TRUST_CHALLENGE_STATUSES as readonly number[]).includes(status);
}
