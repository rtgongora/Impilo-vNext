"use client";

/**
 * Episode FSM + location controls — wires hooks that already existed without a surface.
 * Arrive anchors the journey; transition applies allowed next states; location confirms
 * physical movement (resets LOCATION_UNKNOWN clock).
 */

import { useState } from "react";
import { bffErrorMessage } from "@/lib/bff-errors";
import type { EmergencyEpisodeRow } from "@/hooks/queries/useEmergencyEpisode";

const LOCATIONS = [
  "INBOUND",
  "EMERGENCY_UNIT",
  "RESUS",
  "TRIAGE",
  "OBSERVATION",
  "IMAGING",
  "THEATRE",
  "WARD",
  "OTHER",
] as const;

const OPEN_TRANSITIONS: Record<string, string[]> = {
  PRE_ARRIVAL: ["OPEN_UNTRIAGED", "CLOSED_DECLINED_CARE"],
  OPEN_UNTRIAGED: ["OPEN_IN_CARE", "CLOSED_LEFT_BEFORE_ASSESSMENT", "CLOSED_DIED"],
  OPEN_IN_CARE: [
    "CLOSED_DISCHARGED",
    "CLOSED_DIED",
    "CLOSED_LEFT_AGAINST_ADVICE",
    "CLOSED_ABSCONDED",
    "CLOSED_DECLINED_CARE",
  ],
  OPEN_AWAITING_ACCEPTANCE: ["OPEN_IN_CARE", "CLOSED_DIED"],
};

export interface EpisodeCareActionsPanelProps {
  episode: EmergencyEpisodeRow;
  onArrive: (body: { journeyId: string; encounterId?: number }) => Promise<unknown>;
  onTransition: (body: { state: string; outcome?: string }) => Promise<unknown>;
  onConfirmLocation: (body: { location: string }) => Promise<unknown>;
  isMutating?: boolean;
}

export function EpisodeCareActionsPanel({
  episode,
  onArrive,
  onTransition,
  onConfirmLocation,
  isMutating,
}: EpisodeCareActionsPanelProps) {
  const state = String(episode.state ?? "");
  const [journeyId, setJourneyId] = useState(String(episode.journey_id ?? ""));
  const [nextState, setNextState] = useState(OPEN_TRANSITIONS[state]?.[0] ?? "");
  const [location, setLocation] = useState(String(episode.current_location ?? "EMERGENCY_UNIT"));
  const [error, setError] = useState<string | null>(null);

  const allowed = OPEN_TRANSITIONS[state] ?? [];

  const run = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(bffErrorMessage(e, "this episode action"));
    }
  };

  return (
    <div className="space-y-4 rounded border p-4" data-testid="episode-care-actions">
      <h3 className="text-sm font-semibold">Care actions</h3>

      {state === "PRE_ARRIVAL" && (
        <div className="space-y-2" data-testid="episode-arrive">
          <p className="text-xs text-muted-foreground">
            Arrival anchors the PCT journey and moves the episode to untriaged.
          </p>
          <div className="flex flex-wrap gap-2">
            <input
              className="min-w-[12rem] flex-1 rounded border px-2 py-1 text-sm"
              placeholder="Journey id"
              value={journeyId}
              onChange={(e) => setJourneyId(e.target.value)}
              data-testid="arrive-journey-id"
            />
            <button
              type="button"
              className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
              disabled={!journeyId.trim() || isMutating}
              data-testid="arrive-submit"
              onClick={() => run(() => onArrive({ journeyId: journeyId.trim() }))}
            >
              Record arrival
            </button>
          </div>
        </div>
      )}

      {allowed.length > 0 && (
        <div className="space-y-2" data-testid="episode-transition">
          <p className="text-xs text-muted-foreground">Allowed next states from {state.replaceAll("_", " ")}.</p>
          <div className="flex flex-wrap gap-2">
            <select
              className="rounded border px-2 py-1 text-sm"
              value={nextState || allowed[0]}
              onChange={(e) => setNextState(e.target.value)}
              aria-label="Next episode state"
              data-testid="transition-state"
            >
              {allowed.map((s) => (
                <option key={s} value={s}>
                  {s.replaceAll("_", " ")}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="rounded border px-3 py-1 text-sm disabled:opacity-50"
              disabled={isMutating}
              data-testid="transition-submit"
              onClick={() =>
                run(() =>
                  onTransition({
                    state: nextState || allowed[0],
                    outcome: (nextState || allowed[0]).startsWith("CLOSED_")
                      ? nextState || allowed[0]
                      : undefined,
                  }),
                )
              }
            >
              Apply transition
            </button>
          </div>
        </div>
      )}

      <div className="space-y-2" data-testid="episode-location">
        <p className="text-xs text-muted-foreground">
          Confirming location resets the LOCATION_UNKNOWN alert clock.
        </p>
        <div className="flex flex-wrap gap-2">
          <select
            className="rounded border px-2 py-1 text-sm"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            aria-label="Episode location"
            data-testid="location-select"
          >
            {LOCATIONS.map((loc) => (
              <option key={loc} value={loc}>
                {loc.replaceAll("_", " ")}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="rounded border px-3 py-1 text-sm disabled:opacity-50"
            disabled={isMutating}
            data-testid="location-submit"
            onClick={() => run(() => onConfirmLocation({ location }))}
          >
            Confirm location
          </button>
        </div>
      </div>

      {error ? (
        <p className="text-sm text-danger" role="alert" data-testid="care-actions-error">
          {error}
        </p>
      ) : null}
    </div>
  );
}
