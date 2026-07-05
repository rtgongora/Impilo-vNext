"use client";

import Link from "next/link";
import { Radio } from "lucide-react";
import { useFundoSessions } from "@/hooks/queries/useFundoDelivery";
import {
  isJoinableLiveSession,
  isUpcomingSession,
  resolveSessionMode,
  type LearningSessionRecord,
} from "@/lib/learning/live-session";

/**
 * "Live sessions" section for course/enrolment pages — the course's scheduled
 * LIVE/HYBRID sessions with join links into the classroom. The BFF sessions
 * list has no courseId filter, so filtering happens client-side.
 */
export function CourseLiveSessionsSection({ courseId }: { courseId: string }) {
  const { data, isLoading } = useFundoSessions(200);
  const sessions = ((data?.data?.items ?? []) as LearningSessionRecord[]).filter(
    (s) => String(s.courseId ?? "") === courseId && isJoinableLiveSession(s)
  );

  if (isLoading || sessions.length === 0) return null;

  return (
    <section data-testid="course-live-sessions" className="mb-6 rounded-lg border border-border bg-card p-4">
      <h2 className="mb-2 flex items-center gap-1.5 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
        <Radio className="h-4 w-4" /> Live sessions
      </h2>
      <ul className="space-y-2">
        {sessions.map((s) => {
          const upcoming = isUpcomingSession(s);
          return (
            <li
              key={String(s.id)}
              className="flex flex-wrap items-center justify-between gap-2 rounded border border-border px-3 py-2 text-sm"
            >
              <div className="min-w-0">
                <Link href={`/learning/sessions/${String(s.id)}`} className="font-medium text-foreground hover:underline">
                  {String(s.title ?? "Learning session")}
                </Link>
                <p className="text-xs text-muted-foreground">
                  {resolveSessionMode(s)} · {s.startsAt ? new Date(String(s.startsAt)).toLocaleString() : "unscheduled"}
                </p>
              </div>
              {upcoming ? (
                <Link
                  href={`/learning/sessions/${String(s.id)}/classroom`}
                  data-testid="course-live-session-join"
                  className="rounded bg-violet-700 px-3 py-1.5 text-xs font-medium text-white"
                >
                  Join classroom
                </Link>
              ) : (
                <span className="rounded bg-neutral-100 px-2 py-1 text-[11px] uppercase text-muted-foreground">Ended</span>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
