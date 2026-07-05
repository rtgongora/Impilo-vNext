"use client";

import { CheckCircle2 } from "lucide-react";

export interface CompletionBannerProps {
  completed: boolean;
  percent: number;
  thresholdPercent: number;
}

export function CompletionBanner({ completed, percent, thresholdPercent }: CompletionBannerProps) {
  if (completed) {
    return (
      <div
        className="flex items-center gap-2 rounded-lg border border-teal-200 bg-teal-50 px-3 py-2 text-sm text-teal-900"
        data-testid="player-completion-banner"
        data-state="completed"
      >
        <CheckCircle2 className="h-4 w-4 shrink-0 text-teal-700" />
        Watch requirement met — this recording counts towards your completion.
      </div>
    );
  }
  return (
    <div
      className="rounded-lg border border-border bg-card px-3 py-2 text-xs text-muted-foreground"
      data-testid="player-completion-banner"
      data-state="in-progress"
    >
      <div className="mb-1 flex items-center justify-between">
        <span>
          Watched {Math.min(100, Math.max(0, Math.round(percent)))}% — {thresholdPercent}% counts as complete
        </span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-teal-600 transition-all"
          style={{ width: `${Math.min(100, Math.max(0, percent))}%` }}
        />
      </div>
    </div>
  );
}
