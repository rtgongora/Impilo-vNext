"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Headphones, Loader2, LogOut } from "lucide-react";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import { LiveAudienceEngagementRail } from "@/components/live/LiveAudienceEngagementRail";
import {
  useLiveEvent,
  useLiveJoinRoom,
  useLiveParticipant,
  useLiveRoomToken,
  useLiveStageRole,
  useLiveAttendanceLeave,
  useLiveTrackMinutes,
} from "@/hooks/queries/useLive";

interface LiveRoomProps {
  eventId: string;
}

/** Modes riding the LIVE_EVENT session template (stage-managed broadcasts). */
const BROADCAST_MODES = new Set(["PUBLIC_BROADCAST", "EMERGENCY_BRIEFING", "HYBRID_EVENT"]);
/** Tiers whose template grant can publish to the stage. */
const PUBLISH_TIERS = new Set(["HOST", "PRODUCER", "SPEAKER"]);

export function LiveRoom({ eventId }: LiveRoomProps) {
  const router = useRouter();
  const { participantId, participantType, role } = useLiveParticipant();
  const { data: event, isLoading: eventLoading } = useLiveEvent(eventId);
  const isBroadcast = BROADCAST_MODES.has(event?.mode ?? "");

  // Server-resolved role tier: the backend clamps token roles to this tier,
  // so the UI variant (stage vs audience) mirrors what the token will grant.
  // Polled — an approve/demote in the producer console flips the tier here,
  // which changes the token query key and re-mints the media token.
  const { data: stageRole } = useLiveStageRole(eventId, isBroadcast);
  const tier = isBroadcast ? stageRole?.tier ?? "AUDIENCE" : undefined;
  const canPublish = isBroadcast ? PUBLISH_TIERS.has(tier ?? "") : role === "PRESENTER";
  const audience = !canPublish;

  const joinRoom = useLiveJoinRoom();
  const leaveAttendance = useLiveAttendanceLeave();
  const trackMinutes = useLiveTrackMinutes();
  const { data: roomToken, isLoading: tokenLoading, error: tokenError } = useLiveRoomToken(
    eventId,
    Boolean(joinRoom.isSuccess || event?.status === "LIVE"),
    // Broadcast modes speak the template vocabulary; the server clamps to the
    // resolved tier anyway — this only keeps the requested role honest.
    isBroadcast ? tier : undefined,
  );

  const [lowBandwidth, setLowBandwidth] = useState(false);
  const [watchStartedAt] = useState(() => Date.now());
  const [joined, setJoined] = useState(false);

  useEffect(() => {
    if (!eventId || !participantId || joined) return;
    let cancelled = false;
    void liveApiJoin();
    async function liveApiJoin() {
      await joinRoom.mutateAsync({
        eventId,
        body: { participantId, participantType, role: isBroadcast ? tier : role },
      });
      if (!cancelled) setJoined(true);
    }
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- join once per room mount
  }, [eventId, participantId]);

  async function handleLeave() {
    const elapsedMinutes = Math.max(1, Math.round((Date.now() - watchStartedAt) / 60_000));
    await trackMinutes.mutateAsync({
      eventId,
      participantId,
      liveMinutes: event?.status === "LIVE" ? elapsedMinutes : 0,
      replayMinutes: event?.status === "ENDED" ? elapsedMinutes : 0,
    });
    await leaveAttendance.mutateAsync({ eventId, participantId });
    router.push(`/live/event/${eventId}`);
  }

  return (
    <div className="flex flex-col lg:flex-row gap-4 min-h-[70vh]">
      <div className="flex-1 flex flex-col rounded-2xl border border-border bg-neutral-900 overflow-hidden min-h-[360px]">
        <div className="flex items-center justify-between gap-2 px-3 py-2 bg-neutral-900 text-muted-foreground text-sm">
          <div>
            <p className="font-medium text-white">{event?.title ?? "Live session"}</p>
            {isBroadcast ? (
              <p className="text-xs text-amber-300 mt-0.5" data-testid="room-tier">
                {audience
                  ? "Public broadcast · moderated viewer mode"
                  : `On stage · ${tier?.toLowerCase() ?? "speaker"}`}
              </p>
            ) : null}
            <p className="text-xs text-muted-foreground">{event?.status ?? "…"}</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setLowBandwidth((v) => !v)}
              className={`inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs border ${
                lowBandwidth
                  ? "border-amber-400 text-warning-foreground bg-amber-950/40"
                  : "border-gray-600 text-muted-foreground"
              }`}
            >
              <Headphones className="h-3.5 w-3.5" />
              {lowBandwidth ? "Audio only" : "Video"}
            </button>
            <button
              type="button"
              onClick={handleLeave}
              className="inline-flex items-center gap-1 rounded-lg bg-red-700 px-2.5 py-1 text-xs font-medium text-white hover:bg-red-800"
            >
              <LogOut className="h-3.5 w-3.5" />
              Leave
            </button>
          </div>
        </div>

        <div className="flex-1 min-h-[280px]">
          {eventLoading || joinRoom.isPending || tokenLoading ? (
            <div className="h-full flex items-center justify-center gap-2 text-muted-foreground text-sm">
              <Loader2 className="h-5 w-5 animate-spin" />
              Connecting to governed live room…
            </div>
          ) : tokenError || !roomToken ? (
            <div className="h-full flex flex-col items-center justify-center gap-2 px-4 text-center text-muted-foreground text-sm">
              <p>Room token not available yet.</p>
              <Link href={`/live/event/${eventId}`} className="text-violet-300 underline">
                Return to event details
              </Link>
            </div>
          ) : (
            // AdaptiveSessionRoom is additionally grant-aware: a subscribe-only
            // token (canPublish=false in the JWT) renders without publish
            // controls regardless of what this component computed.
            <AdaptiveSessionRoom
              layout={isBroadcast ? "stage" : "speaker"}
              audience={audience}
              serverUrl={roomToken.roomUrl}
              token={roomToken.accessToken}
              videoEnabled={!lowBandwidth}
              audioOnly={lowBandwidth}
              onAudioOnlyChange={setLowBandwidth}
              controls={{
                microphone: !audience,
                camera: !audience,
                screenShare: !audience && isBroadcast,
                leave: true,
              }}
              onError={() => undefined}
            />
          )}
        </div>
      </div>

      <LiveAudienceEngagementRail
        eventId={eventId}
        event={event}
        stageRole={stageRole}
        showStageRequest={isBroadcast && audience}
      />
    </div>
  );
}
