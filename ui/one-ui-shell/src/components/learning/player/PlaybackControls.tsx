"use client";

import { Pause, Play, RotateCcw, RotateCw } from "lucide-react";

export interface PlaybackControlsProps {
  playing: boolean;
  currentTime: number;
  duration: number | null;
  playbackRate: number;
  onPlayPause: () => void;
  onSeek: (seconds: number) => void;
  onRateChange: (rate: number) => void;
}

const RATES = [0.75, 1, 1.25, 1.5, 2];

export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = h > 0 ? String(m).padStart(2, "0") : String(m);
  const ss = String(sec).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function PlaybackControls({
  playing,
  currentTime,
  duration,
  playbackRate,
  onPlayPause,
  onSeek,
  onRateChange,
}: PlaybackControlsProps) {
  const max = duration && Number.isFinite(duration) && duration > 0 ? duration : 0;
  return (
    <div className="flex flex-col gap-2 rounded-b-lg border-x border-b border-border bg-card p-3" data-testid="player-controls">
      <input
        type="range"
        min={0}
        max={max || 1}
        step={1}
        value={Math.min(currentTime, max || 1)}
        onChange={(e) => onSeek(Number(e.target.value))}
        aria-label="Seek"
        className="w-full accent-teal-700"
        data-testid="player-seekbar"
      />
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onSeek(Math.max(0, currentTime - 10))}
            className="rounded p-1.5 text-foreground hover:bg-muted"
            aria-label="Back 10 seconds"
            data-testid="player-back-10"
          >
            <RotateCcw className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={onPlayPause}
            className="rounded-full bg-teal-700 p-2 text-white hover:bg-teal-800"
            aria-label={playing ? "Pause" : "Play"}
            data-testid="player-play-pause"
          >
            {playing ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
          </button>
          <button
            type="button"
            onClick={() => onSeek(Math.min(max || currentTime + 10, currentTime + 10))}
            className="rounded p-1.5 text-foreground hover:bg-muted"
            aria-label="Forward 10 seconds"
            data-testid="player-forward-10"
          >
            <RotateCw className="h-4 w-4" />
          </button>
          <span className="ml-1 text-xs tabular-nums text-muted-foreground" data-testid="player-clock">
            {formatClock(currentTime)}{max ? ` / ${formatClock(max)}` : ""}
          </span>
        </div>
        <select
          value={playbackRate}
          onChange={(e) => onRateChange(Number(e.target.value))}
          aria-label="Playback speed"
          className="rounded border border-border bg-background px-1.5 py-1 text-xs text-foreground"
          data-testid="player-rate"
        >
          {RATES.map((r) => (
            <option key={r} value={r}>
              {r}×
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
