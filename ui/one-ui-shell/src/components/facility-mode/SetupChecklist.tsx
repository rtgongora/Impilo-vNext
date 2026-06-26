"use client";

import { CheckCircle2, Circle } from "lucide-react";
import { SETUP_STEPS, type FacilitySetupStepState } from "./types";

/**
 * Renders the facility-mode setup-wizard progress as an honest checklist.
 * No fake completions — each flag reflects the TUSO setup-state SoR.
 */
export function SetupChecklist({
  state,
  compact = false,
}: {
  state: FacilitySetupStepState;
  compact?: boolean;
}) {
  const completed = SETUP_STEPS.filter((s) => Boolean(state[s.flag])).length;
  const total = SETUP_STEPS.length;
  const pct = Math.round((completed / total) * 100);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-foreground">Setup progress</span>
        <span className="text-xs text-muted-foreground">
          {completed}/{total} · {pct}%
        </span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
        <div
          className="h-full bg-primary transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <ul className={compact ? "grid grid-cols-2 gap-1.5" : "space-y-1.5"}>
        {SETUP_STEPS.map((step) => {
          const done = Boolean(state[step.flag]);
          const isNext = state.nextStep === step.key;
          return (
            <li
              key={step.key}
              className={`flex items-center gap-2 rounded-md px-2 py-1.5 text-sm ${
                isNext ? "bg-primary-soft/60 ring-1 ring-primary/30" : ""
              }`}
            >
              {done ? (
                <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" />
              ) : (
                <Circle className="h-4 w-4 shrink-0 text-muted-foreground/50" />
              )}
              <span className={done ? "text-foreground" : "text-muted-foreground"}>
                {step.label}
              </span>
              {isNext && (
                <span className="ml-auto text-[10px] font-medium uppercase tracking-wide text-primary">
                  Next
                </span>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
