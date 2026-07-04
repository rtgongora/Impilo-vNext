"use client";

import {
  FocusLayout,
  GridLayout,
  ParticipantTile,
  useTracks,
} from "@livekit/components-react";
import { Track } from "livekit-client";

export type LocalPreviewPolicy = "tile" | "pip" | "hidden";

/**
 * Consult stage — 1:1 clinical layout. The remote participant fills the
 * stage; the local preview follows `localPreviewPolicy`:
 *  - "tile":   local participant is a normal grid tile alongside the remote
 *  - "pip":    local camera floats as a corner picture-in-picture
 *  - "hidden": local preview is not rendered at all
 */
export function ConsultStageLayout({
  localPreviewPolicy = "tile",
}: {
  localPreviewPolicy?: LocalPreviewPolicy;
}) {
  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: true },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { onlySubscribed: false }
  );

  if (localPreviewPolicy === "tile") {
    return (
      <GridLayout tracks={tracks} className="flex-1 min-h-0 p-2">
        <ParticipantTile />
      </GridLayout>
    );
  }

  const remoteTracks = tracks.filter((t) => !t.participant.isLocal);
  const localCamera = tracks.find(
    (t) => t.participant.isLocal && t.source === Track.Source.Camera
  );
  const focus =
    remoteTracks.find((t) => t.source === Track.Source.ScreenShare) ?? remoteTracks[0];

  return (
    <div className="relative flex-1 min-h-0 p-2" data-testid="consult-stage">
      {focus ? (
        <FocusLayout trackRef={focus} className="h-full w-full" />
      ) : (
        <div className="h-full w-full flex items-center justify-center text-center text-xs text-white/70">
          Waiting for the other participant to join…
        </div>
      )}
      {localPreviewPolicy === "pip" ? (
        <div
          data-testid="session-local-pip"
          className="absolute bottom-3 right-3 z-10 aspect-video w-28 overflow-hidden rounded-md border border-white/20 bg-black/70 shadow-lg sm:w-36"
        >
          {localCamera ? (
            <ParticipantTile trackRef={localCamera} className="h-full w-full" />
          ) : (
            <div className="flex h-full w-full items-center justify-center text-[10px] text-white/70">
              Camera off
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
