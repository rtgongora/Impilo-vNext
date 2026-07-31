"use client";

import React from "react";

/**
 * An elapsed-time display for time-critical work, anchored on the server's clock and ticked from a
 * monotonic source.
 *
 * Three properties, each because the obvious implementation gets it wrong:
 *
 * - **Anchored on the server, not the device.** Elapsed time is measured from `startedAt` against
 *   the server's own clock at page load (`serverNow`), not the browser's. A tablet whose clock is
 *   forty minutes off — routine on shared ward hardware — would otherwise report forty minutes of
 *   resuscitation that did not happen.
 * - **Ticked monotonically.** After the initial anchor, each tick advances by the delta from
 *   `performance.now()`, which no NTP correction or user clock change can move backwards. Re-reading
 *   `Date.now()` every second would let the display jump or stall mid-arrest.
 * - **Survives reload.** Nothing is held in component state that a refresh would lose: the anchor is
 *   two timestamps passed in as props, so a reload re-derives the same elapsed time.
 *
 * **Cadences are supplied, never assumed.** `intervals` carries whatever rhythm the protocol in force
 * actually specifies. This component contains no clinical constant of its own — a hardcoded "two
 * minutes" here would be a clinical rule living in TypeScript, which is precisely what
 * `check-no-ts-clinical-logic.sh` exists to prevent. When no cadence is supplied the timer shows
 * elapsed time and says that no cadence is configured, rather than inventing one.
 */
export interface TimerInterval {
  /** What this cadence is, in the protocol's own words. */
  label: string;
  /** Cadence length, from the protocol — never a constant defined in this file. */
  seconds: number;
}

export interface ElapsedTimerProps {
  /** When the clock started, ISO-8601, from the server. */
  startedAt: string;
  /**
   * The server's clock at the moment this page's data was fetched, ISO-8601. Omit only when the
   * server did not supply one — the component then falls back to the device clock and says so.
   */
  serverNow?: string;
  intervals?: TimerInterval[];
  /** Stops ticking (episode ended). The final elapsed value remains displayed. */
  stopped?: boolean;
  /** Fired once each time a supplied cadence comes due. */
  onIntervalDue?: (interval: TimerInterval, occurrence: number) => void;
  className?: string;
  "data-testid"?: string;
}

function formatElapsed(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const seconds = safe % 60;
  const mm = String(minutes).padStart(2, "0");
  const ss = String(seconds).padStart(2, "0");
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** Exported for tests: the pure part, with no timers and no React. */
export function elapsedSecondsAt(
  startedAt: string,
  anchorNow: string | undefined,
  monotonicDeltaMs: number,
): number {
  const start = Date.parse(startedAt);
  if (Number.isNaN(start)) return 0;
  const anchor = anchorNow ? Date.parse(anchorNow) : Date.now();
  const base = Number.isNaN(anchor) ? Date.now() : anchor;
  return (base - start + monotonicDeltaMs) / 1000;
}

function monotonicNow(): number {
  return typeof performance !== "undefined" && typeof performance.now === "function"
    ? performance.now()
    : Date.now();
}

export function ElapsedTimer({
  startedAt,
  serverNow,
  intervals,
  stopped = false,
  onIntervalDue,
  className = "",
  "data-testid": testId = "elapsed-timer",
}: ElapsedTimerProps) {
  const mountedAtRef = React.useRef<number | null>(null);
  if (mountedAtRef.current === null) mountedAtRef.current = monotonicNow();

  const [elapsed, setElapsed] = React.useState(() =>
    elapsedSecondsAt(startedAt, serverNow, 0),
  );

  React.useEffect(() => {
    if (stopped) return;
    const mountedAt = mountedAtRef.current ?? monotonicNow();
    const tick = () => setElapsed(elapsedSecondsAt(startedAt, serverNow, monotonicNow() - mountedAt));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [startedAt, serverNow, stopped]);

  const firedRef = React.useRef<Map<string, number>>(new Map());
  React.useEffect(() => {
    if (!intervals?.length || !onIntervalDue || stopped) return;
    for (const interval of intervals) {
      if (interval.seconds <= 0) continue;
      const occurrence = Math.floor(elapsed / interval.seconds);
      if (occurrence < 1) continue;
      if ((firedRef.current.get(interval.label) ?? 0) < occurrence) {
        firedRef.current.set(interval.label, occurrence);
        onIntervalDue(interval, occurrence);
      }
    }
  }, [elapsed, intervals, onIntervalDue, stopped]);

  return (
    <div className={`flex flex-col gap-1 ${className}`} data-testid={testId}>
      <div className="flex items-baseline gap-2">
        <span
          className="font-mono text-3xl tabular-nums tracking-tight text-foreground"
          aria-label="elapsed time"
          data-testid={`${testId}-value`}
        >
          {formatElapsed(elapsed)}
        </span>
        {stopped ? (
          <span className="text-xs uppercase tracking-wide text-muted-foreground">stopped</span>
        ) : null}
      </div>

      {!serverNow ? (
        <p className="text-xs text-warning-foreground" data-testid={`${testId}-unanchored`}>
          Timed from this device&rsquo;s clock — the server did not supply a reference time.
        </p>
      ) : null}

      {intervals?.length ? (
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
          {intervals.map((interval) => {
            const remaining = interval.seconds - (elapsed % interval.seconds);
            return (
              <li key={interval.label} data-testid={`${testId}-interval-${interval.label}`}>
                <span className="text-foreground">{interval.label}</span>{" "}
                <span className="font-mono tabular-nums">{formatElapsed(remaining)}</span>
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="text-xs text-muted-foreground" data-testid={`${testId}-no-cadence`}>
          No cadence configured for this protocol.
        </p>
      )}
    </div>
  );
}
