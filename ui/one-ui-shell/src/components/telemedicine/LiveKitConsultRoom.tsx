"use client";

import {
  ConnectionStateToast,
  ControlBar,
  GridLayout,
  LiveKitRoom,
  ParticipantTile,
  RoomAudioRenderer,
  useTracks,
} from "@livekit/components-react";
import { Track } from "livekit-client";
import { Activity, Shield } from "lucide-react";

interface LiveKitConsultRoomProps {
  serverUrl: string;
  token: string;
  videoEnabled: boolean;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (message: string) => void;
}

export function normalizeLiveKitServerUrl(value: string | undefined | null): string {
  const raw = String(value ?? "").trim();
  if (!raw) return "";

  const withoutRoomPath = raw.replace(/\/rooms\/[^/?#]+.*$/i, "");
  if (withoutRoomPath.startsWith("https://")) {
    return `wss://${withoutRoomPath.slice("https://".length)}`;
  }
  if (withoutRoomPath.startsWith("http://")) {
    return `ws://${withoutRoomPath.slice("http://".length)}`;
  }
  return withoutRoomPath;
}

function isSupportedLiveKitServerUrl(url: string): boolean {
  return url.startsWith("ws://") || url.startsWith("wss://");
}

export function LiveKitConsultRoom({
  serverUrl,
  token,
  videoEnabled,
  onConnected,
  onDisconnected,
  onError,
}: LiveKitConsultRoomProps) {
  const normalizedServerUrl = normalizeLiveKitServerUrl(serverUrl);

  if (!normalizedServerUrl || !token) {
    return (
      <div className="h-full w-full rounded-md bg-neutral-900 text-muted-foreground flex flex-col items-center justify-center gap-2 px-3 text-center">
        <Shield className="h-8 w-8 text-muted-foreground" />
        <span className="text-xs">Waiting for governed LiveKit server URL and scoped media token.</span>
      </div>
    );
  }

  if (!isSupportedLiveKitServerUrl(normalizedServerUrl)) {
    onError?.("Invalid LiveKit server URL. Expected ws:// or wss:// endpoint from RTC Gateway.");
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
      audio
      video={videoEnabled}
      onConnected={onConnected}
      onDisconnected={onDisconnected}
      onError={(error) => onError?.(error.message)}
      className="h-full w-full rounded-md overflow-hidden bg-neutral-900 text-white"
    >
      <div className="h-full w-full flex flex-col">
        <div className="flex items-center gap-2 px-2 py-1 text-[10px] text-emerald-100 bg-emerald-950/60">
          <Activity className="h-3 w-3" />
          <span>Governed LiveKit media connected through Impilo RTC Gateway</span>
        </div>
        <LiveKitTrackGrid />
        <div className="border-t border-white/10 bg-black/70">
          <ControlBar
            variation="minimal"
            controls={{ microphone: true, camera: true, screenShare: false, chat: false, leave: true, settings: false }}
          />
        </div>
      </div>
      <RoomAudioRenderer />
      <ConnectionStateToast />
    </LiveKitRoom>
  );
}

function LiveKitTrackGrid() {
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
