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
 * Classroom stage — facilitator focused large, learners in a filmstrip.
 * Facilitator heuristic: screen share wins; otherwise the first remote
 * publisher; otherwise the local participant if they are publishing.
 */
export function ClassroomStageLayout() {
  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: false },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { onlySubscribed: false }
  );

  const screenShare = tracks.find((t) => t.source === Track.Source.ScreenShare);
  const firstRemotePublisher = tracks.find((t) => !t.participant.isLocal);
  const localPublisher = tracks.find((t) => t.participant.isLocal);
  const facilitator = screenShare ?? firstRemotePublisher ?? localPublisher;
  const rest = tracks.filter((t) => t !== facilitator);

  if (!facilitator) {
    return (
      <div className="flex-1 min-h-0 flex items-center justify-center px-3 text-center text-xs text-white/70" data-testid="classroom-stage-empty">
        Waiting for the facilitator to start publishing…
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 p-2" data-testid="classroom-stage">
      <FocusLayoutContainer>
        {rest.length > 0 ? (
          <CarouselLayout tracks={rest}>
            <ParticipantTile />
          </CarouselLayout>
        ) : null}
        <FocusLayout trackRef={facilitator} />
      </FocusLayoutContainer>
    </div>
  );
}
