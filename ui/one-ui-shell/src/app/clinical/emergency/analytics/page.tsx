"use client";

/**
 * Emergency analytics — deliberately empty, and specific about why.
 *
 * The WHO DSEC minimum dataset and its derived indicators are not computed anywhere yet;
 * docs/clinical-governance/emergency/coverage-exclusions.json records EMS.DSEC.MINIMUM_DATASET as
 * DEFERRED to W17. Until that wave lands the projection and the definitions, this page names each
 * measure that is missing and says which one thing is stopping it.
 *
 * It renders no zeros. A zero on an emergency dashboard is read as "no deaths", not as "no
 * pipeline", and that misreading is the whole reason this page is a named-absence list instead of
 * a grid of cards.
 */

import Link from "next/link";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

type PendingMeasure = {
  name: string;
  dsecElement: string;
  blockedBy: string;
};

/**
 * Named, not counted. Each entry is a measure a clinician or a district officer would expect to
 * find here, paired with the DSEC element it derives from and the single reason it is absent.
 */
const PENDING_MEASURES: PendingMeasure[] = [
  {
    name: "Time to first clinician assessment",
    dsecElement: "Arrival time; time of first provider contact",
    blockedBy: "No projection reads pct.emergency_episode; reporting-service has its own database",
  },
  {
    name: "Triage acuity distribution",
    dsecElement: "Triage category",
    blockedBy: "IITT is the acuity of record in pct, but no indicator definition aggregates it",
  },
  {
    name: "Emergency unit length of stay",
    dsecElement: "Arrival time; disposition time",
    blockedBy: "Disposition timestamps land in pct.emergency_disposition; nothing derives the interval",
  },
  {
    name: "Disposition mix across the 15 outcome types",
    dsecElement: "Disposition",
    blockedBy: "No rpt_ definition exists for the disposition vocabulary",
  },
  {
    name: "Emergency unit mortality",
    dsecElement: "Outcome at disposition",
    blockedBy: "Deaths are recorded per-episode; no governed denominator is defined",
  },
  {
    name: "Left without being seen",
    dsecElement: "Disposition; time of departure",
    blockedBy: "Captured as a disposition type; no indicator aggregates it",
  },
  {
    name: "Handover acceptance time and expiry rate",
    dsecElement: "Referral / transfer out",
    blockedBy: "The acceptance handshake is recorded in pct; no projection exposes its timings",
  },
  {
    name: "Resuscitation interval adherence",
    dsecElement: "Interventions performed",
    blockedBy: "Timings exist on the resuscitation record only; not projected",
  },
  {
    name: "Observation and short-stay conversion",
    dsecElement: "Disposition",
    blockedBy: "Observation stays are per-episode rows with no aggregate definition",
  },
  {
    name: "Critical result acknowledgement time",
    dsecElement: "Diagnostics ordered and resulted",
    blockedBy: "The act/close chain is recorded per order; no indicator measures the delay",
  },
];

export default function EmergencyAnalyticsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Emergency analytics"
        subtitle="Not yet computed — every measure below is named so its absence cannot be mistaken for a zero"
      >
        <div className="mb-4 flex gap-4 text-sm">
          <Link href="/clinical/emergency/command" className="text-primary underline">
            Emergency command
          </Link>
          <Link href="/clinical/emergency/board" className="text-primary underline">
            Episode board
          </Link>
        </div>

        <div
          className="mb-6 rounded-lg border border-warning/40 bg-warning-soft p-4 text-sm"
          data-testid="analytics-not-computed"
        >
          <p className="font-medium text-foreground">
            No emergency indicator is being computed today.
          </p>
          <p className="mt-1 text-muted-foreground">
            The WHO DSEC minimum dataset is recorded as deferred in the emergency standards
            register. The national reporting store cannot read the emergency schema directly, so a
            projection must be built before any of these can be produced. Nothing on this page is a
            measured value, and no figure below should be quoted.
          </p>
        </div>

        <h2 className="mb-2 text-sm font-semibold text-foreground">
          Measures that are expected here and are not available ({PENDING_MEASURES.length})
        </h2>
        <ul className="space-y-2" data-testid="analytics-pending-measures">
          {PENDING_MEASURES.map((measure) => (
            <li
              key={measure.name}
              className="rounded border border-border bg-card p-3 text-sm"
              data-testid="analytics-pending-measure"
            >
              <p className="font-medium text-foreground">{measure.name}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Derived from: {measure.dsecElement}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">Blocked by: {measure.blockedBy}</p>
            </li>
          ))}
        </ul>
      </PageShell>
    </AppLayout>
  );
}
