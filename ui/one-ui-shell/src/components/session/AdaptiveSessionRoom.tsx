"use client";

/**
 * AdaptiveSessionRoom — the one governed media surface for the adaptive
 * session suite (Health OS: one experience shell, many session modes).
 *
 * Wraps LiveKitRoom with adaptive streaming + dynacast, renders a
 * mode-appropriate stage layout, a governance-aware control bar, recording
 * and reconnect indicators, and a REAL audio-only mode that pauses remote
 * video subscriptions instead of merely hiding pixels.
 *
 * Domain surfaces (telemedicine, Impilo Live, comms, learning) compose this
 * component; they never talk to LiveKit primitives directly.
 */

import { useEffect, useMemo, type ReactNode } from "react";
import {
  ConnectionStateToast,
  LiveKitRoom,
  RoomAudioRenderer,
  useRoomContext,
} from "@livekit/components-react";
import {
  RoomEvent,
  Track,
  type RemoteParticipant,
  type RemoteTrack,
  type RemoteTrackPublication,
  type RoomOptions,
} from "livekit-client";
import { Activity, Shield } from "lucide-react";
import type { SessionLayout } from "../../../../../contracts/impilo-live";
import {
  isSupportedLiveKitServerUrl,
  normalizeLiveKitServerUrl,
} from "@/lib/session/livekit-url";
import { SessionControlBar, type SessionControlsConfig } from "./SessionControlBar";
import { RecordingBadge } from "./RecordingBadge";
import { ReconnectBanner } from "./ReconnectBanner";
import { GridStageLayout } from "./layouts/GridStageLayout";
import { SpeakerStageLayout } from "./layouts/SpeakerStageLayout";
import { BroadcastStageLayout } from "./layouts/BroadcastStageLayout";
import { ClassroomStageLayout } from "./layouts/ClassroomStageLayout";
import { ConsultStageLayout, type LocalPreviewPolicy } from "./layouts/ConsultStageLayout";

/** In-room layouts (the "player" contract layout is a recording surface, not a live room). */
export type SessionRoomLayout = Exclude<SessionLayout, "player">;

export interface AdaptiveSessionRoomProps {
  serverUrl: string;
  token: string;
  videoEnabled: boolean;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (message: string) => void;
  /** Stage layout for this session mode. Defaults to "grid". */
  layout?: SessionRoomLayout;
  /** Which controls the active role may use; audience overrides publish controls off. */
  controls?: SessionControlsConfig;
  /** Subscribe-only participant: no publish controls, no local tile. */
  audience?: boolean;
  /** Real low-bandwidth mode: local camera off + remote video subscriptions paused. */
  audioOnly?: boolean;
  onAudioOnlyChange?: (value: boolean) => void;
  /** How the local participant preview is rendered (consult layout). */
  localPreviewPolicy?: LocalPreviewPolicy;
  showRecordingBadge?: boolean;
  audioEnabled?: boolean;
  /** Slots render INSIDE <LiveKitRoom>, so they can use LiveKit hooks (e.g. useSessionRoomState). */
  headerSlot?: ReactNode;
  footerSlot?: ReactNode;
  overlaySlot?: ReactNode;
  children?: ReactNode;
}

