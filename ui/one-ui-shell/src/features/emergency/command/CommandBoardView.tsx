"use client";

/**
 * The emergency command view: what is happening in this facility's emergency lane right now.
 *
 * Presentational on purpose — every value arrives already decided by pct-service, and this component
 * takes them as props so its degradation behaviour can be tested without a network. The page that
 * wraps it supplies the data.
 *
 * The behaviour worth stating: a source that could not be read renders as a named blind spot, never
 * as a zero. "0 critical alerts" and "the alert service did not answer" look identical on a tile
 * that only knows how to display a number, and on a safety board that difference is the whole point.
 */

import Link from "next/link";
import { StalenessBadge } from "shared-ui";

import type {
  EmergencyAlertRow,
  EmergencyCommandBoard,
  EmergencyEpisodeRow,
} from "@/hooks/queries/useEmergencyEpisode";

import { humanise, orderAlerts, standingOf, tallyEpisodeStates } from "./boardModel";

export interface CommandBoardViewProps {
  board?: EmergencyCommandBoard;
  isLoading: boolean;
  /** The whole request failed — distinct from an individual source failing inside a live response. */
  isError: boolean;
  onAcknowledge: (alert: EmergencyAlertRow) => void;
  onRespond: (alert: EmergencyAlertRow) => void;
  onClose: (alert: EmergencyAlertRow) => void;
  pendingAlertId?: string | null;
}

function failureFor(board: EmergencyCommandBoard | undefined, source: string) {
  return board?.failures?.find((failure) => failure.source === source);
}

function Tile({
  label,
  children,
  blindSpot,
}: {
  label: string;
  children: React.ReactNode;
  blindSpot?: { message: string };
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4" data-testid={`command-tile-${label}`}>
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-foreground">{label}</h3>
        {blindSpot ? <StalenessBadge state="UNAVAILABLE" subject={label.toLowerCase()} /> : null}
      </div>
      {blindSpot ? (
        <p className="mt-2 text-xs text-danger" data-testid={`command-tile-${label}-blind`}>
          {blindSpot.message}
        </p>
      ) : (
        <div className="mt-2">{children}</div>
      )}
    </div>
  );
}

