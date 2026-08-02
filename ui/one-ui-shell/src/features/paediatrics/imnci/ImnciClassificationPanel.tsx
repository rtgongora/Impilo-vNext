"use client";

import React from "react";
import {
  useImnciClassification,
  type ImnciAssessment,
  type ImnciClassification,
} from "@/hooks/queries/usePaediatricDecisionSupport";
import {
  assessmentHeadline,
  observationList,
  presentOutcome,
  sortOutcomes,
} from "./imnci-presentation";

/**
 * The IMNCI assess-and-classify result.
 *
 * Everything shown here comes from the engine. This component chooses no classification, applies
 * no threshold and derives no disposition — a second implementation of the tables in the browser
 * would eventually disagree with the governed content about the same child, and the whole reason
 * the tables live in versioned content is that a clinical revision should be a reviewable diff
 * rather than a UI release.
 *
 * What it does own is the honesty of the display, and there are four properties it must hold:
 *
 * 1. **Unresolved never reads as well.** An `INDETERMINATE` row gets a dashed, uncoloured card
 *    that names the observation which would settle it, and sorts *above* the treat-here rows
 *    because it carries an outstanding action.
 * 2. **Provisional never reads as a conclusion.** The chip says so and the caveat explains that a
 *    severer row on the same chart could not be excluded.
 * 3. **An incomplete assessment never summarises as a disposition.** The headline reports the
 *    outstanding work instead, because the disposition was computed over a partial picture.
 * 4. **An unreachable engine never reads as a classified child.** The 502 the BFF returns is
 *    rendered as an explicit failure with nothing clinical in it.
 */
export interface ImnciClassificationPanelProps {
  patientId: string;
  ageDays: number | null;
  /** The findings recorded so far, keyed as the engine names them. Absent means not assessed. */
  findings: Record<string, unknown>;
  /** Hold the call until the clinician has entered something worth classifying. */
  enabled?: boolean;
}

export function ImnciClassificationPanel({
  patientId,
  ageDays,
  findings,
  enabled = true,
}: ImnciClassificationPanelProps) {
  const query = useImnciClassification({ patientId, ageDays, findings }, enabled);

  if (ageDays === null) {
    return (
      <section
        className="rounded-lg border border-slate-400 border-dashed bg-slate-50 p-4 dark:border-slate-500 dark:bg-slate-900/60"
        data-testid="imnci-no-age"
      >
        <h2 className="text-sm font-semibold text-foreground">IMNCI classification</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          This child&apos;s date of birth is not on the record, so neither IMNCI chart can be
          selected. IMNCI is age-routed: the sick young infant chart runs from birth to two months
          and the child chart from two months to five years, and running the wrong one is not a
          near-miss.
        </p>
      </section>
    );
  }

  if (query.isLoading) {
    return (
      <section className="rounded-lg border border-border bg-background p-4" data-testid="imnci-loading">
        <h2 className="text-sm font-semibold text-foreground">IMNCI classification</h2>
        <p className="mt-1 text-sm text-muted-foreground">Running the assess-and-classify tables…</p>
      </section>
    );
  }

  if (query.isError) {
    // The engine could not be reached. Nothing clinical appears here: an empty classification list
    // is the most reassuring thing this panel could show and it has not been established.
    return (
      <section
        className="rounded-lg border border-rose-400 bg-rose-50 p-4 dark:border-rose-600 dark:bg-rose-950/40"
        data-testid="imnci-unavailable"
      >
        <h2 className="text-sm font-semibold text-foreground">IMNCI classification unavailable</h2>
        <p className="mt-1 text-sm text-foreground">
          The classification tables could not be run against this child. This is not a
          classification of no illness — no chart was run at all. Assess and classify on paper and
          record the outcome when the service is available.
        </p>
        <button
          type="button"
          onClick={() => query.refetch()}
          className="mt-3 min-h-[44px] rounded-md border border-rose-500 px-3 py-2 text-sm font-medium text-rose-900 hover:bg-rose-100 dark:text-rose-100 dark:hover:bg-rose-900/50"
        >
          Try again
        </button>
      </section>
    );
  }

  const assessment = query.data;
  if (!assessment) return null;

  if (!assessment.applicable) {
    return (
      <section className="rounded-lg border border-border bg-muted/40 p-4" data-testid="imnci-not-applicable">
        <h2 className="text-sm font-semibold text-foreground">IMNCI classification</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {assessment.note ??
            "IMNCI covers children from birth to five years. This child is outside that range."}
        </p>
      </section>
    );
  }

  return <ImnciAssessmentView assessment={assessment} />;
}

