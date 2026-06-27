/**
 * Step-up detection + presentation helpers (G-CZO-04 / G-CZO-17).
 *
 * A sensitive action denied by the trust plane comes back as HTTP 401 with a
 * STEP_UP_REQUIRED signal and a list of acceptable methods. This module recognises
 * that signal (so it is not mistaken for a session-expiry 401) and maps the engine's
 * method tokens to citizen-friendly labels.
 */

const STEP_UP_CODE = "STEP_UP_REQUIRED";

export interface StepUpSignal {
  required: boolean;
  methods: string[];
}

/** Inspect a thrown api-client error (or a raw 401 body) for a step-up challenge. */
export function readStepUp(err: unknown): StepUpSignal {
  const o = (err ?? {}) as Record<string, unknown>;
  const code = o.decision ?? o.verdict ?? o.errorCode;
  const required = code === STEP_UP_CODE;
  const methods = Array.isArray(o.stepUpMethods)
    ? (o.stepUpMethods as unknown[]).map(String)
    : [];
  return { required, methods };
}

/** True when the error/body is a step-up challenge (and must not be treated as session expiry). */
export function isStepUpRequired(err: unknown): boolean {
  return readStepUp(err).required;
}

const METHOD_LABELS: Record<string, string> = {
  MFA: "Authenticator app code",
  TOTP: "Authenticator app code",
  SMS_OTP: "SMS one-time code",
  SMS: "SMS one-time code",
  BIOMETRIC: "Device biometric",
  SUPERVISOR_APPROVAL: "Supervisor approval",
  SUPERVISOR: "Supervisor approval",
};

export function stepUpMethodLabel(method: string): string {
  return METHOD_LABELS[method?.toUpperCase?.()] ?? method;
}
