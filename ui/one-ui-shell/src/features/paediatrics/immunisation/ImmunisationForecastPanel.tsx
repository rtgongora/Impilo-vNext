"use client";

import React from "react";
import {
  useImmunisationForecast,
  type ForecastDose,
  type ImmunisationForecast,
} from "@/hooks/queries/usePaediatricDecisionSupport";
import { actionableDoses, presentDose, sortDoses } from "./forecast-presentation";

/**
 * The child's immunisation forecast.
 *
 * The screen is organised around one question — what can be given today — and around the answer
 * being trustworthy in both directions. A dose in the "give now" list must actually immunise the
 * child, and a dose that is not in it must say why not, because "not due" without a reason is what
 * brings a child back tomorrow to be turned away again.
 *
 * Nothing here decides giveability. `actionableToday` comes from the engine and is used verbatim;
 * the interval and age-ceiling rules live in governed content because the EPI programme owns them.
 */
export interface ImmunisationForecastPanelProps {
  patientId: string;
  dateOfBirth: string | null;
  sex?: string | null;
  doses: Array<{
    vaccineCode: string | null;
    doseNumber: number | null;
    administeredAt: string | null;
    status: string | null;
  }>;
  /** True when the dose history itself could not be read — see below; this changes the meaning. */
  historyUnavailable?: boolean;
  asOf?: string | null;
}

export function ImmunisationForecastPanel({
  patientId,
  dateOfBirth,
  sex,
  doses,
  historyUnavailable = false,
  asOf,
}: ImmunisationForecastPanelProps) {
  const query = useImmunisationForecast(
    { patientId, dateOfBirth, sex, doses, asOf },
    // A forecast computed over a dose history that failed to load would report the child as due
    // for everything, including doses they have already had. Refuse to run rather than produce a
    // confident wrong answer.
    !historyUnavailable,
  );

  if (historyUnavailable) {
    return (
      <Frame testId="forecast-history-unavailable" tone="error" heading="Immunisation forecast unavailable">
        <p className="text-sm text-foreground">
          The doses already given to this child could not be read, so no forecast was run. A
          forecast built on an unreadable history would report this child as due for doses they may
          already have had.
        </p>
      </Frame>
    );
  }

  if (!dateOfBirth) {
    return (
      <Frame testId="forecast-no-dob" tone="blocked" heading="Immunisation forecast">
        <p className="text-sm text-foreground">
          This child&apos;s date of birth is not on the record. Every gate in the schedule — the
          minimum age for a dose, the interval since the last one, and the age at which a window
          closes — is a function of age, so no dose can be forecast. Record the date of birth to
          see the schedule.
        </p>
      </Frame>
    );
  }

  if (query.isLoading) {
    return (
      <Frame testId="forecast-loading" tone="plain" heading="Immunisation forecast">
        <p className="text-sm text-muted-foreground">Running the national schedule…</p>
      </Frame>
    );
  }

  if (query.isError) {
    return (
      <Frame testId="forecast-unavailable" tone="error" heading="Immunisation forecast unavailable">
        <p className="text-sm text-foreground">
          The schedule could not be run for this child. Do not read this as the child being up to
          date — no dose was evaluated. Check the child health card and the paper schedule.
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
  return <ImmunisationForecastView forecast={query.data} />;
}

/** Split out so the display can be tested against a fixture without a query client. */
export function ImmunisationForecastView({ forecast }: { forecast: ImmunisationForecast }) {
  const giveable = actionableDoses(forecast);
  const rest = sortDoses(forecast.doses.filter((d) => !d.actionableToday));

  return (
    <section className="space-y-3" data-testid="immunisation-forecast">
      <div
        className={`rounded-lg border p-4 ${
          giveable.length > 0
            ? "border-emerald-400 bg-emerald-50 dark:border-emerald-600 dark:bg-emerald-950/40"
            : "border-border bg-background"
        }`}
        data-testid="forecast-headline"
        data-actionable-count={giveable.length}
      >
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold text-foreground">
            {giveable.length > 0
              ? `${giveable.length} dose${giveable.length === 1 ? "" : "s"} to give at this contact`
              : "No dose can be given at this contact"}
          </h2>
          {forecast.ageDays !== null && (
            <span className="text-xs text-muted-foreground">{forecast.ageDays} days old</span>
          )}
        </div>
        {giveable.length === 0 && (
          <p className="mt-1 text-sm text-muted-foreground">
            This is the schedule&apos;s answer for today, not a statement that the child&apos;s
            immunisation is complete. The rows below say what is outstanding and why it is being
            withheld.
          </p>
        )}
        {forecast.provisional && (
          // The forecast itself is qualified. Showing the doses without this would present a
          // qualified answer as a definite one.
          <p className="mt-2 text-sm text-amber-800 dark:text-amber-300" data-testid="forecast-provisional">
            Provisional forecast
            {forecast.provisionalReasons.length > 0 ? `: ${forecast.provisionalReasons.join("; ")}` : "."}
          </p>
        )}
      </div>

      {giveable.length > 0 && (
        <ul className="space-y-2" data-testid="forecast-giveable">
          {sortDoses(giveable).map((dose) => (
            <DoseRow key={`${dose.series}-${dose.doseNumber}`} dose={dose} />
          ))}
        </ul>
      )}

      {rest.length > 0 && (
        <div>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Not giveable today
          </h3>
          <ul className="mt-2 space-y-2" data-testid="forecast-withheld">
            {rest.map((dose) => (
              <DoseRow key={`${dose.series}-${dose.doseNumber}`} dose={dose} />
            ))}
          </ul>
        </div>
      )}

      <p className="text-xs text-muted-foreground" data-testid="forecast-provenance">
        {forecast.scheduleId ?? "National EPI schedule"}
        {forecast.scheduleVersion ? ` · ${forecast.scheduleVersion}` : ""}
        {forecast.approvalStatus ? ` · ${forecast.approvalStatus.replace(/_/g, " ").toLowerCase()}` : ""}
        {forecast.asOf ? ` · as of ${forecast.asOf}` : ""}
      </p>
    </section>
  );
}

function DoseRow({ dose }: { dose: ForecastDose }) {
  const p = presentDose(dose);
  return (
    <li
      className={`rounded-lg border p-3 ${p.containerClass}`}
      data-testid={`dose-${dose.series}-${dose.doseNumber}`}
      data-tone={p.tone}
      data-status={dose.status}
      data-actionable={String(p.actionable)}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-semibold text-foreground">{p.label}</span>
        <span className={`rounded px-2 py-0.5 text-xs font-semibold ${p.chipClass}`}>{p.chipLabel}</span>
      </div>
      <p className="mt-1 text-sm text-foreground">{p.detail}</p>
    </li>
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
  tone: "plain" | "error" | "blocked";
}) {
  const cls =
    tone === "error"
      ? "border-rose-400 bg-rose-50 dark:border-rose-600 dark:bg-rose-950/40"
      : tone === "blocked"
        ? "border-slate-400 border-dashed bg-slate-50 dark:border-slate-500 dark:bg-slate-900/60"
        : "border-border bg-background";
  return (
    <section className={`rounded-lg border p-4 ${cls}`} data-testid={testId}>
      <h2 className="text-sm font-semibold text-foreground">{heading}</h2>
      <div className="mt-1">{children}</div>
    </section>
  );
}
