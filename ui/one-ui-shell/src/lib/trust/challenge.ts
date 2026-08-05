/**
 * Trust challenge recognition for the web shell (Checkpoint 6).
 *
 * The defect this closes: `api-client.ts` handled HTTP 401 only, so an authorization
 * outcome arrived in the browser as an opaque failure — "you may not do this ever",
 * "here is what you must do first", and "the trust plane is briefly unavailable" were
 * all indistinguishable from a network blip.
 *
 * This module parses a canonical {@link TrustChallengeOutcome} out of an HTTP error body
 * (401 or 403) and classifies it into three kinds that a user experience must never
 * conflate:
 *
 *   - `refusal`     — DENY. No action available to this user changes the answer.
 *   - `actionable`  — a challenge with a next step (step-up, consent, context, authority…).
 *   - `unavailable` — TEMPORARILY_UNAVAILABLE. Nothing is wrong with the user's permissions.
 *
 * The canonical types are IMPORTED from `contracts/trust-decision/v1` and never re-declared
 * here (see `scripts/guard/check-trust-decision-contracts.sh`). Bodies that claim to be a
 * challenge but fail `validateTrustChallengeOutcome` are treated as NOT a challenge: a
 * malformed trust body must not be dressed up as a governed decision.
 */

import { TRUST_CONTRACT_VERSION_V1 } from "../../../../../contracts/trust-decision/v1";
// Inline `type X` imports are avoided on purpose: the canonical-contract guard reads them as a
// local re-declaration of the trust types. See scripts/guard/check-trust-decision-contracts.sh.
import type { TrustChallengeDecision, TrustChallengeOutcome } from "../../../../../contracts/trust-decision/v1";
import { validateTrustChallengeOutcome } from "../../../../../contracts/trust-decision/validate";

export type { TrustChallengeDecision, TrustChallengeOutcome };

/**
 * HTTP statuses on which the trust plane can deliver a challenge.
 *
 * 503 belongs here: the BFF maps TEMPORARILY_UNAVAILABLE to 503 exactly so that "we could not
 * ask" is not read as "you may not". An ordinary 503 carries no challenge body and is unaffected.
 */
export const TRUST_CHALLENGE_STATUSES: readonly number[] = [401, 403, 503];

/** Decisions that name a next step the user (or someone they can ask) can actually take. */
export type ActionableTrustDecision =
  | "AUTHENTICATION_REQUIRED"
  | "STEP_UP_REQUIRED"
  | "CONTEXT_REQUIRED"
  | "AUTHORITY_REQUIRED"
  | "CONSENT_REQUIRED"
  | "APPROVAL_REQUIRED"
  | "BREAK_GLASS_AVAILABLE"
  | "REAUTHENTICATION_REQUIRED"
  | "RECOVERY_REQUIRED";

const ACTIONABLE_DECISIONS: readonly ActionableTrustDecision[] = [
  "AUTHENTICATION_REQUIRED",
  "STEP_UP_REQUIRED",
  "CONTEXT_REQUIRED",
  "AUTHORITY_REQUIRED",
  "CONSENT_REQUIRED",
  "APPROVAL_REQUIRED",
  "BREAK_GLASS_AVAILABLE",
  "REAUTHENTICATION_REQUIRED",
  "RECOVERY_REQUIRED",
] as const;

/** Discriminated result — callers branch on `kind`, then narrow on `decision`. */
export type TrustChallenge =
  | { kind: "actionable"; decision: ActionableTrustDecision; outcome: TrustChallengeOutcome }
  | { kind: "refusal"; decision: "DENY"; outcome: TrustChallengeOutcome }
  | { kind: "unavailable"; decision: "TEMPORARILY_UNAVAILABLE"; outcome: TrustChallengeOutcome };

/** An api-client rejection that carries a recognised trust challenge. */
export interface TrustChallengeApiError {
  status: number;
  isTrustChallenge: true;
  trustChallenge: TrustChallenge;
}

/**
 * Keys the api-client itself adds when it turns a response body into a rejection.
 * Removing them at the root restores the wire body so it can be validated as-is
 * (the canonical validator rejects unknown properties, and that fence is worth keeping).
 */
const API_CLIENT_ENVELOPE_KEYS = new Set(["status", "isTrustChallenge", "trustChallenge"]);

/** A machine code, not a sentence — the shape tshepo/ext_authz puts in the legacy `error` field. */
const LEGACY_CODE = /^[A-Z][A-Z0-9_]{2,63}$/;

/**
 * Legacy deny codes that name an ACTIONABLE outcome rather than a refusal.
 *
 * Mirrors `AuthzResponseChallengeAdapter.ACTIONABLE_DENY_CODES` in
 * `libs/tshepo-contracts` — deliberately an allowlist, so a new refusal reason can never be
 * silently promoted into something the UI invites the user to act on.
 */
