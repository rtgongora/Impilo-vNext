"use client";

import React from "react";
import {
  useGrowthInterpretation,
  type GrowthInterpretation,
  type GrowthSignal,
} from "@/hooks/queries/usePaediatricDecisionSupport";
import type { GrowthMeasurement } from "@/hooks/queries/useGrowth";

/**
 * What the growth series means, and what to do about it.
 *
 * Growth faltering shows in the change between contacts long before any single measurement crosses
 * a line, so this is a judgement about a series and it lives in governed content. Nothing is
 * computed here: the engine cannot even recompute a z-score — pct-service stamps those at the time
 * of measurement — so its reading can never drift from what the record says, and neither can this
 * panel.
 *
 * **The property this component exists to preserve is `interpretation_blocked`.** When a
 * measurement is in question — an implausible jump, or the artefact of a child first measured
 * standing rather than lying down — the engine suppresses the clinical signals *entirely* rather
 * than reporting them alongside the data-quality problem. Data quality outranks clinical
 * interpretation, because otherwise a mistyped weight raises a severe-faltering alert and a clinic
 * investigates a transcription error as if it were disease.
 *
 * The engine does that suppression by emptying the signal list. This panel must therefore do two
 * things and neither is optional: **show the blocked state as its own finding**, so an empty
 * signal list is not read as "nothing wrong"; and **never render a trend, a tick or a reassurance
 * in the space where the signals would have been**. An empty list under a blocked interpretation
 * means nobody has interpreted this child's growth, not that it is fine.
 */
export interface GrowthSignalsPanelProps {
  patientId: string;
  measurements: GrowthMeasurement[];
  /** True when the growth history itself could not be read. */
  historyUnavailable?: boolean;
}

/**
 * The engine takes the stamped scores, not the raw anthropometry. `standard` is passed through
 * because a handover from the Fenton preterm chart to the WHO term standard is a discontinuity the
 * engine must be able to see; omitting it leaves it comparing across two references as if they
 * were one.
 */
export function toEnginePoints(measurements: GrowthMeasurement[]): Array<Record<string, unknown>> {
  return [...measurements]
    .sort((a, b) => new Date(a.measuredAt).getTime() - new Date(b.measuredAt).getTime())
    .map((m) => ({
      age_days: m.derived?.ageDays ?? null,
      weight_kg: m.weightKg,
      weight_for_age_z: m.derived?.weightForAge?.zScore ?? null,
      length_height_for_age_z: m.derived?.lengthHeightForAge?.zScore ?? null,
      head_circumference_for_age_z: m.derived?.headCircumferenceForAge?.zScore ?? null,
      measurement_mode: m.derived?.normalizedStatureMode ?? m.measurementMode ?? null,
      standard: m.derived?.standard ?? null,
      postmenstrual_age_weeks: m.derived?.postmenstrualAgeWeeks ?? null,
    }));
}

export function GrowthSignalsPanel({
  patientId,
  measurements,
  historyUnavailable = false,
}: GrowthSignalsPanelProps) {
  const points = React.useMemo(() => toEnginePoints(measurements), [measurements]);
  const query = useGrowthInterpretation({ patientId, measurements: points }, !historyUnavailable);

  if (historyUnavailable) {
    return (
      <Frame testId="growth-signals-history-unavailable" tone="error" heading="Growth interpretation unavailable">
        <p className="text-sm text-foreground">
          The growth measurements could not be read, so nothing was interpreted. This is not a
          finding that the child is growing normally.
        </p>
      </Frame>
    );
  }

  if (query.isLoading) {
    return (
      <Frame testId="growth-signals-loading" tone="plain" heading="Growth interpretation">
        <p className="text-sm text-muted-foreground">Interpreting the growth series…</p>
      </Frame>
    );
  }

  if (query.isError) {
    return (
      <Frame testId="growth-signals-unavailable" tone="error" heading="Growth interpretation unavailable">
        <p className="text-sm text-foreground">
          The growth series could not be interpreted. An absence of signals here is not a finding
          that this child is growing normally — nothing was assessed.
        </p>
        <button
          type="button"
          onClick={() => query.refetch()}
          className="mt-3 min-h-[44px] rounded-md border border-rose-500 px-3 py-2 text-sm font-medium text-rose-900 hover:bg-rose-100 dark:text-rose-100 dark:hover:bg-rose-900/50"
        >
          Try again
        </button>
      </Frame>
    );
  }

  if (!query.data) return null;
  return <GrowthSignalsView interpretation={query.data} />;
}

