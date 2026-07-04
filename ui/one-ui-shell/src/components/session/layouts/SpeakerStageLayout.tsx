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
 * Speaker stage — active speaker (or screen share) large, everyone else in a
 * carousel strip. Used for interactive webinars and professional meetings.
 */
export function SpeakerStageLayout() {
  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: true },
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
      <div className="flex-1 min-h-0 flex items-center justify-center text-xs text-white/70" data-testid="speaker-stage-empty">
        Waiting for participants to join the session…
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 p-2" data-testid="speaker-stage">
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
