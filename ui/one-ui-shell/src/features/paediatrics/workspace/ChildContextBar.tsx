"use client";

import React from "react";
import type { PaediatricContext } from "./usePaediatricContext";

/**
 * The facts that must never be more than a glance away while a child is being seen.
 *
 * Age, current weight and allergies are the three things a paediatric prescribing error
 * usually turns on, and all three are easy to lose behind a tab. Age is shown in the unit a
 * clinician thinks in at that age — days for a newborn, weeks for a small infant — because
 * "0 years" for a ten-day-old is how an adult dose reaches a neonate.
 *
 * The weight is labelled as the dosing weight with the date it was taken, so a stale weight
 * is visible as stale rather than silently used. Where there is no weight at all the bar
 * says so in the strongest terms available: no dose can be calculated without one.
 */
export interface ChildContextBarProps {
  context: PaediatricContext;
  className?: string;
}

export function ChildContextBar({ context, className = "" }: ChildContextBarProps) {
  const { age, dosingWeightKg, dosingWeightMeasuredAt, allergyLabels } = context;

  const weightAgeDays = dosingWeightMeasuredAt
    ? Math.floor((Date.now() - new Date(dosingWeightMeasuredAt).getTime()) / 86_400_000)
    : null;

  return (
    <div
      className={`sticky top-0 z-20 rounded-lg border border-border bg-background/95 p-3 backdrop-blur ${className}`}
      data-testid="child-context-bar"
    >
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
        <div>
          <span className="block text-xs text-muted-foreground">Age</span>
          <span className="font-medium text-foreground" data-testid="child-age">
            {age.display}
          </span>
        </div>

        <div>
          <span className="block text-xs text-muted-foreground">Dosing weight</span>
          {dosingWeightKg === null ? (
            <span className="font-medium text-red-600" data-testid="child-weight">
              None recorded — no dose can be calculated
            </span>
          ) : (
            <span className="font-medium text-foreground" data-testid="child-weight">
              {dosingWeightKg} kg
              {weightAgeDays !== null && (
                <span className="ml-1 text-xs font-normal text-muted-foreground">
                  ({weightAgeDays === 0 ? "today" : `${weightAgeDays}d ago`})
                </span>
              )}
            </span>
          )}
        </div>

        <div className="min-w-[10rem]">
          <span className="block text-xs text-muted-foreground">Allergies</span>
          {allergyLabels.length === 0 ? (
            // "None recorded" rather than "no allergies" — the record may simply not have
            // been asked, and the difference matters before a first prescription.
            <span className="font-medium text-muted-foreground" data-testid="child-allergies">
              None recorded
            </span>
          ) : (
            <span className="font-medium text-red-600" data-testid="child-allergies">
              {allergyLabels.join(", ")}
            </span>
          )}
        </div>

        {age.isSickYoungInfantWindow && (
          <span
            className="rounded-md bg-red-600 px-2 py-1 text-xs font-medium text-white"
            data-testid="child-young-infant-flag"
          >
            Under 60 days — young infant
          </span>
        )}
      </div>

      {context.unavailable.length > 0 && (
        <p className="mt-2 text-xs text-amber-600" data-testid="child-context-degraded">
          Could not load: {context.unavailable.join(", ")}. What is shown may be incomplete.
        </p>
      )}
    </div>
  );
}