/** Split out so the display can be tested against a fixture without a query client. */
export function GrowthSignalsView({ interpretation }: { interpretation: GrowthInterpretation }) {
  // Checked before anything else. Every branch below this one describes a clinical reading, and
  // when the interpretation is blocked there is no clinical reading to describe.
  if (interpretation.interpretationBlocked) {
    return (
      <Frame testId="growth-signals-blocked" tone="blocked" heading="Growth not interpreted — measurement in question">
        <p className="text-sm text-foreground">
          {interpretation.notAssessableReason ??
            "One of these measurements is implausible or was taken differently from the others, so the growth signals have been withheld."}
        </p>
        <p className="mt-2 text-sm text-foreground">
          The clinical signals are deliberately not shown. A measurement that may be a recording
          error must be resolved before this child&apos;s growth is interpreted — otherwise a
          mistyped weight is investigated as disease. Check the measurement, correct it if it is
          wrong, and re-measure if it is not.
        </p>
        <Provenance interpretation={interpretation} />
      </Frame>
    );
  }

  if (!interpretation.assessable) {
    return (
      <Frame testId="growth-signals-not-assessable" tone="blocked" heading="Growth not yet assessable">
        <p className="text-sm text-foreground">
          {interpretation.notAssessableReason ??
            "Two measurements are the minimum. A child weighed once has not been shown to be growing well."}
        </p>
        <Provenance interpretation={interpretation} />
      </Frame>
    );
  }

  if (interpretation.signals.length === 0) {
    return (
      <Frame testId="growth-signals-none" tone="well" heading="No growth concern detected">
        <p className="text-sm text-foreground">
          {interpretation.zTrendDerivable
            ? `Compared across ${interpretation.contactsConsidered ?? 0} contacts, no faltering or excessive gain was detected.`
            : // Not the same claim. Nothing was compared, so nothing was found — which is not a
              // finding that nothing is there.
              "No signal was raised, but the trend between contacts could not be derived, so this is not a comparison that found nothing."}
        </p>
        <Provenance interpretation={interpretation} />
      </Frame>
    );
  }

  return (
    <section className="space-y-3" data-testid="growth-signals">
      <div
        className={`rounded-lg border p-4 ${
          interpretation.referralRequired
            ? "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40"
            : "border-amber-400 bg-amber-50 dark:border-amber-600 dark:bg-amber-950/40"
        }`}
        data-testid="growth-signals-headline"
      >
        <h2 className="text-sm font-semibold text-foreground">
          {interpretation.signals.length} growth signal
          {interpretation.signals.length === 1 ? "" : "s"}
          {interpretation.referralRequired ? " — referral required" : ""}
        </h2>
        <p className="mt-1 text-xs text-muted-foreground">
          Compared across {interpretation.contactsConsidered ?? 0} contacts.
        </p>
      </div>

      <ul className="space-y-2" data-testid="growth-signal-list">
        {interpretation.signals.map((signal) => (
          <SignalRow key={signal.code} signal={signal} />
        ))}
      </ul>

      <Provenance interpretation={interpretation} />
    </section>
  );
}

function SignalRow({ signal }: { signal: GrowthSignal }) {
  const severe = signal.referralRequired || (signal.severity ?? "").toUpperCase() === "SEVERE";
  return (
    <li
      className={`rounded-lg border p-3 ${
        severe
          ? "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40"
          : "border-amber-400 bg-amber-50 dark:border-amber-600 dark:bg-amber-950/40"
      }`}
      data-testid={`growth-signal-${signal.code}`}
      data-severity={signal.severity ?? ""}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-foreground">{signal.name ?? signal.code}</span>
        {signal.severity && (
          <span
            className={`rounded px-2 py-0.5 text-xs font-semibold ${
              severe ? "bg-rose-600 text-white" : "bg-amber-500 text-amber-950"
            }`}
          >
            {signal.severity}
          </span>
        )}
      </div>

      {/* The action is the point of the signal. A finding with no action is a worry with nowhere
          to go, and the engine always names one. */}
      {signal.action && (
        <p className="mt-1 text-sm font-medium text-foreground" data-testid={`growth-action-${signal.code}`}>
          {signal.action}
        </p>
      )}

      {signal.rationale && <p className="mt-1 text-sm text-foreground">{signal.rationale}</p>}

      {signal.zChange !== null && (
        <p className="mt-1 text-xs text-muted-foreground">
          {signal.measure ? `${signal.measure}: ` : ""}
          {signal.zChange > 0 ? "+" : ""}
          {signal.zChange.toFixed(2)} z
          {signal.fromAgeDays !== null && signal.toAgeDays !== null
            ? ` between day ${signal.fromAgeDays} and day ${signal.toAgeDays}`
            : ""}
        </p>
      )}
    </li>
  );
}

function Provenance({ interpretation }: { interpretation: GrowthInterpretation }) {
  return (
    <p className="mt-3 text-xs text-muted-foreground" data-testid="growth-signals-provenance">
      {interpretation.contentSource ?? "Growth intelligence content"}
      {interpretation.contentVersion ? ` · ${interpretation.contentVersion}` : ""}
      {interpretation.approvalStatus
        ? ` · ${interpretation.approvalStatus.replace(/_/g, " ").toLowerCase()}`
        : ""}
    </p>
  );
}

function Frame({
  children,
  heading,
  testId,
  tone,
}: {
  children: React.ReactNode;
  heading: string;
  testId: string;
  tone: "plain" | "error" | "blocked" | "well";
}) {
  const cls =
    tone === "error"
      ? "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40"
      : tone === "blocked"
        ? "border-slate-400 border-dashed bg-slate-50 dark:border-slate-500 dark:bg-slate-900/60"
        : tone === "well"
          ? "border-emerald-400 bg-emerald-50 dark:border-emerald-600 dark:bg-emerald-950/40"
          : "border-border bg-background";
  return (
    <section className={`rounded-lg border p-4 ${cls}`} data-testid={testId} data-tone={tone}>
      <h2 className="text-sm font-semibold text-foreground">{heading}</h2>
      <div className="mt-1">{children}</div>
    </section>
  );
}
