"use client";

import { Target } from "lucide-react";

/**
 * Learner objectives panel — renders what the payloads honestly carry today:
 * the session title/type, the linked live event description, and the course
 * description. There is no dedicated per-session objectives field yet; when
 * one lands it should be threaded through here.
 */
export function ObjectivesPanel({
  sessionTitle,
  sessionType,
  eventDescription,
  courseTitle,
  courseDescription,
}: {
  sessionTitle?: string;
  sessionType?: string;
  eventDescription?: string | null;
  courseTitle?: string;
  courseDescription?: string | null;
}) {
  const hasNarrative = Boolean(eventDescription || courseDescription);
  return (
    <div data-testid="objectives-panel" className="space-y-2">
      <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <Target className="h-3.5 w-3.5" /> Session objectives
      </p>
      <div className="rounded border border-border bg-background p-2 text-xs">
        <p className="font-medium text-foreground">{sessionTitle ?? "Learning session"}</p>
        {sessionType ? <p className="mt-0.5 font-mono text-[10px] uppercase text-muted-foreground">{sessionType}</p> : null}
      </div>
      {eventDescription ? (
        <p className="text-xs text-foreground" data-testid="objectives-event-description">
          {eventDescription}
        </p>
      ) : null}
      {courseDescription ? (
        <div className="text-xs text-muted-foreground" data-testid="objectives-course-description">
          {courseTitle ? <p className="font-medium text-foreground">{courseTitle}</p> : null}
          <p>{courseDescription}</p>
        </div>
      ) : null}
      {!hasNarrative ? (
        <p className="text-xs text-muted-foreground" data-testid="objectives-empty">
          No objectives have been captured for this session yet.
        </p>
      ) : null}
    </div>
  );
}