const LEGACY_ACTIONABLE_CODES: Record<
  string,
  { decision: ActionableTrustDecision; messageKey: string; action: string }
> = {
  CONSENT_REQUIRED: {
    decision: "CONSENT_REQUIRED",
    messageKey: "trust.consent.required",
    action: "OBTAIN_CONSENT",
  },
  RECOVERY_REQUIRED: {
    decision: "RECOVERY_REQUIRED",
    messageKey: "trust.recovery.required",
    action: "COMPLETE_RECOVERY",
  },
};

const LEGACY_STEP_UP_CODE = "STEP_UP_REQUIRED";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === "string") : [];
}

function classify(outcome: TrustChallengeOutcome): TrustChallenge | null {
  if (outcome.decision === "DENY") {
    return { kind: "refusal", decision: "DENY", outcome };
  }
  if (outcome.decision === "TEMPORARILY_UNAVAILABLE") {
    return { kind: "unavailable", decision: "TEMPORARILY_UNAVAILABLE", outcome };
  }
  if ((ACTIONABLE_DECISIONS as readonly string[]).includes(outcome.decision)) {
    return {
      kind: "actionable",
      decision: outcome.decision as ActionableTrustDecision,
      outcome,
    };
  }
  // ALLOW (or anything unknown) inside an HTTP error body is incoherent. Fail closed to
  // "not a challenge" rather than inventing a decision the trust plane did not make.
  return null;
}

/** Validate a candidate as the canonical contract; a violation means "not a challenge". */
function acceptCanonical(candidate: Record<string, unknown>): TrustChallenge | null {
  if (candidate.contract_version !== TRUST_CONTRACT_VERSION_V1) return null;
  const outcome = candidate as unknown as TrustChallengeOutcome;
  try {
    validateTrustChallengeOutcome(outcome);
  } catch {
    return null;
  }
  return classify(outcome);
}

function legacyOutcome(
  decision: TrustChallengeDecision,
  reasonCode: string,
  userMessageKey: string,
  extra: Partial<TrustChallengeOutcome> = {},
): TrustChallengeOutcome {
  return {
    contract_version: TRUST_CONTRACT_VERSION_V1,
    decision,
    reason_code: reasonCode,
    user_message_key: userMessageKey,
    ...extra,
  };
}

/**
 * Bridge the legacy trust wire into the canonical shape.
 *
 * Two legacy shapes exist today and only these two are bridged:
 *
 *  1. ext_authz denial — `{"error":"<CODE>","message":"…"}` (a STRING `error`, i.e. a code, not
 *     the `{error:{code,…}}` envelope every other service uses). This IS the trust plane
 *     speaking, so the full legacy mapping applies: allowlisted codes become actionable
 *     challenges, STEP_UP_REQUIRED becomes a step-up, everything else stays a DENY.
 *  2. legacy AuthzResponse fields (`decision` / `verdict` / `errorCode` + `stepUpMethods`), and
 *     the BFF `{error:{code}}` envelope — for these, ONLY the actionable allowlist is bridged.
 *     An arbitrary service 403 is not evidence of a trust-plane refusal, so it stays a plain
 *     error rather than claiming a governed DENY.
 *
 * The legacy `message` free text is deliberately NOT carried over: the canonical contract has
 * no free-text field, and policy prose has not been through the challenge-safety fence.
 */
function acceptLegacy(body: Record<string, unknown>): TrustChallenge | null {
  const methods = [
    ...stringArray(body.methods),
    ...stringArray(body.stepUpMethods),
    ...stringArray(body.step_up_methods),
  ];

  const extAuthzCode =
    typeof body.error === "string" && LEGACY_CODE.test(body.error) ? body.error : null;

  const envelope = isRecord(body.error) ? body.error : null;
  const softCode = [
    typeof body.decision === "string" ? body.decision : null,
    typeof body.verdict === "string" ? body.verdict : null,
    typeof body.errorCode === "string" ? body.errorCode : null,
    envelope && typeof envelope.code === "string" ? envelope.code : null,
  ].find((code): code is string => !!code && LEGACY_CODE.test(code));

  const code = extAuthzCode ?? softCode ?? null;
  if (!code) return null;

  if (code === LEGACY_STEP_UP_CODE) {
    return classify(
      legacyOutcome("STEP_UP_REQUIRED", code, "trust.step_up.required", {
        required_action: "STEP_UP",
        allowed_authentication_methods: methods,
      }),
    );
  }

  const actionable = LEGACY_ACTIONABLE_CODES[code];
  if (actionable) {
    return classify(
      legacyOutcome(actionable.decision, code, actionable.messageKey, {
        required_action: actionable.action,
      }),
    );
  }

  // Unrecognised code: only the ext_authz wire is trusted to mean "the trust plane refused".
  if (!extAuthzCode) return null;
  return classify(legacyOutcome("DENY", code, "trust.deny.generic"));
}

