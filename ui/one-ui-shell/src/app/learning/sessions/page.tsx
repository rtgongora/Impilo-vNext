"use client";

import Link from "next/link";
import { CalendarClock } from "lucide-react";
import { PageShell } from "@/components/PageShell";
import { useFundoSessions } from "@/hooks/queries/useFundoDelivery";
import {
  isJoinableLiveSession,
  isUpcomingSession,
  resolveSessionMode,
  type LearningSessionRecord,
} from "@/lib/learning/live-session";

/**
 * Learning sessions list — upcoming and past scheduled sessions with mode
 * badges and, for joinable LIVE/HYBRID sessions, a direct classroom join CTA.
 */

const MODE_BADGE_CLASSES: Record<string, string> = {
  LIVE: "bg-violet-100 text-violet-800",
  HYBRID: "bg-info-soft text-blue-800",
  IN_PERSON: "bg-success-soft text-primary-hover",
  RECORDED: "bg-neutral-100 text-foreground",
};

function ModeBadge({ session }: { session: LearningSessionRecord }) {
  const mode = resolveSessionMode(session);
  const label = mode ?? String(session.sessionType ?? "SESSION");
  return (
    <span
      data-testid="session-mode-badge"
      className={`rounded px-1.5 py-0.5 text-[10px] font-medium uppercase ${
        MODE_BADGE_CLASSES[label] ?? "bg-neutral-100 text-foreground"
      }`}
    >
      {label.replace("_", " ")}
    </span>
  );
}

function SessionRow({ session }: { session: LearningSessionRecord }) {
  const id = String(session.id ?? "");
  const joinable = isJoinableLiveSession(session) && isUpcomingSession(session);
  return (
    <li
      data-testid="session-row"
      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border bg-card px-4 py-3"
    >
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <Link href={`/learning/sessions/${id}`} className="truncate font-medium text-foreground hover:underline">
            {String(session.title ?? "Learning session")}
          </Link>
          <ModeBadge session={session} />
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">
          {session.startsAt ? new Date(String(session.startsAt)).toLocaleString() : "Unscheduled"}
          {session.endsAt ? ` — ${new Date(String(session.endsAt)).toLocaleTimeString()}` : ""}
        </p>
      </div>
      <div className="flex flex-shrink-0 items-center gap-2">
        {joinable ? (
          <Link
            href={`/learning/sessions/${id}/classroom`}
            data-testid="session-join-cta"
            className="rounded bg-violet-700 px-3 py-1.5 text-xs font-medium text-white"
          >
            Join classroom
          </Link>
        ) : null}
        <Link
          href={`/learning/sessions/${id}`}
          className="rounded border border-border px-3 py-1.5 text-xs text-foreground"
        >
          Details
        </Link>
      </div>
    </li>
  );
}

export default function LearningSessionsPage() {
  const { data, isLoading, isError } = useFundoSessions(200);
  const sessions = ((data?.data?.items ?? []) as LearningSessionRecord[]).filter((s) => s && s.id);
  const upcoming = sessions.filter((s) => isUpcomingSession(s));
  const past = sessions.filter((s) => !isUpcomingSession(s));

  return (
    <PageShell
      title="Learning sessions"
      subtitle="Scheduled classes — live classrooms, hybrid and in-person sessions"
      icon={<CalendarClock className="h-6 w-6" />}
    >
      {isLoading ? (
        <div className="rounded-lg border border-border bg-card p-6 text-sm text-muted-foreground">Loading sessions…</div>
      ) : null}
      {!isLoading && isError ? (
        <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
          Sessions are currently unavailable. Please retry shortly.
        </div>
      ) : null}

      {!isLoading && !isError ? (
        <>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Upcoming ({upcoming.length})
          </h2>
          {upcoming.length === 0 ? (
            <p className="mb-4 rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground" data-testid="upcoming-empty">
              No upcoming sessions scheduled.
            </p>
          ) : (
            <ul className="mb-6 space-y-2">
              {upcoming.map((s) => (
                <SessionRow key={String(s.id)} session={s} />
              ))}
            </ul>
          )}

          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Past ({past.length})
          </h2>
          {past.length === 0 ? (
            <p className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">No past sessions.</p>
          ) : (
            <ul className="space-y-2">
              {past.map((s) => (
                <SessionRow key={String(s.id)} session={s} />
              ))}
            </ul>
          )}
        </>
      ) : null}
    </PageShell>
  );
}
