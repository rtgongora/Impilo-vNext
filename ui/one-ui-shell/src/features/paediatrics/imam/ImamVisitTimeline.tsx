"use client";

import React from "react";
import type { ImamVisit } from "@/hooks/queries/useImam";
import { formatMuac, formatWeight, oedemaLabel, TONE_BADGE, triStateLabel, visitVerdict } from "./imam-presentation";

/**
 * The reviews, in order.
 *
 * An empty timeline says so in the strongest terms available. On an open episode "no reviews
 * recorded" is not a neutral state — it is a malnourished child nobody has seen since they were
 * enrolled, and rendering that as a blank list is the exact shape of failure this whole feature
 * exists to prevent.
 */
export interface ImamVisitTimelineProps {
  visits: ImamVisit[];
  reviewIntervalDays: number | null;
}

export function ImamVisitTimeline({ visits, reviewIntervalDays }: ImamVisitTimelineProps) {
  if (visits.length === 0) {
    return (
      <section
        className="rounded-lg border border-red-500 bg-red-50 p-4"
        data-testid="imam-visits-empty"
      >
        <h3 className="text-sm font-semibold text-red-900">No review has been recorded</h3>
        <p className="mt-1 text-xs text-red-900">
          Nothing has been recorded for this child since they were enrolled. This is not an empty
          list — it is a child in treatment that nobody has reviewed.
        </p>
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-border bg-background p-4" data-testid="imam-visits">
      <h3 className="text-sm font-semibold text-foreground">
        Reviews
        {reviewIntervalDays && (
          <span className="ml-1 font-normal text-muted-foreground">
            (every {reviewIntervalDays} days)
          </span>
        )}
      </h3>

      <ol className="mt-3 space-y-3">
        {[...visits]
          .sort((a, b) => b.visitNumber - a.visitNumber)
          .map((visit) => {
            const verdict = visitVerdict(visit);
            return (
              <li
                key={visit.id}
                className="rounded-md border border-border p-3"
                data-testid={`imam-visit-${visit.visitNumber}`}
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-medium text-foreground">
                    Review {visit.visitNumber} ·{" "}
                    {visit.attended
                      ? (visit.visitDate?.slice(0, 10) ?? "date not recorded")
                      : `missed${visit.scheduledFor ? ` (scheduled ${visit.scheduledFor})` : ""}`}
                  </p>
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${TONE_BADGE[verdict.tone]}`}
                    data-testid={`imam-visit-${visit.visitNumber}-verdict`}
                  >
                    {verdict.label}
                  </span>
                </div>

                {visit.attended && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    {formatMuac(visit.muacCm)} · {formatWeight(visit.weightKg)} ·{" "}
                    {oedemaLabel(visit.oedema)} ·{" "}
                    {triStateLabel(visit.dangerSignsPresent, "Danger sign present", "No danger signs")}
                    {typeof visit.rutfSachetsIssued === "number" && (
                      <>
                        {" "}
                        · {visit.rutfSachetsIssued} {visit.rutfProductCode ?? "sachets"} issued
                        {typeof visit.rutfSachetsRecommended === "number" &&
                          visit.rutfSachetsRecommended !== visit.rutfSachetsIssued && (
                            <span className="text-amber-700">
                              {" "}
                              ({visit.rutfSachetsRecommended} recommended)
                            </span>
                          )}
                      </>
                    )}
                  </p>
                )}

                {visit.assessmentNote && (
                  <p className="mt-1 whitespace-pre-line text-xs text-muted-foreground">
                    {visit.assessmentNote}
                  </p>
                )}
                {visit.clinicalNote && (
                  <p className="mt-1 text-xs italic text-foreground">{visit.clinicalNote}</p>
                )}
              </li>
            );
          })}
      </ol>
    </section>
  );
}