export function CommandBoardView({
  board,
  isLoading,
  isError,
  onAcknowledge,
  onRespond,
  onClose,
  pendingAlertId,
}: CommandBoardViewProps) {
  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Loading the command board…</p>;
  }

  if (isError || !board) {
    return (
      <p className="rounded-lg border border-danger/30 bg-danger-soft p-4 text-sm text-danger" data-testid="command-board-error">
        The command board could not be retrieved. Do not treat this as an absence of emergency
        activity at this facility.
      </p>
    );
  }

  const summaryFailure = failureFor(board, "summary");
  const alertsFailure = failureFor(board, "alerts");
  const episodesFailure = failureFor(board, "episodes");

  const alerts: EmergencyAlertRow[] = board.items?.alerts ?? [];
  const episodes: EmergencyEpisodeRow[] = board.items?.episodes ?? [];
  const standing = standingOf(alerts);
  const { tallies, source } = tallyEpisodeStates({
    summary: board.items?.summary,
    episodes: episodesFailure ? [] : episodes,
  });

  return (
    <div className="space-y-6" data-testid="command-board">
      {board.meta?.degraded ? (
        <p
          className="rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-xs text-warning-foreground"
          data-testid="command-board-degraded"
        >
          {board.failures.length === 1 ? "One source" : `${board.failures.length} sources`} could not
          be read. Each is named below; the rest of this board is current.
        </p>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-3">
        <Tile
          label="Episodes by state"
          blindSpot={summaryFailure && episodesFailure ? { message: summaryFailure.message } : undefined}
        >
          {tallies.length === 0 ? (
            <p className="text-xs text-muted-foreground">No open episodes at this facility.</p>
          ) : (
            <ul className="space-y-1">
              {tallies.map((tally) => (
                <li key={tally.state} className="flex justify-between text-sm">
                  <span className="text-muted-foreground">{humanise(tally.state)}</span>
                  <span className="font-mono tabular-nums text-foreground">{tally.count}</span>
                </li>
              ))}
            </ul>
          )}
          {source === "client-board" ? (
            <p className="mt-2 text-[11px] text-muted-foreground" data-testid="command-tally-source">
              Counted from the episodes loaded here, not from the facility summary.
            </p>
          ) : null}
        </Tile>

        <Tile label="Alert standing" blindSpot={alertsFailure ? { message: alertsFailure.message } : undefined}>
          <ul className="space-y-1 text-sm">
            <li className="flex justify-between">
              <span className="text-muted-foreground">Unacknowledged</span>
              <span className="font-mono tabular-nums">{standing.unacknowledged}</span>
            </li>
            <li className="flex justify-between">
              <span className="text-muted-foreground">Seen, no response</span>
              <span className="font-mono tabular-nums">{standing.acknowledgedNotResponded}</span>
            </li>
            <li className="flex justify-between">
              <span className="text-muted-foreground">Responded, still open</span>
              <span className="font-mono tabular-nums">{standing.respondedStillOpen}</span>
            </li>
          </ul>
        </Tile>

        <Tile label="Open episodes" blindSpot={episodesFailure ? { message: episodesFailure.message } : undefined}>
          <p className="font-mono text-2xl tabular-nums text-foreground">{episodes.length}</p>
          <Link href="/clinical/emergency/board" className="text-xs text-primary underline">
            Open the episode board
          </Link>
        </Tile>
      </div>

      <section>
        <h2 className="mb-2 text-sm font-semibold text-foreground">Alert queue</h2>
        {alertsFailure ? (
          <p className="rounded-lg border border-danger/30 bg-danger-soft p-3 text-sm text-danger" data-testid="alert-queue-blind">
            {alertsFailure.message}
          </p>
        ) : alerts.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border px-3 py-4 text-center text-xs text-muted-foreground" data-testid="alert-queue-empty">
            No emergency alerts have been raised at this facility.
          </p>
        ) : (
          <ul className="space-y-2" data-testid="alert-queue">
            {orderAlerts(alerts).map((alert) => {
              const alertId = String(alert.alert_id);
              const isClosed = alert.status === "CLOSED" || Boolean(alert.closed_at);
              const busy = pendingAlertId === alertId;
              return (
                <li
                  key={alertId}
                  className="rounded-lg border border-border bg-card p-3"
                  data-testid="alert-row"
                  data-severity={String(alert.severity ?? "UNKNOWN")}
                >
                  <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                    <span className="text-sm font-semibold text-foreground">
                      {humanise(alert.alert_type)}
                    </span>
                    <span className="rounded-full border border-border px-2 py-0.5 text-[11px] uppercase tracking-wide text-muted-foreground">
                      {String(alert.severity ?? "unrated")}
                    </span>
                    {alert.raised_at ? (
                      <StalenessBadge
                        state="LIVE"
                        asOf={String(alert.raised_at)}
                        subject={`this ${humanise(alert.alert_type).toLowerCase()} alert`}
                      />
                    ) : null}
                    <span className="ml-auto text-xs text-muted-foreground">
                      {isClosed
                        ? "Closed"
                        : alert.responded_at
                          ? "Responded"
                          : alert.acknowledged_at
                            ? "Acknowledged"
                            : "Awaiting acknowledgement"}
                    </span>
                  </div>

                  {alert.detail ? (
                    <p className="mt-1 text-xs text-muted-foreground">{String(alert.detail)}</p>
                  ) : null}

                  <div className="mt-2 flex flex-wrap gap-2">
                    <button
                      type="button"
                      className="rounded border border-border px-2 py-1 text-xs disabled:opacity-40"
                      disabled={busy || isClosed || Boolean(alert.acknowledged_at)}
                      onClick={() => onAcknowledge(alert)}
                      data-testid="alert-acknowledge"
                    >
                      Acknowledge
                    </button>
                    <button
                      type="button"
                      className="rounded border border-border px-2 py-1 text-xs disabled:opacity-40"
                      disabled={busy || isClosed}
                      onClick={() => onRespond(alert)}
                      data-testid="alert-respond"
                    >
                      Record response
                    </button>
                    <button
                      type="button"
                      className="rounded border border-border px-2 py-1 text-xs disabled:opacity-40"
                      disabled={busy || isClosed}
                      onClick={() => onClose(alert)}
                      data-testid="alert-close"
                      title="Closing states that the condition that raised this alert is gone. Only a close lets the same hazard raise again."
                    >
                      Close
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
