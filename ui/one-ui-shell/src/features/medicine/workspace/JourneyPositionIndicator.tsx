"use client";

import { Loader2, MapPin } from "lucide-react";
import {
  ADULT_JOURNEY_STEP_KEYS,
  stepLabel,
  useAdultJourneyPosition,
  useRecordAdultJourneyPosition,
  type AdultJourneyStepKey,
} from "@/hooks/queries/useAdultJourneyPosition";

export interface JourneyPositionIndicatorProps {
  patientId: string;
  /** Visit anchor — at least one of journey or encounter should be supplied for writes. */
  journeyId?: string;
  encounterId?: string;
}

export function JourneyPositionIndicator({
  patientId,
  journeyId,
  encounterId,
}: JourneyPositionIndicatorProps) {
  const position = useAdultJourneyPosition(patientId, { journeyId, encounterId });
  const record = useRecordAdultJourneyPosition(patientId);

  const current = position.data?.data?.position ?? null;
  const currentIndex = current?.step_index ?? 0;
  const nextKey: AdultJourneyStepKey | null =
    currentIndex >= 0 && currentIndex < ADULT_JOURNEY_STEP_KEYS.length
      ? ADULT_JOURNEY_STEP_KEYS[currentIndex]
      : ADULT_JOURNEY_STEP_KEYS[0];
  const hasAnchor = Boolean(journeyId || encounterId);

  function advance() {
    if (!nextKey || !hasAnchor) return;
    const stepIndex = ADULT_JOURNEY_STEP_KEYS.indexOf(nextKey) + 1;
    record.mutate({
      subject_cpid: patientId,
      step_key: nextKey,
      step_index: stepIndex,
      ...(journeyId ? { journey_id: journeyId } : {}),
      ...(encounterId ? { encounter_id: encounterId } : {}),
    });
  }

  if (position.isLoading) {
    return (
      <p className="text-xs text-muted-foreground flex items-center gap-1" data-testid="journey-position-loading">
        <Loader2 className="w-3 h-3 animate-spin" /> Reading pathway position…
      </p>
    );
  }

  if (position.isError) {
    return (
      <p className="text-xs text-danger" data-testid="journey-position-unavailable">
        Pathway position could not be read — do not infer where this visit is on the adult medicine
        pathway.
      </p>
    );
  }

  return (
    <div
      className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2"
      data-testid="journey-position-indicator"
    >
      <MapPin className="w-4 h-4 text-primary shrink-0" />
      <div className="text-sm">
        {current ? (
          <>
            <span className="font-medium" data-testid="journey-position-current">
              Step {current.step_index}/17 — {stepLabel(current.step_key)}
            </span>
            {current.recorded_at && (
              <span className="ml-2 text-xs text-muted-foreground">
                recorded {new Date(current.recorded_at).toLocaleString()}
              </span>
            )}
          </>
        ) : (
          <span className="text-muted-foreground" data-testid="journey-position-none">
            No pathway position recorded yet.
          </span>
        )}
      </div>
      {hasAnchor && nextKey && currentIndex < 17 && (
        <button
          type="button"
          onClick={advance}
          disabled={record.isPending}
          className="ml-auto rounded bg-primary px-2 py-1 text-xs text-primary-foreground disabled:opacity-60"
          data-testid="journey-position-advance"
        >
          {record.isPending ? (
            <Loader2 className="w-3 h-3 animate-spin inline" />
          ) : current ? (
            `Advance to ${stepLabel(nextKey)}`
          ) : (
            `Start at ${stepLabel(nextKey)}`
          )}
        </button>
      )}
      {!hasAnchor && (
        <span className="text-xs text-muted-foreground ml-auto">
          Open from an active visit to advance.
        </span>
      )}
      {record.isError && (
        <p className="w-full text-xs text-danger" data-testid="journey-position-write-failed">
          Position was not saved.
        </p>
      )}
    </div>
  );
}
