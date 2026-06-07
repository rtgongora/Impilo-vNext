"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Clapperboard, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveKitConsultRoom, normalizeLiveKitServerUrl } from "@/components/telemedicine/LiveKitConsultRoom";
import {
  useLiveEvent,
  useLiveJoinRoom,
  useLiveParticipant,
  useLiveRoomToken,
  useLiveTrackMinutes,
} from "@/hooks/queries/useLive";

export default function LiveEventReplayPage() {
  const params = useParams();
  const eventId = params.eventId as string;
  const { data: event, isLoading } = useLiveEvent(eventId);
  const { participantId, participantType, role } = useLiveParticipant();
  const joinRoom = useLiveJoinRoom();
  const trackMinutes = useLiveTrackMinutes();
  const [joined, setJoined] = useState(false);
  const [watchStartedAt] = useState(() => Date.now());

  const { data: roomToken, isLoading: tokenLoading } = useLiveRoomToken(eventId, joined);

  useEffect(() => {
    if (!eventId || !participantId || joined) return;
    joinRoom.mutate(
      { eventId, body: { participantId, participantType, role: "ATTENDEE" } },
      { onSuccess: () => setJoined(true) },
    );
  }, [eventId, participantId, participantType, joined, joinRoom]);

  useEffect(() => {
    return () => {
      if (!joined) return;
      const minutes = Math.max(1, Math.round((Date.now() - watchStartedAt) / 60_000));
      void trackMinutes.mutate({
        eventId,
        participantId,
        liveMinutes: 0,
        replayMinutes: minutes,
      });
    };
  }, [joined, eventId, participantId, trackMinutes, watchStartedAt]);

  const replayUrl = roomToken?.roomUrl ? normalizeLiveKitServerUrl(roomToken.roomUrl) : "";

  return (
    <AppLayout>
      <PageShell
        title={event?.title ? `Replay — ${event.title}` : "Event replay"}
        subtitle="Governed replay with attendance minute tracking"
        icon={<Clapperboard className="h-6 w-6" />}
      >
        <div className="mb-3">
          <Link href={`/live/event/${eventId}`} className="text-sm text-violet-700 hover:underline">
            ← Event details
          </Link>
        </div>

        {isLoading || joinRoom.isPending || tokenLoading ? (
          <div className="flex items-center gap-2 py-12 text-sm text-gray-500">
            <Loader2 className="h-4 w-4 animate-spin" />
            Preparing replay…
          </div>
        ) : event?.status !== "ENDED" ? (
          <p className="text-sm text-amber-700">
            This session has not ended yet. Join the live room when broadcasting starts.
          </p>
        ) : roomToken ? (
          <div className="rounded-2xl border border-gray-200 overflow-hidden min-h-[420px] bg-gray-950">
            <LiveKitConsultRoom
              serverUrl={replayUrl}
              token={roomToken.accessToken}
              videoEnabled
            />
          </div>
        ) : (
          <p className="text-sm text-gray-500">
            Replay media is not available for this event yet. Check back after recording processing completes.
          </p>
        )}
      </PageShell>
    </AppLayout>
  );
}
