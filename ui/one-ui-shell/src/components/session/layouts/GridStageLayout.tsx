"use client";

import { GridLayout, ParticipantTile, useTracks } from "@livekit/components-react";
import { Track } from "livekit-client";

/**
 * Equal-tile grid — the default adaptive session stage (small meetings,
 * consults without a dedicated focus, legacy LiveKitConsultRoom behaviour).
 */
export function GridStageLayout() {
  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: true },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { onlySubscribed: false }
  );

  return (
    <GridLayout tracks={tracks} className="flex-1 min-h-0 p-2">
      <ParticipantTile />
    </GridLayout>
  );
}
