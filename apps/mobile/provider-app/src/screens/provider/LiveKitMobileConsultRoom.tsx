import React, { useEffect } from "react";
import { View, Text, StyleSheet } from "react-native";
import {
  LiveKitRoom,
  VideoTrack,
  useConnectionState,
  useRoomContext,
  useTracks,
  type TrackReference,
} from "@livekit/react-native";
import { ConnectionState, Track } from "livekit-client";

interface LiveKitMobileConsultRoomProps {
  serverUrl?: string;
  token?: string;
  videoEnabled: boolean;
  micMuted: boolean;
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

export function LiveKitMobileConsultRoom({
  serverUrl,
  token,
  videoEnabled,
  micMuted,
  onConnected,
  onDisconnected,
  onError,
}: LiveKitMobileConsultRoomProps) {
  const normalizedServerUrl = normalizeLiveKitServerUrl(serverUrl);

  if (!normalizedServerUrl || !token) {
    return (
      <View style={styles.fallback}>
        <Text style={styles.fallbackTitle}>Media room not available yet</Text>
        <Text style={styles.fallbackText}>
          Live media is blocked until Telemedicine returns a governed LiveKit server URL and scoped token.
        </Text>
      </View>
    );
  }
  if (!isSupportedLiveKitServerUrl(normalizedServerUrl)) {
    onError?.("Invalid media endpoint returned by RTC Gateway. Expected ws:// or wss:// URL.");
    return (
      <View style={styles.fallback}>
        <Text style={styles.fallbackTitle}>Governed media endpoint invalid</Text>
        <Text style={styles.fallbackText}>Continue with async clinical notes and request platform support.</Text>
      </View>
    );
  }

  return (
    <LiveKitRoom
      serverUrl={normalizedServerUrl}
      token={token}
      connect
      audio={!micMuted}
      video={videoEnabled}
      onConnected={onConnected}
      onDisconnected={onDisconnected}
      onError={(error) => onError?.(error.message)}
    >
      <LiveKitRoomContent videoEnabled={videoEnabled} micMuted={micMuted} />
    </LiveKitRoom>
  );
}

function LiveKitRoomContent({ videoEnabled, micMuted }: { videoEnabled: boolean; micMuted: boolean }) {
  const room = useRoomContext();
  const connectionState = useConnectionState();
  const tracks = useTracks([{ source: Track.Source.Camera, withPlaceholder: false }], { onlySubscribed: false });
  const primaryTrack = tracks[0] as TrackReference | undefined;

  useEffect(() => {
    void room.localParticipant.setMicrophoneEnabled(!micMuted);
  }, [micMuted, room]);

  useEffect(() => {
    void room.localParticipant.setCameraEnabled(videoEnabled);
  }, [room, videoEnabled]);

  return (
    <View style={styles.stage}>
      {primaryTrack ? (
        <VideoTrack trackRef={primaryTrack} style={styles.videoTrack} objectFit="cover" />
      ) : (
        <View style={styles.fallback}>
          <Text style={styles.fallbackTitle}>{videoEnabled ? "Waiting for participant video" : "Audio only mode"}</Text>
          <Text style={styles.fallbackText}>Secure media is connected through Impilo RTC Gateway.</Text>
        </View>
      )}
      <View style={styles.statusPill}>
        <Text style={styles.statusText}>{connectionLabel(connectionState)}</Text>
      </View>
    </View>
  );
}

function connectionLabel(state: ConnectionState | string | undefined): string {
  if (state === ConnectionState.Connected || state === "connected") return "LiveKit connected";
  if (state === ConnectionState.Connecting || state === "connecting") return "LiveKit connecting";
  if (state === ConnectionState.Reconnecting || state === "reconnecting") return "LiveKit reconnecting";
  return "LiveKit media standby";
}

const styles = StyleSheet.create({
  stage: {
    minHeight: 220,
    width: "100%",
    overflow: "hidden",
    borderRadius: 12,
    backgroundColor: "#020617",
  },
  videoTrack: {
    minHeight: 220,
    width: "100%",
  },
  fallback: {
    minHeight: 220,
    alignItems: "center",
    justifyContent: "center",
    padding: 16,
    backgroundColor: "#020617",
  },
  fallbackTitle: {
    color: "#f8fafc",
    fontSize: 16,
    fontWeight: "700",
    textAlign: "center",
  },
  fallbackText: {
    color: "#cbd5e1",
    fontSize: 12,
    marginTop: 8,
    textAlign: "center",
  },
  statusPill: {
    position: "absolute",
    left: 12,
    bottom: 12,
    borderRadius: 999,
    backgroundColor: "rgba(15, 23, 42, 0.82)",
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  statusText: {
    color: "#bbf7d0",
    fontSize: 11,
    fontWeight: "700",
  },
});
