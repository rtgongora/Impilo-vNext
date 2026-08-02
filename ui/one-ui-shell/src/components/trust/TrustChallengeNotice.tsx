"use client";

import { useEffect, useRef } from "react";
import { StepUpPrompt } from "@/components/citizen/StepUpPrompt";
import { stepUpMethodLabel } from "@/lib/stepUp";
import {
  challengeAuthenticationMethods,
  challengeContextOptions,
  trustChallengeCopy,
  type TrustChallenge,
} from "@/lib/trust/challenge";

/**
 * The one surface that shows a trust decision to a person (Checkpoint 6).
 *
 * It exists to keep three things apart that an opaque error collapses into one:
 *
 *  - REFUSAL (DENY) — you may not, and no amount of verifying, switching context or waiting
 *    changes that. Rendered as a blocking `alertdialog` with no way forward on offer, because
 *    offering one would be a lie.
 *  - ACTIONABLE (STEP_UP_REQUIRED / CONSENT_REQUIRED / CONTEXT_REQUIRED / AUTHORITY_REQUIRED /
 *    APPROVAL_REQUIRED / RECOVERY_REQUIRED / REAUTHENTICATION_REQUIRED / AUTHENTICATION_REQUIRED /
 *    BREAK_GLASS_AVAILABLE) — there IS a next step. Rendered as a `dialog` that offers ONLY the
 *    choices the outcome itself permits.
 *  - UNAVAILABLE (TEMPORARILY_UNAVAILABLE) — nothing is wrong with your permissions. Rendered as
 *    a polite `status` (not a modal, not an accusation) with a retry.
 *
 * Never rendered: `reason_code`, `decision_id`, `policy_version` or any token material. Those are
 * for logs and support. `support_reference` IS user-facing and is shown so a person can quote it.
 *
 * Step-up reuses {@link StepUpPrompt} rather than duplicating the challenge/verify loop.
 */
export function TrustChallengeNotice({
  challenge,
  onDismiss,
  onResolved,
  onSelectContext,
  onRetry,
  dismissLabel = "Go back",
}: {
  challenge: TrustChallenge;
  /** Leave the blocked action safely. Always offered. */
  onDismiss: () => void;
  /** The challenge was satisfied (e.g. step-up verified) — the caller retries the original action. */
  onResolved?: () => void;
  /** Caller-owned context switch. Omitted → options are listed, not offered as controls. */
  onSelectContext?: (option: string) => void;
  /** Caller-owned retry for a temporarily unavailable trust plane. */
  onRetry?: () => void;
  dismissLabel?: string;
}) {
  const { outcome } = challenge;
  const copy = trustChallengeCopy(outcome);
  const methods = challengeAuthenticationMethods(outcome);
  const contextOptions = challengeContextOptions(outcome);
  const headingId = `trust-challenge-title-${challenge.decision.toLowerCase()}`;
  const bodyId = `trust-challenge-body-${challenge.decision.toLowerCase()}`;

  const containerRef = useRef<HTMLDivElement | null>(null);
  const blocking = challenge.kind !== "unavailable";
  useEffect(() => {
    if (blocking) containerRef.current?.focus();
  }, [blocking, challenge.decision]);

  const tone =
    challenge.kind === "refusal"
      ? "border-rose-300 bg-rose-50 text-rose-900"
      : challenge.kind === "unavailable"
        ? "border-slate-300 bg-slate-50 text-slate-800"
        : "border-amber-300 bg-amber-50 text-amber-900";

  const usesStepUpPrompt =
    challenge.kind === "actionable" &&
    (challenge.decision === "STEP_UP_REQUIRED" || challenge.decision === "REAUTHENTICATION_REQUIRED");

  return (
    <div
      ref={containerRef}
      tabIndex={blocking ? -1 : undefined}
      data-testid="trust-challenge"
      data-kind={challenge.kind}
      data-decision={challenge.decision}
      role={
        challenge.kind === "refusal"
          ? "alertdialog"
          : challenge.kind === "unavailable"
            ? "status"
            : "dialog"
      }
      {...(blocking ? { "aria-modal": true } : { "aria-live": "polite" as const })}
      aria-labelledby={headingId}
      aria-describedby={bodyId}
      className={`rounded-xl border p-5 ${tone}`}
    >
      <h2 id={headingId} className="text-lg font-semibold">
        {copy.title}
      </h2>
      <p id={bodyId} className="mt-1 text-sm">
        {copy.explanation}
      </p>

      {challenge.kind === "refusal" && (
        <p className="mt-2 text-sm font-medium">
          Verifying your identity or switching context will not change this. Nothing was carried
          out.
        </p>
      )}

      {challenge.kind === "unavailable" && (
        <p className="mt-2 text-sm font-medium">
          This is not a permissions problem, and nothing was carried out.
        </p>
      )}

      {copy.actionHint && challenge.kind === "actionable" && (
        <p className="mt-2 text-sm font-medium">{copy.actionHint}</p>
      )}

      {usesStepUpPrompt ? (
        <div className="mt-4">
          <StepUpPrompt
            methods={methods}
            title={copy.title}
            description={copy.explanation}
            onCompleted={onResolved ?? onDismiss}
            onCancel={onDismiss}
          />
        </div>
      ) : (
        <>
          {contextOptions.length > 0 && (
            <div className="mt-4">
              <p className="text-sm font-medium">You can continue in:</p>
              {onSelectContext ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  {contextOptions.map((option) => (
                    <button
                      key={option}
                      type="button"
                      onClick={() => onSelectContext(option)}
                      className="rounded-md border border-amber-400 bg-white px-3 py-2 text-sm font-medium text-amber-900 hover:bg-amber-100"
                    >
                      {humanise(option)}
                    </button>
                  ))}
                </div>
              ) : (
                <ul className="mt-2 list-disc pl-5 text-sm">
                  {contextOptions.map((option) => (
                    <li key={option}>{humanise(option)}</li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {challenge.kind === "actionable" && !usesStepUpPrompt && methods.length > 0 && (
            <div className="mt-4">
              <p className="text-sm font-medium">Accepted ways to verify:</p>
              <ul className="mt-2 list-disc pl-5 text-sm">
                {methods.map((method) => (
                  <li key={method}>{stepUpMethodLabel(method)}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="mt-4 flex flex-wrap gap-2">
            {challenge.kind === "unavailable" && onRetry && (
              <button
                type="button"
                onClick={onRetry}
                className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-900"
              >
                Try again
              </button>
            )}
            <button
              type="button"
              onClick={onDismiss}
              className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              {dismissLabel}
            </button>
          </div>
        </>
      )}

      {outcome.support_reference && (
        <p className="mt-4 text-xs">
          If you need help, quote reference{" "}
          <span className="font-mono">{outcome.support_reference}</span>.
        </p>
      )}
    </div>
  );
}

/** SCREAMING_SNAKE policy tokens read as shouting; anything else is left exactly as sent. */
function humanise(value: string): string {
  if (!/^[A-Z][A-Z0-9_]*$/.test(value)) return value;
  const words = value.toLowerCase().split("_").join(" ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}
