"use client";

import React from "react";
import Link from "next/link";
import { ChildContextBar } from "./ChildContextBar";
import { DueTodayPanel } from "./DueTodayPanel";
import { JourneyRouterCard } from "./JourneyRouterCard";
import { usePaediatricContext } from "./usePaediatricContext";
import type { PaediatricJourney, PresentationContext } from "./paediatric-age";

/**
 * The paediatric workspace.
 *
 * Replaces a static tile list that named Growth Chart, IMCI, Immunization Schedule, PEWS,
 * developmental milestones and nutrition assessment, and wired none of them. Every surface
 * here is backed by an endpoint that exists; where a capability is genuinely not built yet,
 * it is named as not built rather than shown as an inert tile that looks available.
 */
export interface PaediatricWorkspaceShellProps {
  patientId: string;
}

/** Journeys whose assessment surface exists today, and where it lives. */
const JOURNEY_DESTINATIONS: Partial<Record<PaediatricJourney, (patientId: string) => string>> = {
  WELL_CHILD: (id) => `/ehr/${id}/growth-chart`,
  NEWBORN_ROUTINE: (id) => `/ehr/${id}/growth-chart`,
};

export function PaediatricWorkspaceShell({ patientId }: PaediatricWorkspaceShellProps) {
  const [presentation, setPresentation] = React.useState<PresentationContext>("ROUTINE");
  const [pendingJourney, setPendingJourney] = React.useState<PaediatricJourney | null>(null);
  const context = usePaediatricContext(patientId, presentation);

  const onJourneySelect = (journey: PaediatricJourney) => {
    const destination = JOURNEY_DESTINATIONS[journey]?.(patientId);
    if (destination) {
      window.location.assign(destination);
      return;
    }
    // No inert buttons: a journey whose assessment surface is not built yet says so, rather
    // than appearing to start something and doing nothing.
    setPendingJourney(journey);
  };

  if (context.isLoading) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="paediatric-workspace-loading">
        Loading the child&apos;s record…
      </p>
    );
  }

  if (!context.age.paediatric && context.age.ageDays !== null) {
    return (
      <div className="rounded-lg border border-border bg-background p-4" data-testid="paediatric-workspace-adult">
        <p className="text-sm text-foreground">
          This person is {context.age.display} old and is no longer served by paediatric services.
        </p>
        <p className="mt-1 text-xs text-muted-foreground">
          Care transitions to adult services; use the general clinical workspace.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4" data-testid="paediatric-workspace">
      <ChildContextBar context={context} />

      <div className="grid gap-4 lg:grid-cols-2">
        <JourneyRouterCard
          recommendation={context.journey}
          presentation={presentation}
          onPresentationChange={setPresentation}
          onJourneySelect={onJourneySelect}
        />
        <DueTodayPanel items={context.dueItems} />
      </div>

      {pendingJourney && (
        <div
          className="rounded-lg border border-amber-500 bg-amber-50 p-3 text-sm text-amber-900"
          data-testid="journey-not-built"
        >
          The structured assessment for this journey is not built yet, so nothing was started.
          Record the encounter through the clinical workspace in the meantime.
        </div>
      )}

      <section className="rounded-lg border border-border bg-background p-4">
        <h2 className="text-sm font-semibold text-foreground">Child record</h2>
        <ul className="mt-2 grid gap-2 sm:grid-cols-2">
          <li>
            <Link
              href={`/ehr/${patientId}/growth-chart`}
              className="block min-h-[44px] rounded-md border border-border px-3 py-2 text-sm hover:bg-muted"
              data-testid="link-growth-chart"
            >
              Growth chart
              <span className="block text-xs text-muted-foreground">
                Plotted against the WHO standard, with faltering detection
              </span>
            </Link>
          </li>
          <li>
            <Link
              href={`/ehr/${patientId}/imam`}
              className="block min-h-[44px] rounded-md border border-border px-3 py-2 text-sm hover:bg-muted"
              data-testid="link-imam"
            >
              Nutrition treatment
              <span className="block text-xs text-muted-foreground">
                Enrolment, weekly review, discharge criteria and defaulter tracing
              </span>
            </Link>
          </li>
          <li>
            <Link
              href={`/ehr/${patientId}/immunizations`}
              className="block min-h-[44px] rounded-md border border-border px-3 py-2 text-sm hover:bg-muted"
              data-testid="link-immunizations"
            >
              Immunisations
              <span className="block text-xs text-muted-foreground">
                Doses given; the schedule forecast is not available yet
              </span>
            </Link>
          </li>
          <li>
            <Link
              href={`/ehr/${patientId}/vitals`}
              className="block min-h-[44px] rounded-md border border-border px-3 py-2 text-sm hover:bg-muted"
              data-testid="link-vitals"
            >
              Vitals
              <span className="block text-xs text-muted-foreground">Interpreted against age-specific ranges</span>
            </Link>
          </li>
          <li>
            <Link
              href={`/ehr/${patientId}/conditions`}
              className="block min-h-[44px] rounded-md border border-border px-3 py-2 text-sm hover:bg-muted"
              data-testid="link-conditions"
            >
              Problems and diagnoses
              <span className="block text-xs text-muted-foreground">Active problem list</span>
            </Link>
          </li>
        </ul>
      </section>
    </div>
  );
}