/** Candidate locations a canonical challenge can occupy inside an error body. */
function canonicalCandidates(body: Record<string, unknown>): Record<string, unknown>[] {
  const nested: unknown[] = [
    body.trust_challenge,
    body.trustChallenge,
    body.challenge,
    isRecord(body.error) ? body.error.trust_challenge : undefined,
    isRecord(body.error) && isRecord(body.error.details)
      ? body.error.details.trust_challenge
      : undefined,
    isRecord(body.error) && isRecord(body.error.details)
      ? body.error.details.trustChallenge
      : undefined,
    isRecord(body.error) && isRecord(body.error.details) ? body.error.details.challenge : undefined,
    isRecord(body.details) ? body.details.trust_challenge : undefined,
  ];
  return nested.filter(isRecord);
}

/**
 * Parse a trust challenge out of an HTTP error body or an api-client rejection.
 * Returns `null` when the body is an ordinary error — an opaque failure must stay an
 * opaque failure rather than being dressed up as a governed decision.
 */
export function parseTrustChallenge(source: unknown): TrustChallenge | null {
  if (!isRecord(source)) return null;

  // Already parsed by the api-client.
  const attached = source.trustChallenge;
  if (isRecord(attached) && typeof attached.kind === "string" && isRecord(attached.outcome)) {
    return attached as unknown as TrustChallenge;
  }

  const root: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(source)) {
    if (!API_CLIENT_ENVELOPE_KEYS.has(key)) root[key] = value;
  }

  const rootCanonical = acceptCanonical(root);
  if (rootCanonical) return rootCanonical;

  for (const candidate of canonicalCandidates(root)) {
    const parsed = acceptCanonical(candidate);
    if (parsed) return parsed;
  }

  return acceptLegacy(root);
}

/** True when an api-client rejection carries a recognised trust challenge. */
export function isTrustChallengeError(err: unknown): err is TrustChallengeApiError {
  return isRecord(err) && err.isTrustChallenge === true && isRecord(err.trustChallenge);
}

/** Read a challenge off a rejection (attached by the api-client, or parsed from the body). */
export function readTrustChallenge(err: unknown): TrustChallenge | null {
  return parseTrustChallenge(err);
}

/** Authentication methods the outcome permits — the ONLY ones a prompt may offer. */
export function challengeAuthenticationMethods(outcome: TrustChallengeOutcome): string[] {
  return stringArray(outcome.allowed_authentication_methods);
}

/** Context options the outcome permits — the ONLY contexts a prompt may offer. */
export function challengeContextOptions(outcome: TrustChallengeOutcome): string[] {
  return stringArray(outcome.context_options);
}

export interface TrustChallengeCopy {
  /** Short heading — what happened. */
  title: string;
  /** WHY the action needs something, in plain language. Never a reason_code. */
  explanation: string;
  /** What to do next, when there is something to do. */
  actionHint?: string;
}

/**
 * Human copy for a challenge.
 *
 * Resolution order is `user_message_key` → decision → kind default. A `reason_code` is a
 * machine code for logs and support, and is NEVER a fallback here: showing "OPA_RULE_7X" to
 * a person is the same dead end as showing nothing.
 */
const MESSAGE_KEY_COPY: Record<string, TrustChallengeCopy> = {
  "trust.step_up.required": {
    title: "Confirm it's really you",
    explanation:
      "This action is protected. Verifying your identity again before it goes ahead keeps the record trustworthy.",
    actionHint: "Choose how you'd like to verify.",
  },
  "trust.consent.required": {
    title: "Consent hasn't been given yet",
    explanation:
      "This record is protected by the person's consent, and no consent covering this access has been granted yet. Nobody has refused you — nobody has been asked.",
    actionHint: "Ask the person to grant consent, or record another lawful basis for access.",
  },
  "trust.recovery.required": {
    title: "Finish recovering your account",
    explanation:
      "Your sign-in is in a limited recovery state, so this action is held back until recovery is complete.",
    actionHint: "Complete account recovery, then try again.",
  },
  "trust.deny.generic": {
    title: "You don't have access to this",
    explanation:
      "This action isn't permitted for you in this context. This isn't something you can unlock by verifying or by switching context.",
  },
  "trust.deny.missing_verdict": {
    title: "You don't have access to this",
    explanation:
      "This action couldn't be authorised, so it wasn't carried out. Nothing has changed.",
  },
};

