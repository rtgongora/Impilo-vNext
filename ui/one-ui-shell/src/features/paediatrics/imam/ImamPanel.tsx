"use client";

import React from "react";
import { useImamEpisode, useImamEpisodes } from "@/hooks/queries/useImam";
import { ImamEnrolmentCard } from "./ImamEnrolmentCard";
import { ImamEpisodePanel } from "./ImamEpisodePanel";
import { classificationLabel, programmeLabel } from "./imam-presentation";

/**
 * A child's nutrition treatment: the open episode if there is one, the way in if there is not.
 *
 * A failed read is shown as a failed read. An empty state here reads as "this child is in no
 * nutrition programme", which is a clinical statement, and returning it because a query errored
 * is the defect that hid three broken verticals in this codebase for months.
 */
export interface ImamPanelProps {
  patientId: string;
  journeyId?: string | null;
  encounterId?: string | null;
  facilityId?: string | null;
  ageDays?: number | null;
  suggestedMuacCm?: number | null;
  suggestedWeightKg?: number | null;
}

export function ImamPanel({
  patientId,
  journeyId,
  encounterId,
  facilityId,
  ageDays,
  suggestedMuacCm,
  suggestedWeightKg,
}: ImamPanelProps) {
  const episodes = useImamEpisodes(patientId);
  const active = (episodes.data ?? []).find((episode) => episode.status === "ACTIVE") ?? null;
  // The list endpoint carries the episode header only. The reviews and the live assessment —
  // attendance, discharge readiness, the ration due — come from the detail read, which
  // re-evaluates against the protocol on every load, because a child becomes a defaulter by the
  // passage of time rather than by anyone writing something down.
  const detail = useImamEpisode(active?.id ?? null);
  const closed = (episodes.data ?? []).filter((episode) => episode.status !== "ACTIVE");

  if (episodes.isLoading) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="imam-loading">
        Loading nutrition treatment…
      </p>
    );
  }

  if (episodes.isError) {
    const body = episodes.error as { message?: string } | undefined;
    return (
      <div className="rounded-lg border border-red-500 bg-red-50 p-4" data-testid="imam-unavailable">
        <p className="text-sm font-semibold text-red-900">
          The nutrition treatment record could not be loaded
        </p>
        <p className="mt-1 text-xs text-red-900">
          {body?.message ??
            "Do not read this as the child being in no nutrition programme — the record could not be read at all."}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4" data-testid="imam-panel">
      {active ? (
        detail.isLoading ? (
          <p className="text-sm text-muted-foreground">Loading the episode…</p>
        ) : detail.isError ? (
          <div className="rounded-lg border border-red-500 bg-red-50 p-4" data-testid="imam-detail-unavailable">
            <p className="text-sm font-semibold text-red-900">
              This child is in {programmeLabel(active.programme)}, but the episode could not be loaded
            </p>
            <p className="mt-1 text-xs text-red-900">
              Enrolled {active.enrolledAt?.slice(0, 10) ?? "—"}. The reviews and the discharge
              assessment are unavailable; nothing here should be read as the child having none.
            </p>
          </div>
        ) : (
          detail.data && <ImamEpisodePanel episode={detail.data} />
        )
      ) : (
        <>
          <div
            className="rounded-lg border border-border bg-muted p-3"
            data-testid="imam-no-active-episode"
          >
            <p className="text-sm text-foreground">This child is not in a nutrition programme.</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              A child classified with acute malnutrition should be enrolled today — the classification
              is an instruction, and until they are enrolled nothing carries it out.
            </p>
          </div>
          <ImamEnrolmentCard
            patientId={patientId}
            journeyId={journeyId}
            encounterId={encounterId}
            facilityId={facilityId}
            ageDays={ageDays}
            suggestedMuacCm={suggestedMuacCm}
            suggestedWeightKg={suggestedWeightKg}
          />
        </>
      )}

      {closed.length > 0 && (
        <section className="rounded-lg border border-border bg-background p-4" data-testid="imam-past-episodes">
          <h3 className="text-sm font-semibold text-foreground">Previous episodes</h3>
          <ul className="mt-2 space-y-2">
            {closed.map((episode) => (
              <li key={episode.id} className="rounded-md border border-border p-2 text-xs">
                <p className="text-foreground">
                  {programmeLabel(episode.programme)} · {episode.enrolledAt?.slice(0, 10) ?? "—"} to{" "}
                  {episode.outcomeAt?.slice(0, 10) ?? "—"} · <strong>{episode.outcome}</strong>
                </p>
                <p className="mt-0.5 text-muted-foreground">
                  {episode.sourceClassification && (
                    <>Admitted on {classificationLabel(episode.sourceClassification)}. </>
                  )}
                  Discharge criteria{" "}
                  {episode.dischargeCriteriaMet === null
                    ? "could not be evaluated"
                    : episode.dischargeCriteriaMet
                      ? "met"
                      : "not met"}
                  {episode.dischargeOverrideReason && (
                    <> — discharged anyway: {episode.dischargeOverrideReason}</>
                  )}
                </p>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
