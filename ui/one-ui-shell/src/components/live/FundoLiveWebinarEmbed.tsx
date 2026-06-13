"use client";

import Link from "next/link";
import { Radio, Video } from "lucide-react";
import { useLiveEvent } from "@/hooks/queries/useLive";

interface FundoLiveWebinarEmbedProps {
  liveEventId?: string | null;
  courseId: string;
  courseTitle?: string;
}

/**
 * Course-linked Impilo Live webinar card (doctrine §3).
 * Fundo owns learning; Impilo Live provides the live classroom.
 */
export function FundoLiveWebinarEmbed({ liveEventId, courseId, courseTitle }: FundoLiveWebinarEmbedProps) {
  const eventId = liveEventId ?? "";
  const { data: event, isLoading } = useLiveEvent(eventId);

  if (!liveEventId) {
    return (
      <div className="rounded-xl border border-dashed border-border bg-background p-4 text-sm text-muted-foreground">
        <p className="font-medium text-foreground">Live webinar</p>
        <p className="mt-1">
          No Impilo Live session is linked to this course yet. Trainers schedule webinars from Fundo
          Studio; attendance and CPD remain in Fundo.
        </p>
        <Link
          href={`/learning/studio/courses/${courseId}`}
          className="mt-3 inline-flex text-sm font-medium text-primary-hover hover:underline"
        >
          Open Fundo Studio
        </Link>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-success/25 bg-success-soft/50 p-4">
      <div className="flex items-start gap-3">
        <div className="rounded-lg bg-emerald-100 p-2 text-primary-hover">
          <Video className="h-5 w-5" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-primary-hover">Fundo · Impilo Live</p>
          <p className="mt-1 font-medium text-foreground">
            {isLoading ? "Loading webinar…" : event?.title ?? "Course-linked webinar"}
          </p>
          {courseTitle ? (
            <p className="text-sm text-muted-foreground mt-0.5">{courseTitle}</p>
          ) : null}
          {event?.status ? (
            <p className="mt-2 inline-flex items-center gap-1 text-xs text-primary-hover">
              <Radio className="h-3 w-3" /> {event.status.replace(/_/g, " ")}
            </p>
          ) : null}
          <div className="mt-3 flex flex-wrap gap-2">
            <Link
              href={`/live/event/${liveEventId}`}
              className="inline-flex items-center rounded-lg bg-emerald-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-800"
            >
              View session
            </Link>
            {(event?.status === "LIVE" || event?.status === "SCHEDULED") && (
              <Link
                href={`/live/event/${liveEventId}/room`}
                className="inline-flex items-center rounded-lg border border-emerald-700 px-3 py-1.5 text-sm font-medium text-primary-hover hover:bg-emerald-100"
              >
                Join webinar
              </Link>
            )}
            {event?.status === "PUBLISHED_REPLAY" || event?.status === "ENDED" ? (
              <Link
                href={`/live/event/${liveEventId}/replay`}
                className="inline-flex items-center rounded-lg border border-border px-3 py-1.5 text-sm text-foreground hover:bg-card"
              >
                Watch replay
              </Link>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
