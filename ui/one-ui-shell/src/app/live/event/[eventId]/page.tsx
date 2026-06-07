"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Calendar, Radio, User } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveNompiloAssistPanel } from "@/components/live/LiveNompiloAssistPanel";
import {
  useLiveEvent,
  useLiveMyRegistrations,
  useLiveParticipant,
  useLiveRegister,
  useScheduleLiveEvent,
  useLiveStartRoom,
} from "@/hooks/queries/useLive";

export default function LiveEventDetailPage() {
  const params = useParams();
  const eventId = params.eventId as string;
  const { data: event, isLoading, isError } = useLiveEvent(eventId);
  const { data: registrations = [] } = useLiveMyRegistrations();
  const { participantId, participantType } = useLiveParticipant();
  const register = useLiveRegister();
  const schedule = useScheduleLiveEvent();
  const startRoom = useLiveStartRoom();

  const myReg = registrations.find((r) => r.eventId === eventId);
  const isLive = event?.status === "LIVE";
  const isEnded = event?.status === "ENDED";

  if (isLoading) {
    return (
      <AppLayout>
        <PageShell title="Live Event" subtitle="Loading event details…">
          <p className="text-sm text-gray-500">Loading…</p>
        </PageShell>
      </AppLayout>
    );
  }

  if (isError || !event) {
    return (
      <AppLayout>
        <PageShell title="Live Event" subtitle="Event not found">
          <Link href="/live" className="text-sm text-violet-700 underline">
            Back to Impilo Live
          </Link>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell title={event.title} subtitle={event.description ?? "Live event details"}>
        <div className="grid lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-4">
            <div className="rounded-2xl border border-gray-200 bg-white p-5">
              <div className="flex flex-wrap gap-2 mb-3">
                <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-medium text-violet-800">
                  {event.status}
                </span>
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700">
                  {event.eventType.replace(/_/g, " ")}
                </span>
                {event.cpdEnabled ? (
                  <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs text-indigo-800">
                    CPD {event.cpdPoints ? `· ${event.cpdPoints} pts` : ""}
                  </span>
                ) : null}
              </div>

              <div className="flex flex-wrap gap-4 text-sm text-gray-600">
                <span className="inline-flex items-center gap-1">
                  <Calendar className="h-4 w-4" />
                  {event.startTime
                    ? new Date(event.startTime).toLocaleString()
                    : "Schedule TBC"}
                </span>
                <span className="inline-flex items-center gap-1">
                  <User className="h-4 w-4" />
                  {event.facilityId ? `Facility ${event.facilityId}` : "Impilo Live"}
                </span>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {isLive || myReg ? (
                  <Link
                    href={`/live/event/${eventId}/room`}
                    className="inline-flex items-center gap-1 rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white"
                  >
                    <Radio className="h-4 w-4" />
                    {isLive ? "Join live" : "Enter room"}
                  </Link>
                ) : !isEnded ? (
                  <button
                    type="button"
                    onClick={() =>
                      register.mutate({
                        eventId,
                        body: { participantId, participantType, inviteSource: "EVENT_DETAIL" },
                      })
                    }
                    disabled={register.isPending}
                    className="rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white"
                  >
                    Register
                  </button>
                ) : null}

                {isEnded ? (
                  <Link
                    href={`/live/event/${eventId}/replay`}
                    className="rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700"
                  >
                    Watch replay
                  </Link>
                ) : null}

                {event.status === "DRAFT" ? (
                  <button
                    type="button"
                    onClick={() => schedule.mutate(eventId)}
                    disabled={schedule.isPending}
                    className="rounded-lg border border-sky-300 px-3 py-2 text-sm text-sky-800"
                  >
                    Schedule
                  </button>
                ) : null}

                {event.status === "SCHEDULED" ? (
                  <button
                    type="button"
                    onClick={() => startRoom.mutate(eventId)}
                    disabled={startRoom.isPending}
                    className="rounded-lg border border-red-300 px-3 py-2 text-sm text-red-800"
                  >
                    Go live
                  </button>
                ) : null}

                <Link
                  href={`/live/event/${eventId}/analytics`}
                  className="rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700"
                >
                  Analytics
                </Link>
                <Link href="/live" className="rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700">
                  Hub
                </Link>
              </div>
            </div>
          </div>

          <LiveNompiloAssistPanel eventTitle={event.title} eventType={event.eventType} />
        </div>
      </PageShell>
    </AppLayout>
  );
}
