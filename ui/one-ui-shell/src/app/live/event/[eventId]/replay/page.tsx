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
  useLiveParticipant,
  useLiveReplay,
  useLiveTrackMinutes,
} from "@/hooks/queries/useLive";

export default function LiveEventReplayPage() {
  const params = useParams();
  const eventId = params.eventId as string;
  const { data: event, isLoading: eventLoading } = useLiveEvent(eventId);
  const { participantId } = useLiveParticipant();
  const { data: replay, isLoading: replayLoading } = useLiveReplay(eventId);
  const trackMinutes = useLiveTrackMinutes();
  const [watchStartedAt] = useState(() => Date.now());

  useEffect(() => {
    return () => {
      if (!replay || replay.status !== "PUBLISHED_REPLAY") return;
      const minutes = Math.max(1, Math.round((Date.now() - watchStartedAt) / 60_000));
      void trackMinutes.mutate({
        eventId,
        participantId,
        liveMinutes: 0,
        replayMinutes: minutes,
      });
    };
  }, [replay, eventId, participantId, trackMinutes, watchStartedAt]);

  const isPublished = replay?.status === "PUBLISHED_REPLAY";
  const isProcessing = replay?.status === "PROCESSING_REPLAY" || replay?.status === "ENDED";
  const liveKitUrl = replay?.replayRoomUrl
    ? normalizeLiveKitServerUrl(replay.replayRoomUrl)
    : replay?.playbackUrl?.startsWith("ws")
      ? normalizeLiveKitServerUrl(replay.playbackUrl)
      : "";
  const hasLiveKitReplay = Boolean(isPublished && liveKitUrl && replay?.replayAccessToken);

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

        {eventLoading || replayLoading ? (
          <div className="flex items-center gap-2 py-12 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            Preparing replay…
          </div>
        ) : hasLiveKitReplay && replay?.replayAccessToken ? (
          <div className="rounded-2xl border border-border overflow-hidden min-h-[420px] bg-neutral-900">
            <LiveKitConsultRoom
              serverUrl={liveKitUrl}
              token={replay.replayAccessToken}
              videoEnabled
            />
          </div>
        ) : isPublished && replay?.playbackUrl ? (
          <div className="rounded-2xl border border-violet-200 bg-violet-50 p-6">
            <p className="text-sm font-medium text-violet-900">Replay is ready</p>
            <p className="mt-2 text-sm text-violet-800">
              Playback reference: <code className="text-xs">{replay.playbackUrl}</code>
            </p>
            <p className="mt-3 text-xs text-violet-700">
              Replay watch minutes are tracked while this page is open. Production LiveKit replay uses
              rtc-gateway when <code>LIVE_MEDIA_PROVIDER=rtc-gateway</code> is configured.
            </p>
          </div>
        ) : isProcessing ? (
          <p className="text-sm text-warning-foreground">
            Recording is being processed. Refresh in a moment once the organiser publishes the replay.
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">
            Replay media is not available for this event yet. Check back after recording processing completes.
          </p>
        )}
      </PageShell>
    </AppLayout>
  );
}
