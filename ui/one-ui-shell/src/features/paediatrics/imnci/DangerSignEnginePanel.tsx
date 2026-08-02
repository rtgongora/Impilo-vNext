"use client";

import React from "react";
import { useDangerSigns, type DangerSignAssessment } from "@/hooks/queries/usePaediatricDecisionSupport";
import { observationList } from "./imnci-presentation";

/**
 * The danger-sign engine's verdict — ETAT emergency signs, the IMNCI general danger signs, and the
 * young-infant signs of possible serious bacterial infection.
 *
 * Distinct from `DangerSignBanner`, which prompts the clinician from local form state while they
 * work. This is the governed evaluation, and the two must never be confused: the banner says what
 * has not been filled in yet, this says what the rules make of what has.
 *
 * **`incomplete` is rendered before, and more prominently than, the absence of alerts.** A
 * danger-sign screen that raised nothing is the single most reassuring output this system can
 * produce, and it is only reassurance if the questions were actually asked. The engine reports
 * exactly which signs nobody looked at; showing "no danger signs" over the top of that would be a
 * confident all-clear built on unasked questions, which the engine's own documentation names as
 * the most dangerous output it could give.
 */
export interface DangerSignEnginePanelProps {
  patientId: string;
  ageDays: number | null;
  findings: Record<string, unknown>;
  enabled?: boolean;
}

export function DangerSignEnginePanel({
  patientId,
  ageDays,
  findings,
  enabled = true,
}: DangerSignEnginePanelProps) {
  const query = useDangerSigns({ patientId, ageDays, findings }, enabled);

  if (ageDays === null) {
    return (
      <Frame testId="danger-signs-no-age" tone="blocked" heading="Danger signs">
        <p className="text-sm text-foreground">
          The danger-sign rules are age-banded — the young-infant signs of serious bacterial
          infection apply only under two months — so without a date of birth no rule can be
          evaluated.
        </p>
      </Frame>
    );
  }

  if (query.isLoading) {
    return (
      <Frame testId="danger-signs-loading" tone="plain" heading="Danger signs">
        <p className="text-sm text-muted-foreground">Evaluating the danger-sign rules…</p>
      </Frame>
    );
  }

  if (query.isError) {
    return (
      <Frame testId="danger-signs-unavailable" tone="error" heading="Danger-sign evaluation unavailable">
        <p className="text-sm text-foreground">
          The danger-sign rules could not be evaluated. No sign was checked, which is not the same
          as no sign being present. Check the emergency and general danger signs directly.
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
  return <DangerSignEngineView assessment={query.data} />;
}

/** Split out so the display can be tested against a fixture without a query client. */
export function DangerSignEngineView({ assessment }: { assessment: DangerSignAssessment }) {
  const hasAlerts = assessment.alerts.length > 0;

  return (
    <section className="space-y-3" data-testid="danger-signs">
      {hasAlerts && (
        <ul className="space-y-2" data-testid="danger-sign-alerts">
          {assessment.alerts.map((alert) => (
            <li
              key={alert.id}
              className="rounded-lg border border-rose-400 bg-rose-50 p-3 dark:border-rose-600 dark:bg-rose-950/40"
              data-testid={`danger-alert-${alert.id}`}
              data-severity={alert.severity ?? ""}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm font-semibold text-foreground">{alert.name ?? alert.id}</span>
                <span className="rounded bg-rose-600 px-2 py-0.5 text-xs font-semibold text-white">
                  {alert.severity ?? "Alert"}
                </span>
              </div>
              {alert.action && <p className="mt-1 text-sm font-medium text-foreground">{alert.action}</p>}
              {!alert.overridable && (
                <p className="mt-1 text-xs font-semibold text-rose-700 dark:text-rose-300">
                  This alert cannot be overridden.
                </p>
              )}
              {alert.triggeringFindings.length > 0 && (
                <p className="mt-1 text-xs text-muted-foreground">
                  Triggered by {observationList(alert.triggeringFindings)}.
                </p>
              )}
              {alert.source && (
                <p className="mt-1 text-xs text-muted-foreground">
                  {alert.source}
                  {alert.contentVersion ? ` · ${alert.contentVersion}` : ""}
                  {alert.approvalStatus
                    ? ` · ${alert.approvalStatus.replace(/_/g, " ").toLowerCase()}`
                    : ""}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {assessment.incomplete ? (
        // Deliberately rendered whether or not alerts fired, and never replaced by an all-clear.
        // A screen that raised nothing is only reassurance if the questions were asked.
        <Frame
          testId="danger-signs-incomplete"
          tone="blocked"
          heading={
            hasAlerts
              ? "Danger-sign screen incomplete"
              : "No danger sign raised — but the screen is incomplete"
          }
        >
          <p className="text-sm text-foreground">
            {assessment.unassessedSigns.length > 0
              ? `${assessment.unassessedSigns.length} sign${assessment.unassessedSigns.length === 1 ? " was" : "s were"} not assessed: ${observationList(assessment.unassessedSigns)}.`
              : "Some signs were not assessed."}
          </p>
          <p className="mt-2 text-sm text-foreground">
            {hasAlerts
              ? "Completing these may raise further alerts. What is shown above is what the rules could reach."
              : "This is not an all-clear. A danger sign that nobody looked for has not been ruled out."}
          </p>
        </Frame>
      ) : (
        !hasAlerts && (
          <Frame testId="danger-signs-clear" tone="well" heading="No danger sign present">
            <p className="text-sm text-foreground">
              Every danger sign in the rule set was assessed and none is present.
            </p>
          </Frame>
        )
      )}

      {assessment.referralRequired && (
        <p className="text-sm font-semibold text-rose-700 dark:text-rose-300" data-testid="danger-signs-referral">
          Referral required after pre-referral treatment.
        </p>
      )}
    </section>
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