export function AdaptiveSessionRoom({
  serverUrl,
  token,
  videoEnabled,
  onConnected,
  onDisconnected,
  onError,
  layout = "grid",
  controls,
  audience = false,
  audioOnly = false,
  onAudioOnlyChange,
  localPreviewPolicy = "tile",
  showRecordingBadge = true,
  audioEnabled = true,
  headerSlot,
  footerSlot,
  overlaySlot,
  children,
}: AdaptiveSessionRoomProps) {
  const normalizedServerUrl = normalizeLiveKitServerUrl(serverUrl);
  const hasCredentials = Boolean(normalizedServerUrl && token);
  const isSupported = isSupportedLiveKitServerUrl(normalizedServerUrl);

  const roomOptions = useMemo<RoomOptions>(
    () => ({ adaptiveStream: true, dynacast: true }),
    []
  );

  useEffect(() => {
    if (hasCredentials && !isSupported) {
      onError?.("Invalid LiveKit server URL. Expected ws:// or wss:// endpoint from RTC Gateway.");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- report once per endpoint change
  }, [hasCredentials, isSupported, normalizedServerUrl]);

  if (!hasCredentials) {
    return (
      <div className="h-full w-full rounded-md bg-neutral-900 text-muted-foreground flex flex-col items-center justify-center gap-2 px-3 text-center">
        <Shield className="h-8 w-8 text-muted-foreground" />
        <span className="text-xs">Waiting for governed LiveKit server URL and scoped media token.</span>
      </div>
    );
  }

  if (!isSupported) {
    return (
      <div className="h-full w-full rounded-md bg-neutral-900 text-warning-foreground flex flex-col items-center justify-center gap-2 px-3 text-center">
        <Shield className="h-8 w-8 text-amber-400" />
        <span className="text-xs">Governed media endpoint is invalid. Use async clinical workflow and alert platform support.</span>
      </div>
    );
  }

  return (
    <LiveKitRoom
      serverUrl={normalizedServerUrl}
      token={token}
      connect
      audio={audioEnabled && !audience}
      video={videoEnabled && !audioOnly && !audience}
      options={roomOptions}
      onConnected={onConnected}
      onDisconnected={onDisconnected}
      onError={(error) => onError?.(error.message)}
      className="h-full w-full rounded-md overflow-hidden bg-neutral-900 text-white"
    >
      <AudioOnlyController audioOnly={audioOnly} />
      <div className="relative h-full w-full flex flex-col">
        <div className="flex items-center justify-between gap-2 px-2 py-1 text-[10px] text-emerald-100 bg-emerald-950/60">
          <span className="flex items-center gap-2">
            <Activity className="h-3 w-3" />
            <span>Governed LiveKit media connected through Impilo RTC Gateway</span>
          </span>
          {showRecordingBadge ? <RecordingBadge /> : null}
        </div>
        {headerSlot}
        <ReconnectBanner />
        <SessionStage layout={layout} localPreviewPolicy={audience ? "hidden" : localPreviewPolicy} />
        {footerSlot}
        <SessionControlBar controls={controls} audience={audience} />
        {overlaySlot ? (
          <div className="pointer-events-none absolute inset-0 z-20 [&>*]:pointer-events-auto">
            {overlaySlot}
          </div>
        ) : null}
      </div>
      {children}
      <RoomAudioRenderer />
      <ConnectionStateToast />
    </LiveKitRoom>
  );
}

function SessionStage({
  layout,
  localPreviewPolicy,
}: {
  layout: SessionRoomLayout;
  localPreviewPolicy: LocalPreviewPolicy;
}) {
  switch (layout) {
    case "speaker":
      return <SpeakerStageLayout />;
    case "stage":
      return <BroadcastStageLayout />;
    case "classroom":
      return <ClassroomStageLayout />;
    case "consult":
      return <ConsultStageLayout localPreviewPolicy={localPreviewPolicy} />;
    case "grid":
    default:
      return <GridStageLayout />;
  }
}

/**
 * Real audio-only enforcement. On audioOnly=true: local camera is turned off
 * and every remote video subscription is paused (bandwidth actually drops).
 * On audioOnly=false: remote subscriptions resume; the local camera stays off
 * until the user re-toggles it from the control bar.
 */
function AudioOnlyController({ audioOnly }: { audioOnly: boolean }) {
  const room = useRoomContext();

  useEffect(() => {
    if (!room) return;

    const setPublicationEnabled = (publication: RemoteTrackPublication, enabled: boolean) => {
      try {
        publication.setEnabled(enabled);
      } catch {
        /* publication not subscribable yet — the TrackSubscribed listener catches it */
      }
    };

    const applyToRemoteVideo = (enabled: boolean) => {
      try {
        for (const participant of room.remoteParticipants.values()) {
          for (const publication of participant.videoTrackPublications.values()) {
            setPublicationEnabled(publication, enabled);
          }
        }
      } catch {
        /* room not ready yet */
      }
    };

    if (audioOnly) {
      void room.localParticipant?.setCameraEnabled(false).catch(() => undefined);
      applyToRemoteVideo(false);

      const onTrackSubscribed = (
        _track: RemoteTrack,
        publication: RemoteTrackPublication,
        _participant: RemoteParticipant
      ) => {
        if (publication.kind === Track.Kind.Video) {
          setPublicationEnabled(publication, false);
        }
      };
      room.on(RoomEvent.TrackSubscribed, onTrackSubscribed);
      return () => {
        room.off(RoomEvent.TrackSubscribed, onTrackSubscribed);
      };
    }

    applyToRemoteVideo(true);
    return undefined;
  }, [room, audioOnly]);

  return null;
}