/** Split out so the presentation can be tested against a fixture without a query client. */
export function ImnciAssessmentView({ assessment }: { assessment: ImnciAssessment }) {
  const headline = assessmentHeadline(assessment);
  const headlineStyles: Record<string, string> = {
    critical: "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40",
    unresolved: "border-slate-400 border-dashed bg-slate-50 dark:border-slate-500 dark:bg-slate-900/60",
    treat: "border-amber-400 bg-amber-50 dark:border-amber-600 dark:bg-amber-950/40",
    well: "border-emerald-400 bg-emerald-50 dark:border-emerald-600 dark:bg-emerald-950/40",
    "not-applicable": "border-border bg-muted/40",
  };

  return (
    <section className="space-y-3" data-testid="imnci-assessment">
      <div
        className={`rounded-lg border p-4 ${headlineStyles[headline.tone]}`}
        data-testid="imnci-headline"
        data-tone={headline.tone}
      >
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold text-foreground">{headline.text}</h2>
          {assessment.pathway && (
            // Which chart ran is not a detail. A ten-day-old assessed on the child chart is a
            // different assessment from the one the guideline intends.
            <span className="text-xs text-muted-foreground" data-testid="imnci-pathway">
              {assessment.pathway === "YOUNG_INFANT" ? "Sick young infant chart" : "Sick child chart"}
              {assessment.ageDays !== null ? ` · ${assessment.ageDays} days old` : ""}
            </span>
          )}
        </div>
        {headline.detail && <p className="mt-1 text-sm text-foreground">{headline.detail}</p>}
      </div>

      {assessment.outstandingAssessments.length > 0 && (
        <div
          className="rounded-lg border border-slate-400 border-dashed bg-slate-50 p-3 dark:border-slate-500 dark:bg-slate-900/60"
          data-testid="imnci-outstanding"
        >
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Outstanding observations
          </h3>
          <ul className="mt-2 space-y-1">
            {assessment.outstandingAssessments.map((key) => (
              <li key={key} className="text-sm text-foreground">
                {observationList([key])}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-muted-foreground">
            Each of these is a question the charts need answered. Until they are recorded the
            classifications below are what could be reached without them.
          </p>
        </div>
      )}

      <ul className="space-y-2" data-testid="imnci-classifications">
        {sortOutcomes(assessment.classifications).map((outcome) => (
          <ClassificationRow key={outcome.tableId} outcome={outcome} />
        ))}
      </ul>

      {assessment.treatmentPlan.length > 0 && (
        <div className="rounded-lg border border-border bg-background p-3" data-testid="imnci-treatment-plan">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Treatment plan
          </h3>
          <ol className="mt-2 list-decimal space-y-1 pl-5">
            {assessment.treatmentPlan.map((step, i) => (
              <li key={`${i}-${step}`} className="text-sm text-foreground">
                {step}
              </li>
            ))}
          </ol>
          {assessment.incomplete && (
            <p className="mt-2 text-xs text-amber-700 dark:text-amber-400">
              This plan was built from an incomplete assessment. Completing the outstanding
              observations above may add to it.
            </p>
          )}
        </div>
      )}

      <p className="text-xs text-muted-foreground" data-testid="imnci-provenance">
        {assessment.contentSource ?? "IMNCI classification tables"}
        {assessment.contentVersion ? ` · ${assessment.contentVersion}` : ""}
        {assessment.approvalStatus ? ` · ${assessment.approvalStatus.replace(/_/g, " ").toLowerCase()}` : ""}
        {" · a classification is a management category, not a diagnosis."}
      </p>
    </section>
  );
}

function ClassificationRow({ outcome }: { outcome: ImnciClassification }) {
  const presentation = presentOutcome(outcome);

  return (
    <li
      className={`rounded-lg border p-3 ${presentation.containerClass}`}
      data-testid={`imnci-row-${outcome.tableId}`}
      data-tone={presentation.tone}
      data-status={outcome.status}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-foreground">{presentation.heading}</span>
        <span
          className={`rounded px-2 py-0.5 text-xs font-semibold ${presentation.chipClass}`}
          data-testid={`imnci-chip-${outcome.tableId}`}
        >
          {presentation.chipLabel}
        </span>
      </div>

      {presentation.colourLabel && (
        <p className="mt-1 text-xs font-medium text-foreground" data-testid={`imnci-colour-${outcome.tableId}`}>
          {presentation.colourLabel}
        </p>
      )}

      {presentation.caveat && (
        <p className="mt-1 text-sm text-foreground" data-testid={`imnci-caveat-${outcome.tableId}`}>
          {presentation.caveat}
        </p>
      )}

      {outcome.action && presentation.tone !== "unresolved" && (
        <p className="mt-1 text-sm text-foreground" data-testid={`imnci-action-${outcome.tableId}`}>
          {outcome.action}
        </p>
      )}

      {outcome.treatments.length > 0 && presentation.tone !== "unresolved" && (
        <ul className="mt-1 list-disc space-y-0.5 pl-5">
          {outcome.treatments.map((t, i) => (
            <li key={`${i}-${t}`} className="text-sm text-foreground">
              {t}
            </li>
          ))}
        </ul>
      )}

      {outcome.waivedCriteria.length > 0 && (
        // The waiver never hides the gap. A nutrition row reached without a height board is a
        // different claim from one reached with it, and the clinician is told which they have.
        <p className="mt-1 text-xs text-muted-foreground" data-testid={`imnci-waived-${outcome.tableId}`}>
          Reached without {observationList(outcome.waivedCriteria)} — these were treated as not met
          rather than blocking the chart.
        </p>
      )}

      {outcome.urgentReferral && (
        <p className="mt-1 text-sm font-semibold text-rose-700 dark:text-rose-300">
          Refer urgently after pre-referral treatment.
        </p>
      )}
    </li>
  );
}
