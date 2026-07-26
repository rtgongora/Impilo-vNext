"use client";

import React from "react";
import type { ImamAssessment } from "@/hooks/queries/useImam";
import { dischargeReadiness, TONE_CLASSES } from "./imam-presentation";

/**
 * Every discharge criterion, met or not, with the sentence that explains it.
 *
 * The details are the point. "1 review recorded; 2 consecutive are required. A single
 * measurement above the cut-off can be a mis-measurement and must not discharge a child" is the
 * difference between a clinician understanding why the button is not there and believing the
 * system is being obstructive. Showing only the unmet ones would hide how close the child is.
 */
export function DischargeCriteriaList({ assessment }: { assessment: ImamAssessment | null }) {
  const readiness = dischargeReadiness(assessment);

  if (readiness.state === "NOT_EVALUATED") {
    return (
      <div
        className={`rounded-lg border p-3 ${TONE_CLASSES.unknown}`}
        data-testid="discharge-not-evaluated"
      >
        <p className="text-sm font-semibold">{readiness.headline}</p>
        <p className="mt-1 text-xs">{readiness.detail}</p>
      </div>
    );
  }

  const criteria = assessment?.dischargeCriteria ?? [];

  return (
    <section className="rounded-lg border border-border bg-background p-4" data-testid="discharge-criteria">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-foreground">Discharge criteria</h3>
          <p className="mt-0.5 text-xs text-muted-foreground">{readiness.detail}</p>
        </div>
        <span
          className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
            readiness.state === "READY" ? "bg-emerald-600 text-white" : "bg-muted text-foreground"
          }`}
          data-testid="discharge-state"
        >
          {readiness.headline}
        </span>
      </div>

      {criteria.length === 0 ? (
        <p className="mt-3 text-xs text-muted-foreground">
          This programme declares no anthropometric discharge criteria; discharge is a clinical decision
          recorded with its reason.
        </p>
      ) : (
        <ul className="mt-3 space-y-2">
          {criteria.map((criterion) => (
            <li
              key={criterion.code}
              className="flex gap-2 rounded-md border border-border p-2"
              data-testid={`criterion-${criterion.code}`}
            >
              <span
                aria-hidden="true"
                className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-xs font-bold ${
                  criterion.met ? "bg-emerald-600 text-white" : "bg-muted text-muted-foreground"
                }`}
              >
                {criterion.met ? "✓" : "–"}
              </span>
              <div className="min-w-0">
                <p className="text-sm text-foreground">
                  <span className="sr-only">{criterion.met ? "Met: " : "Not met: "}</span>
                  {criterion.name}
                </p>
                {criterion.detail && (
                  <p className="mt-0.5 text-xs text-muted-foreground">{criterion.detail}</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