const DECISION_COPY: Record<TrustChallengeDecision, TrustChallengeCopy> = {
  ALLOW: {
    title: "Allowed",
    explanation: "This action is permitted.",
  },
  DENY: MESSAGE_KEY_COPY["trust.deny.generic"],
  AUTHENTICATION_REQUIRED: {
    title: "Sign in to continue",
    explanation: "This action needs you to be signed in.",
    actionHint: "Sign in, then try again.",
  },
  STEP_UP_REQUIRED: MESSAGE_KEY_COPY["trust.step_up.required"],
  CONTEXT_REQUIRED: {
    title: "Choose where you're working",
    explanation:
      "This action has to be recorded against a place of work. Your session doesn't have one set for it yet.",
    actionHint: "Pick the facility or workspace you're working in.",
  },
  AUTHORITY_REQUIRED: {
    title: "This needs a professional role",
    explanation:
      "This action can only be carried out under an active professional authority — a valid registration and an appointment that covers it.",
    actionHint: "Activate the professional role you're practising under, then try again.",
  },
  CONSENT_REQUIRED: MESSAGE_KEY_COPY["trust.consent.required"],
  APPROVAL_REQUIRED: {
    title: "Someone needs to approve this",
    explanation: "This action is held until it's approved by someone with the authority to allow it.",
    actionHint: "Request approval, then come back to it.",
  },
  BREAK_GLASS_AVAILABLE: {
    title: "Emergency access is available",
    explanation:
      "You don't have ordinary access to this, but emergency access can be taken. Every emergency access is recorded and reviewed.",
    actionHint: "Use emergency access only if care depends on it right now.",
  },
  REAUTHENTICATION_REQUIRED: {
    title: "Sign in again to continue",
    explanation:
      "It's been a while since you signed in. Confirming your identity again keeps this action attributable to you.",
    actionHint: "Choose how you'd like to verify.",
  },
  RECOVERY_REQUIRED: MESSAGE_KEY_COPY["trust.recovery.required"],
  TEMPORARILY_UNAVAILABLE: {
    title: "Can't check this right now",
    explanation:
      "There's nothing wrong with your permissions — the service that checks them is briefly unavailable, so nothing was carried out.",
    actionHint: "Try again in a moment.",
  },
};

/**
 * Which of the three kinds each known message key speaks for.
 *
 * `decision` decides how the notice BEHAVES (role, tone, whether a next step is offered);
 * `user_message_key` decided what it SAID. Nothing checked the two agreed, so a mismatched
 * pair rendered a self-contradiction — measured, both directions:
 *
 *   TEMPORARILY_UNAVAILABLE + `trust.deny.generic` →
 *     "You don't have access to this … This is not a permissions problem"
 *   DENY + `trust.step_up.required` →
 *     "Confirm it's really you … Verifying your identity will not change this"
 *
 * The second is the dangerous one: it puts an instruction to verify at the top of a refusal
 * that cannot be unlocked by verifying — exactly the false next step this component exists to
 * withhold. So the decision wins: a key whose kind disagrees is ignored rather than trusted.
 * A key we do not recognise carries no kind and is still honoured (it is only ever additional
 * wording for the same decision).
 */
const MESSAGE_KEY_KIND: Record<string, TrustChallenge["kind"]> = {
  "trust.step_up.required": "actionable",
  "trust.consent.required": "actionable",
  "trust.recovery.required": "actionable",
  "trust.deny.generic": "refusal",
  "trust.deny.missing_verdict": "refusal",
};

/** The kind a decision implies, independent of any message key. */
function decisionKind(decision: TrustChallengeDecision): TrustChallenge["kind"] | null {
  if (decision === "DENY") return "refusal";
  if (decision === "TEMPORARILY_UNAVAILABLE") return "unavailable";
  if ((ACTIONABLE_DECISIONS as readonly string[]).includes(decision)) return "actionable";
  return null;
}

export function trustChallengeCopy(outcome: TrustChallengeOutcome): TrustChallengeCopy {
  const key = outcome.user_message_key;
  const byKey = key ? MESSAGE_KEY_COPY[key] : undefined;

  if (byKey && key) {
    const keyKind = MESSAGE_KEY_KIND[key];
    const ownKind = decisionKind(outcome.decision);
    // Unknown key kind → nothing to disagree with. Kinds match → the key refines the wording.
    if (!keyKind || !ownKind || keyKind === ownKind) return byKey;
    // Mismatch: fall through to the decision's own copy, which is always kind-consistent.
  }

  return DECISION_COPY[outcome.decision] ?? DECISION_COPY.DENY;
}
