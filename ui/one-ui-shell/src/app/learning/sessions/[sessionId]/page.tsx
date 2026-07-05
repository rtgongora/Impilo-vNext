"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, CalendarClock, MapPin, QrCode, Radio, UserCheck } from "lucide-react";
import { PageShell } from "@/components/PageShell";
import { useFundoFacilitators, useFundoSession, useFundoVenues } from "@/hooks/queries/useFundoDelivery";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";
import {
  isJoinableLiveSession,
  isUpcomingSession,
  resolveLiveEventId,
  resolveSessionMode,
  type LearningSessionRecord,
} from "@/lib/learning/live-session";

/**
 * Learning session detail — schedule, facilitator, venue (IN_PERSON/HYBRID),
 * course link + description, check-in entry, and the "Enter classroom" CTA
 * for LIVE/HYBRID sessions with a linked Impilo Live event.
 */
export default function LearningSessionDetailPage() {
  const params = useParams<{ sessionId: string }>();
  const sessionId = typeof params?.sessionId === "string" ? params.sessionId : "";
  const { data: session, isLoading, isError } = useFundoSession(sessionId || undefined);
  const record = (session ?? {}) as LearningSessionRecord;
  const courseId = typeof record.courseId === "string" ? record.courseId : undefined;
  const { data: structureData } = useFundoCourseStructure(courseId);
  const { data: facilitatorsData } = useFundoFacilitators();
  const { data: venuesData } = useFundoVenues();

  const structure = structureData?.data?.structure;
  const mode = session ? resolveSessionMode(record) : undefined;
  const liveEventId = session ? resolveLiveEventId(record) : undefined;
  const joinable = session ? isJoinableLiveSession(record) : false;
  const showVenue = mode === "IN_PERSON" || mode === "HYBRID";

  const facilitators =
    ((facilitatorsData?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []);
  const venues = ((venuesData?.data as { items?: Array<Record<string, unknown>> } | undefined)?.items ?? []);

  const facilitatorRecord = facilitators.find((f) => String(f.id) === String(record.facilitatorId ?? ""));
  const facilitatorName =
    (facilitatorRecord?.displayName as string | undefined) ??
    (typeof record.facilitator === "string" && record.facilitator ? record.facilitator : undefined);
  const venueRecord = venues.find((v) => String(v.id) === String(record.venueId ?? ""));
  const venueName =
    (venueRecord?.name as string | undefined) ??
    (typeof record.locationRef === "string" && record.locationRef ? record.locationRef : undefined);

  return (
    <PageShell
      title={String(record.title ?? "Learning session")}
      subtitle={mode ? `${mode.replace("_", " ")} session` : "Scheduled learning session"}
      icon={<CalendarClock className="h-6 w-6" />}
    >
      <div className="mb-4">
        <Link
          href="/learning/sessions"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> All sessions
        </Link>
      </div>

      {isLoading ? (
        <div className="rounded-lg border border-border bg-card p-6 text-sm text-muted-foreground">Loading session…</div>
      ) : null}
      {!isLoading && (isError || !session) ? (
        <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
          Session not found or currently unavailable.
        </div>
      ) : null}

      {!isLoading && session ? (
        <>
          {joinable && isUpcomingSession(record) ? (
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-violet-200 bg-violet-50 p-4">
              <div className="flex items-center gap-2 text-sm text-violet-900">
                <Radio className="h-5 w-5" />
                <span>This class runs in a governed live classroom{liveEventId ? ` (event ${liveEventId})` : ""}.</span>
              </div>
              <Link
                href={`/learning/sessions/${sessionId}/classroom`}
                data-testid="enter-classroom-cta"
                className="rounded bg-violet-700 px-4 py-2 text-sm font-medium text-white"
              >
                Enter classroom
              </Link>
            </div>
          ) : null}

          <div className="mb-6 grid gap-4 md:grid-cols-2">
            <div className="rounded-lg border border-border bg-card p-4 text-sm">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Schedule</p>
              <p className="text-foreground">
                {record.startsAt ? new Date(String(record.startsAt)).toLocaleString() : "Unscheduled"}
                {record.endsAt ? ` — ${new Date(String(record.endsAt)).toLocaleString()}` : ""}
              </p>
              <p className="mt-1 font-mono text-xs uppercase text-muted-foreground">
                {String(record.sessionType ?? "")}
              </p>
              <div className="mt-3">
                <Link
                  href={`/learning/sessions/${sessionId}/checkin`}
                  data-testid="session-checkin-link"
                  className="inline-flex items-center gap-1.5 rounded border border-teal-700 px-3 py-1.5 text-xs text-teal-700 hover:bg-teal-50"
                >
                  <QrCode className="h-3.5 w-3.5" /> Attendance check-in
                </Link>
              </div>
            </div>

            <div className="rounded-lg border border-border bg-card p-4 text-sm">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Delivery</p>
              <p className="flex items-center gap-1.5 text-foreground" data-testid="session-facilitator">
                <UserCheck className="h-4 w-4 text-muted-foreground" />
                {facilitatorName ?? "Facilitator not assigned yet"}
              </p>
              {showVenue ? (
                <p className="mt-1.5 flex items-center gap-1.5 text-foreground" data-testid="session-venue">
                  <MapPin className="h-4 w-4 text-muted-foreground" />
                  {venueName ?? "Venue not assigned yet"}
                </p>
              ) : null}
            </div>
          </div>

          {courseId ? (
            <div className="mb-6 rounded-lg border border-border bg-card p-4 text-sm">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Course</p>
              <Link href={`/learning/courses/${courseId}`} className="font-medium text-teal-700 hover:underline">
                {structure?.title ?? "Open course"}
              </Link>
              {structure?.description ? (
                <p className="mt-2 text-muted-foreground" data-testid="session-course-description">
                  {structure.description}
                </p>
              ) : null}
            </div>
          ) : null}
        </>
      ) : null}
    </PageShell>
  );
}
