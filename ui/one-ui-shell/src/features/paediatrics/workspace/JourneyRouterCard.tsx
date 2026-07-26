"use client";

import React from "react";
import { SegmentedControl } from "shared-ui";
import type { JourneyRecommendation, PaediatricJourney, PresentationContext } from "./paediatric-age";

/**
 * Suggests where to start, and shows why.
 *
 * The point of the age router is that a clinician should not have to hunt for the correct
 * age-specific assessment — but a suggestion that cannot be overridden is a different and
 * worse problem, so every journey stays open and the reasoning is always visible. A
 * clinician who disagrees can see exactly what the suggestion was based on.
 */
const ALL_JOURNEYS: { journey: PaediatricJourney; title: string }[] = [
  { journey: "NEWBORN_ROUTINE", title: "Newborn assessment" },
  { journey: "SICK_YOUNG_INFANT", title: "Sick young infant" },
  { journey: "IMNCI_UNDER_FIVE", title: "IMNCI assessment" },
  { journey: "WELL_CHILD", title: "Well child visit" },
  { journey: "GENERAL_PAEDIATRIC", title: "General clerking" },
  { journey: "ADOLESCENT", title: "Adolescent consultation" },
];

export interface JourneyRouterCardProps {
  recommendation: JourneyRecommendation;
  presentation: PresentationContext;
  onPresentationChange: (presentation: PresentationContext) => void;
  onJourneySelect?: (journey: PaediatricJourney) => void;
  className?: string;
}

export function JourneyRouterCard({
  recommendation,
  presentation,
  onPresentationChange,
  onJourneySelect,
  className = "",
}: JourneyRouterCardProps) {
  return (
    <section
      className={`rounded-lg border border-border bg-background p-4 ${className}`}
      aria-labelledby="journey-router-heading"
      data-testid="journey-router-card"
    >
      <h2 id="journey-router-heading" className="text-sm font-semibold text-foreground">
        Start the assessment
      </h2>

      <div className="mt-3">
        <span id="presentation-label" className="text-xs font-medium text-muted-foreground">
          How is the child presenting?
        </span>
        <div className="mt-1">
          <SegmentedControl
            aria-labelledby="presentation-label"
            data-testid="presentation-toggle"
            options={[
              { value: "ROUTINE", label: "Routine" },
              { value: "ACUTE", label: "Unwell", tone: "warning" },
            ]}
            value={presentation}
            onChange={(v) => onPresentationChange(v as PresentationContext)}
          />
        </div>
      </div>

      <div className="mt-4 rounded-md border border-primary bg-primary/5 p-3" data-testid="journey-recommendation">
        <p className="text-sm font-medium text-foreground">{recommendation.title}</p>
        <p className="mt-1 text-xs text-muted-foreground" data-testid="journey-rationale">
          {recommendation.rationale}
        </p>
        {recommendation.journey !== "UNDETERMINED" && onJourneySelect && (
          <button
            type="button"
            onClick={() => onJourneySelect(recommendation.journey)}
            data-testid="journey-start-recommended"
            className="mt-3 min-h-[44px] w-full rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground"
          >
            Start {recommendation.title.toLowerCase()}
          </button>
        )}
      </div>

      <details className="mt-3" data-testid="journey-alternatives">
        <summary className="cursor-pointer text-xs text-muted-foreground">
          Start a different assessment
        </summary>
        <ul className="mt-2 space-y-1">
          {ALL_JOURNEYS.filter((entry) => entry.journey !== recommendation.journey).map((entry) => (
            <li key={entry.journey}>
              <button
                type="button"
                onClick={() => onJourneySelect?.(entry.journey)}
                data-testid={`journey-option-${entry.journey}`}
                className="min-h-[44px] w-full rounded-md border border-border px-3 text-left text-sm text-foreground hover:bg-muted"
              >
                {entry.title}
              </button>
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
