"use client";

import React from "react";

/**
 * Horizontal workflow stepper (desktop/tablet) with an automatic compact
 * mobile variant (step counter + progress bar) below the `sm` breakpoint —
 * a wide horizontal stepper is never forced into a phone viewport.
 *
 * Pure presentation: validation, data preservation, and Back/Continue actions
 * belong to the owning workflow. Completed steps become clickable when
 * `onStepSelect` is provided and `stepClickPolicy` permits.
 */
export interface StepperStep {
  id: string;
  label: string;
  /** Optional one-line detail shown under the label on wide viewports. */
  description?: string;
}

export interface StepperProps {
  steps: StepperStep[];
  /** Index of the current step (0-based). */
  activeIndex: number;
  /** Navigate on step click. Omit for a purely indicative stepper. */
  onStepSelect?: (id: string, index: number) => void;
  /** Which steps may be clicked when `onStepSelect` is set. Default: completed only. */
  stepClickPolicy?: "completed" | "none";
  className?: string;
  "aria-label"?: string;
}

export function Stepper({
  steps,
  activeIndex,
  onStepSelect,
  stepClickPolicy = "completed",
  className = "",
  "aria-label": ariaLabel = "Workflow progress",
}: StepperProps) {
  const total = steps.length;
  const clamped = Math.min(Math.max(activeIndex, 0), Math.max(total - 1, 0));
  const active = steps[clamped];
  const progressPct = total > 0 ? Math.round(((clamped + 1) / total) * 100) : 0;

  return (
    <nav aria-label={ariaLabel} className={`min-w-0 ${className}`}>
      {/* Wide viewports: horizontal step rail */}
      <ol className="hidden items-start gap-1 sm:flex">
        {steps.map((step, i) => {
          const state = i < clamped ? "completed" : i === clamped ? "current" : "upcoming";
          const clickable =
            typeof onStepSelect === "function" && stepClickPolicy === "completed" && state === "completed";

          const badge = (
            <span
              aria-hidden="true"
              className={[
                "flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold",
                state === "completed"
                  ? "border-[color:var(--primary)] bg-[color:var(--primary)] text-[color:var(--on-green)]"
                  : state === "current"
                    ? "border-[color:var(--primary)] bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]"
                    : "border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-muted)]",
              ].join(" ")}
            >
              {state === "completed" ? "✓" : i + 1}
            </span>
          );

          const labelBlock = (
            <span className="min-w-0 text-left">
              <span
                className={[
                  "block truncate text-xs font-medium",
                  state === "current"
                    ? "text-[color:var(--text-primary)]"
                    : "text-[color:var(--text-secondary)]",
                ].join(" ")}
              >
                {step.label}
              </span>
              {step.description ? (
                <span className="hidden max-w-[14rem] truncate text-[11px] text-[color:var(--text-muted)] lg:block">
                  {step.description}
                </span>
              ) : null}
            </span>
          );

          return (
            <li
              key={step.id}
              className="flex min-w-0 flex-1 items-center gap-1"
              aria-current={state === "current" ? "step" : undefined}
            >
              {clickable ? (
                <button
                  type="button"
                  onClick={() => onStepSelect(step.id, i)}
                  className="flex min-w-0 items-center gap-2 rounded-lg px-2 py-1.5 transition hover:bg-[color:var(--surface-soft)]"
                  aria-label={`Go back to step ${i + 1}: ${step.label}`}
                >
                  {badge}
                  {labelBlock}
                </button>
              ) : (
                <span className="flex min-w-0 items-center gap-2 px-2 py-1.5">
                  {badge}
                  {labelBlock}
                </span>
              )}
              {i < total - 1 ? (
                <span
                  aria-hidden="true"
                  className={[
                    "h-px min-w-3 flex-1",
                    i < clamped ? "bg-[color:var(--primary)]" : "bg-[color:var(--border-soft)]",
                  ].join(" ")}
                />
              ) : null}
            </li>
          );
        })}
      </ol>

      {/* Phones: compact step counter + progress bar */}
      <div className="sm:hidden">
        <div className="flex items-baseline justify-between gap-2">
          <p className="min-w-0 truncate text-sm font-semibold text-[color:var(--text-primary)]">
            {active?.label ?? ""}
          </p>
          <p className="shrink-0 text-xs font-medium text-[color:var(--text-muted)]">
            Step {total > 0 ? clamped + 1 : 0} of {total}
          </p>
        </div>
        <div
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={progressPct}
          aria-label={`${ariaLabel}: step ${clamped + 1} of ${total}`}
          className="mt-1.5 h-1 overflow-hidden rounded-full bg-[color:var(--surface-soft)]"
        >
          <div className="h-full rounded-full bg-[color:var(--primary)]" style={{ width: `${progressPct}%` }} />
        </div>
      </div>
    </nav>
  );
}
