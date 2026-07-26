"use client";

import React from "react";
import { DANGER_SIGN_LABELS, dangerSignStatus, type AssessmentAnswers } from "./imnci-journey";

/**
 * The state of the general danger signs, kept in view for the whole assessment.
 *
 * Three states, never two. A sign answered "yes" is an emergency. Every sign answered "no"
 * is a completed check. Anything else is an incomplete check — and that is displayed as
 * incomplete rather than as safe, because a clinician glancing at a quiet banner will read
 * it as "no danger signs" if the banner lets them.
 *
 * This prompts; it does not classify. The authoritative evaluation runs server-side against
 * the governed rule content, so nothing here tells a clinician what the findings mean.
 */
export interface DangerSignBannerProps {
  answers: AssessmentAnswers;
  className?: string;
}

export function DangerSignBanner({ answers, className = "" }: DangerSignBannerProps) {
  const status = dangerSignStatus(answers);

  if (status.present.length > 0) {
    return (
      <div
        role="alert"
        data-testid="danger-sign-banner"
        data-state="present"
        className={`rounded-lg border-l-4 border-red-600 bg-red-50 p-3 ${className}`}
      >
        <p className="text-sm font-semibold text-red-900">
          General danger sign present — this child needs urgent attention
        </p>
        <ul className="mt-1 list-inside list-disc text-xs text-red-900">
          {status.present.map((field) => (
            <li key={field} data-testid={`danger-sign-present-${field}`}>
              {DANGER_SIGN_LABELS[field] ?? field}
            </li>
          ))}
        </ul>
        {!status.complete && (
          <p className="mt-1 text-xs text-red-900">
            {status.unassessed.length} other danger sign
            {status.unassessed.length === 1 ? "" : "s"} still unassessed.
          </p>
        )}
      </div>
    );
  }

  if (!status.complete) {
    return (
      <div
        data-testid="danger-sign-banner"
        data-state="incomplete"
        className={`rounded-lg border-l-4 border-amber-500 bg-amber-50 p-3 ${className}`}
      >
        <p className="text-sm font-medium text-amber-900">
          Danger signs not yet fully assessed
        </p>
        <ul className="mt-1 list-inside list-disc text-xs text-amber-900">
          {status.unassessed.map((field) => (
            <li key={field} data-testid={`danger-sign-unassessed-${field}`}>
              {DANGER_SIGN_LABELS[field] ?? field}
            </li>
          ))}
        </ul>
      </div>
    );
  }

  return (
    <div
      data-testid="danger-sign-banner"
      data-state="clear"
      className={`rounded-lg border-l-4 border-emerald-600 bg-emerald-50 p-3 ${className}`}
    >
      <p className="text-sm font-medium text-emerald-900">
        All four general danger signs assessed — none present
      </p>
    </div>
  );
}
