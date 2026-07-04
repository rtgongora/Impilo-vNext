"use client";

import {
  CarouselLayout,
  FocusLayout,
  FocusLayoutContainer,
  ParticipantTile,
  useTracks,
} from "@livekit/components-react";
import { Track } from "livekit-client";

/**
 * Broadcast stage — presenter-only surface for PUBLIC_BROADCAST /
 * EMERGENCY_BRIEFING sessions. Renders only tracks that participants have
 * actually published (no placeholders), so subscribe-only audience members
 * never appear as empty tiles.
 */
export function BroadcastStageLayout() {
  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: false },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { onlySubscribed: false }
  );

  const screenShare = tracks.find((t) => t.source === Track.Source.ScreenShare);
  const activeSpeaker = tracks.find((t) => t.participant.isSpeaking);
  const focus = screenShare ?? activeSpeaker ?? tracks[0];
  const rest = tracks.filter((t) => t !== focus);

  if (!focus) {
    return (
      <div className="flex-1 min-h-0 flex flex-col items-center justify-center gap-1 px-3 text-center text-xs text-white/70" data-testid="broadcast-stage-empty">
        <span>The broadcast has not started publishing media yet.</span>
        <span className="text-[10px] text-white/50">You are connected — video will appear when the presenter goes live.</span>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 p-2" data-testid="broadcast-stage">
      <FocusLayoutContainer>
        {rest.length > 0 ? (
          <CarouselLayout tracks={rest}>
            <ParticipantTile />
          </CarouselLayout>
        ) : null}
        <FocusLayout trackRef={focus} />
      </FocusLayoutContainer>
    </div>
  );
}
