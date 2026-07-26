"use client";

import React from "react";
import type { ImamEpisode } from "@/hooks/queries/useImam";
import { DischargeCriteriaList } from "./DischargeCriteriaList";
import { ImamOutcomeForm } from "./ImamOutcomeForm";
import { ImamReviewForm } from "./ImamReviewForm";
import { ImamVisitTimeline } from "./ImamVisitTimeline";
import {
  attendanceBanner,
  classificationLabel,
  formatMuac,
  formatWeight,
  oedemaLabel,
  programmeLabel,
  responseLabel,
  responseTone,
  TONE_BADGE,
  TONE_CLASSES,
} from "./imam-presentation";

/** One treatment episode: what admitted the child, how it is going, and what happens next. */
export function ImamEpisodePanel({ episode }: { episode: ImamEpisode }) {
  const assessment = episode.assessment;
  const banner = attendanceBanner(assessment);
  const closed = episode.status !== "ACTIVE";

  return (
    <div className="space-y-4" data-testid="imam-episode-panel">
      <header className="rounded-lg border border-border bg-background p-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h2 className="text-base font-semibold text-foreground">
              {programmeLabel(episode.programme)}
            </h2>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Enrolled {episode.enrolledAt?.slice(0, 10) ?? "—"}
              {episode.sourceClassification && (
                <> on {classificationLabel(episode.sourceClassification)}</>
              )}
              {assessment && <> · day {assessment.daysInProgramme} of treatment</>}
            </p>
          </div>
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
              closed ? TONE_BADGE.neutral : TONE_BADGE.positive
            }`}
            data-testid="imam-episode-status"
          >
            {closed ? `Closed — ${episode.outcome}` : "Open"}
          </span>
        </div>

        <dl className="mt-3 grid gap-x-4 gap-y-2 text-xs sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <dt className="text-muted-foreground">Admitted on</dt>
            <dd className="text-foreground">{episode.admissionCriterion ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Arm circumference at admission</dt>
            <dd className="text-foreground">{formatMuac(episode.admissionMuacCm)}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Weight at admission</dt>
            <dd className="text-foreground">{formatWeight(episode.admissionWeightKg)}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Oedema at admission</dt>
            <dd className="text-foreground">{oedemaLabel(episode.admissionOedema)}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Review every</dt>
            <dd className="text-foreground">
              {episode.reviewIntervalDays ? `${episode.reviewIntervalDays} days` : "—"}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">Next review due</dt>
            <dd className="text-foreground">{episode.nextReviewDue ?? "—"}</dd>
          </div>
          {assessment && (
            <div>
              <dt className="text-muted-foreground">Response</dt>
              <dd className="text-foreground">
                {responseLabel(assessment.responseStatus)}
                {typeof assessment.weightGainGPerKgPerDay === "number" && (
                  <> · {assessment.weightGainGPerKgPerDay} g/kg/day</>
                )}
              </dd>
            </div>
          )}
          <div>
            <dt className="text-muted-foreground">Protocol</dt>
            <dd className="text-foreground">
              {episode.contentVersion ?? "—"}
              {episode.approvalStatus && (
                <span className="ml-1 text-muted-foreground">({episode.approvalStatus})</span>
              )}
            </dd>
          </div>
        </dl>

        {episode.approvalStatus === "ENGINEERING_SEED" && (
          <p className="mt-3 rounded-md border border-slate-400 bg-slate-50 p-2 text-xs text-slate-800">
            This protocol is an engineering seed pending MoHCC ratification. Confirm the cut-offs,
            ration and discharge criteria against the national IMAM guideline before acting on them.
          </p>
        )}
      </header>

      {banner && !closed && (
        <div className={`rounded-lg border p-3 ${TONE_CLASSES[banner.tone]}`} data-testid="imam-attendance-banner">
          <p className="text-sm font-semibold">{banner.title}</p>
          <p className="mt-1 text-xs">{banner.detail}</p>
        </div>
      )}

      {assessment?.escalationAction && !closed && (
        <div className={`rounded-lg border p-3 ${TONE_CLASSES.critical}`} data-testid="imam-escalation">
          <p className="text-sm font-semibold">This child is in the wrong programme</p>
          <p className="mt-1 text-xs">{assessment.escalationAction}</p>
        </div>
      )}

      {assessment && assessment.responseStatus === "NON_RESPONDER" && !closed && (
        <div className={`rounded-lg border p-3 ${TONE_CLASSES[responseTone("NON_RESPONDER")]}`}>
          <p className="text-sm font-semibold">Not responding to treatment</p>
          <p className="mt-1 text-xs">
            Failure to recover within the maximum stay is itself the finding. Investigate for
            tuberculosis, HIV and other underlying illness rather than continuing the same ration.
          </p>
        </div>
      )}

      {closed ? (
        <section className="rounded-lg border border-border bg-background p-4" data-testid="imam-outcome-summary">
          <h3 className="text-sm font-semibold text-foreground">Outcome</h3>
          <p className="mt-1 text-sm text-foreground">
            {episode.outcome} on {episode.outcomeAt?.slice(0, 10) ?? "—"}
            {episode.outcomeBy && <> · recorded by {episode.outcomeBy}</>}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            Discharge criteria:{" "}
            {episode.dischargeCriteriaMet === null
              ? "could not be evaluated when this episode was closed"
              : episode.dischargeCriteriaMet
                ? "met"
                : "not met"}
          </p>
          {episode.dischargeOverrideReason && (
            <div
              className="mt-2 rounded-md border border-amber-500 bg-amber-50 p-2"
              data-testid="imam-override-recorded"
            >
              <p className="text-xs font-semibold text-amber-900">
                Discharged without meeting the criteria — reason recorded
              </p>
              <p className="mt-1 text-xs text-amber-900">{episode.dischargeOverrideReason}</p>
            </div>
          )}
          {episode.dischargeCriteriaDetail && (
            <pre className="mt-2 whitespace-pre-wrap rounded-md bg-muted p-2 text-xs text-foreground">
              {episode.dischargeCriteriaDetail}
            </pre>
          )}
        </section>
      ) : (
        <DischargeCriteriaList assessment={assessment} />
      )}

      {assessment && assessment.actions.length > 0 && !closed && (
        <section className="rounded-lg border border-border bg-background p-4" data-testid="imam-actions">
          <h3 className="text-sm font-semibold text-foreground">What to do at this contact</h3>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {assessment.actions.map((action) => (
              <li key={action} className="text-sm text-foreground">
                {action}
              </li>
            ))}
          </ul>
        </section>
      )}

      <ImamVisitTimeline
        visits={episode.visits}
        reviewIntervalDays={episode.reviewIntervalDays}
      />

      {!closed && (
        <div className="grid gap-4 lg:grid-cols-2">
          <ImamReviewForm episode={episode} assessment={assessment} />
          <ImamOutcomeForm episode={episode} assessment={assessment} />
        </div>
      )}
    </div>
  );
}
