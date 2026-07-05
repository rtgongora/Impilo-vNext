"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useMediaWatchProgress, useUpsertWatchProgress, type FundoWatchProgress } from "@/hooks/queries/useFundoPlayer";

export interface UseWatchProgressOptions {
  assetId?: string;
  enrolmentId?: string;
  /** Milliseconds between debounced upserts while playing (default 10s). */
  flushIntervalMs?: number;
}

export interface WatchProgressController {
  /** Server truth for the enrolment+asset pair (resume point, percent, threshold). */
  progress: FundoWatchProgress | null;
  /** Position (seconds) the player should resume from — the furthest watched point. */
  resumePositionSeconds: number;
  /** True once the watch threshold milestone has been recorded server-side. */
  completed: boolean;
  /** Latest percent (server truth; refreshed after each flush). */
  percent: number;
  thresholdPercent: number;
  /** Call on every timeupdate tick with the current playback state. */
  onTimeUpdate: (positionSeconds: number, durationSeconds: number | null, playing: boolean) => void;
  /** Force-flush pending progress (pause, seek away, unmount). */
  flush: () => void;
}

/**
 * Debounced watch-progress tracking against the Fundo media watch API
 * (LEARNING_RECORDING W4).
 *
 * Watched seconds are accumulated locally from play-time deltas (scrubbing
 * does not inflate them) and flushed at most once per `flushIntervalMs` while
 * playing, plus a final flush on unmount/pause. The server keeps
 * watched/furthest monotonic, so redundant flushes are harmless.
 */
export function useWatchProgress(options: UseWatchProgressOptions): WatchProgressController {
  const { assetId, enrolmentId, flushIntervalMs = 10_000 } = options;
  const { data } = useMediaWatchProgress(assetId, enrolmentId);
  const upsert = useUpsertWatchProgress();

  const serverProgress = (data?.data?.watchProgress ?? null) as FundoWatchProgress | null;

  // Local accumulation between flushes.
  const watchedRef = useRef(0);
  const lastTickRef = useRef<number | null>(null);
  const positionRef = useRef(0);
  const durationRef = useRef<number | null>(null);
  // Seed with mount time so the first flush waits a full debounce window.
  const lastFlushAtRef = useRef(Date.now());
  const dirtyRef = useRef(false);
  const [localCompleted, setLocalCompleted] = useState(false);
  const [localPercent, setLocalPercent] = useState<number | null>(null);

  // Seed local accumulators from server truth exactly once per asset+enrolment.
  const seededForRef = useRef<string | null>(null);
  useEffect(() => {
    const key = `${assetId}:${enrolmentId}`;
    if (serverProgress && seededForRef.current !== key) {
      seededForRef.current = key;
      watchedRef.current = serverProgress.watchedSeconds ?? 0;
      setLocalCompleted(Boolean(serverProgress.completedAt));
      setLocalPercent(serverProgress.percent ?? null);
    }
  }, [serverProgress, assetId, enrolmentId]);

  const doFlush = useCallback(() => {
    if (!assetId || !enrolmentId || !dirtyRef.current) return;
    dirtyRef.current = false;
    lastFlushAtRef.current = Date.now();
    upsert.mutate(
      {
        assetId,
        body: {
          enrolmentId,
          positionSeconds: Math.round(positionRef.current),
          watchedSeconds: Math.round(watchedRef.current),
          durationSeconds: durationRef.current != null ? Math.round(durationRef.current) : undefined,
        },
      },
      {
        onSuccess: (res) => {
          const wp = (res as { data?: { watchProgress?: FundoWatchProgress } })?.data?.watchProgress;
          if (wp) {
            setLocalPercent(wp.percent ?? null);
            if (wp.completedAt) setLocalCompleted(true);
          }
        },
      },
    );
  }, [assetId, enrolmentId, upsert]);

  const onTimeUpdate = useCallback(
    (positionSeconds: number, durationSeconds: number | null, playing: boolean) => {
      positionRef.current = positionSeconds;
      if (durationSeconds != null && Number.isFinite(durationSeconds) && durationSeconds > 0) {
        durationRef.current = durationSeconds;
      }
      const now = Date.now();
      if (playing) {
        if (lastTickRef.current != null) {
          // Cap the delta so a suspended tab does not fabricate watch time.
          const delta = Math.min((now - lastTickRef.current) / 1000, 2);
          if (delta > 0) {
            watchedRef.current += delta;
            dirtyRef.current = true;
          }
        }
        lastTickRef.current = now;
        if (now - lastFlushAtRef.current >= flushIntervalMs) {
          doFlush();
        }
      } else {
        lastTickRef.current = null;
      }
    },
    [doFlush, flushIntervalMs],
  );

  // Final flush when the player unmounts.
  useEffect(() => {
    return () => {
      doFlush();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return {
    progress: serverProgress,
    resumePositionSeconds: serverProgress?.furthestPositionSeconds ?? 0,
    completed: localCompleted,
    percent: localPercent ?? serverProgress?.percent ?? 0,
    thresholdPercent: serverProgress?.thresholdPercent ?? 90,
    onTimeUpdate,
    flush: doFlush,
  };
}
