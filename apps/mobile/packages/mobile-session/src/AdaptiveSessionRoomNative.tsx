import React, { useEffect, useMemo } from "react";
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
import { isSupportedLiveKitServerUrl, normalizeLiveKitServerUrl } from "./normalizeLiveKitServerUrl";

export type AdaptiveSessionLayout = "consult" | "grid" | "stage";

export interface AdaptiveSessionRoomNativeProps {
  /** Governed LiveKit server URL as returned by the BFF / rtc-gateway (http(s) or ws(s)). */
  serverUrl?: string;
  /** Scoped LiveKit access token issued by the rtc-gateway. */
  token?: string;
  /** Local camera publish intent. */
  videoEnabled: boolean;
  /** Local microphone mute state (true = muted). */
  micMuted: boolean;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (message: string) => void;
  /**
   * Participant rendering layout:
   * - "consult" (default): remote participant large, local participant as a small overlay.
   * - "grid": all camera tracks as flex-wrap tiles.
   * - "stage": remote publishers only (e.g. webinars / broadcasts).
   */
  layout?: AdaptiveSessionLayout;
  /** Audience mode: never publish local audio/video (view/listen only). */
  audience?: boolean;
  /** Audio-only session: never publish local video and skip video rendering intent. */
  audioOnly?: boolean;
  /** Show a banner while the room connection is reconnecting. */
  showReconnectBanner?: boolean;
  /** Show a recording badge on the media stage. */
  showRecordingBadge?: boolean;
  /** Override copy shown when no governed server URL / token is available yet. */
  unavailableMessage?: string;
  /** Override copy shown when the governed endpoint is not a ws:// or wss:// URL. */
  invalidEndpointMessage?: string;
}

const DEFAULT_UNAVAILABLE_MESSAGE =
  "Live media is blocked until Impilo returns a governed LiveKit server URL and scoped token.";
const DEFAULT_INVALID_ENDPOINT_MESSAGE =
  "Continue with secure chat/notes and request platform support.";

/**
 * Shared adaptive LiveKit room for Impilo mobile apps (citizen + provider).
 * Unifies the previously duplicated LiveKitMobileConsultRoom implementations.
 */
export function AdaptiveSessionRoomNative({
  serverUrl,
  token,
  videoEnabled,
  micMuted,
  onConnected,
  onDisconnected,
  onError,
  layout = "consult",
  audience = false,
  audioOnly = false,
  showReconnectBanner = false,
  showRecordingBadge = false,
  unavailableMessage = DEFAULT_UNAVAILABLE_MESSAGE,
  invalidEndpointMessage = DEFAULT_INVALID_ENDPOINT_MESSAGE,
}: AdaptiveSessionRoomNativeProps) {
  const normalizedServerUrl = normalizeLiveKitServerUrl(serverUrl);
  const publishAudio = !micMuted && !audience;
  const publishVideo = videoEnabled && !audience && !audioOnly;

  if (!normalizedServerUrl || !token) {
    return (
      <View style={styles.fallback} testID="session-room-unavailable">
        <Text style={styles.fallbackTitle}>Media room not available yet</Text>
        <Text style={styles.fallbackText}>{unavailableMessage}</Text>
      </View>
    );
  }
  if (!isSupportedLiveKitServerUrl(normalizedServerUrl)) {
    onError?.("Invalid media endpoint returned by RTC Gateway. Expected ws:// or wss:// URL.");
    return (
      <View style={styles.fallback} testID="session-room-invalid-endpoint">
        <Text style={styles.fallbackTitle}>Governed media endpoint invalid</Text>
        <Text style={styles.fallbackText}>{invalidEndpointMessage}</Text>
      </View>
    );
  }

  return (
    <LiveKitRoom
      serverUrl={normalizedServerUrl}
      token={token}
      connect
      audio={publishAudio}
      video={publishVideo}
      onConnected={onConnected}
      onDisconnected={onDisconnected}
      onError={(error) => onError?.(error.message)}
    >
      <AdaptiveSessionRoomContent
        layout={layout}
        audience={audience}
        audioOnly={audioOnly}
        videoEnabled={videoEnabled}
        micMuted={micMuted}
        showReconnectBanner={showReconnectBanner}
        showRecordingBadge={showRecordingBadge}
      />
    </LiveKitRoom>
  );
}

interface AdaptiveSessionRoomContentProps {
  layout: AdaptiveSessionLayout;
  audience: boolean;
  audioOnly: boolean;
  videoEnabled: boolean;
  micMuted: boolean;
  showReconnectBanner: boolean;
  showRecordingBadge: boolean;
}

function AdaptiveSessionRoomContent({
  layout,
  audience,
  audioOnly,
  videoEnabled,
  micMuted,
  showReconnectBanner,
  showRecordingBadge,
}: AdaptiveSessionRoomContentProps) {
  const room = useRoomContext();
  const connectionState = useConnectionState();
  const tracks = useTracks([{ source: Track.Source.Camera, withPlaceholder: false }], { onlySubscribed: false });

  const publishAudio = !micMuted && !audience;
  const publishVideo = videoEnabled && !audience && !audioOnly;

  useEffect(() => {
    void room.localParticipant.setMicrophoneEnabled(publishAudio);
  }, [publishAudio, room]);

  useEffect(() => {
    void room.localParticipant.setCameraEnabled(publishVideo);
  }, [publishVideo, room]);

  const { localTracks, remoteTracks } = useMemo(() => {
    const all = tracks as TrackReference[];
    return {
      localTracks: all.filter((track) => track.participant?.isLocal === true),
      remoteTracks: all.filter((track) => track.participant?.isLocal !== true),
    };
  }, [tracks]);

  const isReconnecting =
    connectionState === ConnectionState.Reconnecting ||
    (connectionState as ConnectionState | string) === "reconnecting";

  return (
    <View style={styles.stage} testID="session-room-stage">
      {renderLayout({ layout, audioOnly, videoEnabled, localTracks, remoteTracks })}
      {showReconnectBanner && isReconnecting ? (
        <View style={styles.reconnectBanner} testID="session-room-reconnect-banner">
          <Text style={styles.reconnectText}>Reconnecting to governed media…</Text>
        </View>
      ) : null}
      {showRecordingBadge ? (
        <View style={styles.recordingBadge} testID="session-room-recording-badge">
          <View style={styles.recordingDot} />
          <Text style={styles.recordingText}>REC</Text>
        </View>
      ) : null}
      <View style={styles.statusPill}>
        <Text style={styles.statusText}>{connectionLabel(connectionState)}</Text>
      </View>
    </View>
  );
}

function renderLayout({
  layout,
  audioOnly,
  videoEnabled,
  localTracks,
  remoteTracks,
}: {
  layout: AdaptiveSessionLayout;
  audioOnly: boolean;
  videoEnabled: boolean;
  localTracks: TrackReference[];
  remoteTracks: TrackReference[];
}) {
  if (audioOnly) {
    return renderEmptyStage("Audio only mode");
  }

  if (layout === "grid") {
    const gridTracks = [...remoteTracks, ...localTracks];
    if (gridTracks.length === 0) {
      return renderEmptyStage(videoEnabled ? "Waiting for participant video" : "Audio only mode");
    }
    return (
      <View style={styles.grid} testID="session-room-grid">
        {gridTracks.map((track, index) => (
          <VideoTrack
            key={trackKey(track, index)}
            trackRef={track}
            style={styles.gridTile}
            objectFit="cover"
          />
        ))}
      </View>
    );
  }

  if (layout === "stage") {
    if (remoteTracks.length === 0) {
      return renderEmptyStage("Waiting for the stage to go live");
    }
    return (
      <View style={styles.grid} testID="session-room-stage-publishers">
        {remoteTracks.map((track, index) => (
          <VideoTrack
            key={trackKey(track, index)}
            trackRef={track}
            style={remoteTracks.length > 1 ? styles.gridTile : styles.videoTrack}
            objectFit="cover"
          />
        ))}
      </View>
    );
  }

  // "consult": remote large + local small overlay. Falls back to any available
  // track as primary (matches the legacy single-track behavior).
  const primaryTrack = remoteTracks[0] ?? localTracks[0];
  const overlayTrack =
    primaryTrack && localTracks[0] && localTracks[0] !== primaryTrack ? localTracks[0] : undefined;

  if (!primaryTrack) {
    return renderEmptyStage(videoEnabled ? "Waiting for participant video" : "Audio only mode");
  }

  return (
    <View style={styles.consult} testID="session-room-consult">
      <VideoTrack trackRef={primaryTrack} style={styles.videoTrack} objectFit="cover" />
      {overlayTrack ? (
        <View style={styles.localOverlay} testID="session-room-local-overlay">
          <VideoTrack trackRef={overlayTrack} style={styles.localOverlayVideo} objectFit="cover" />
        </View>
      ) : null}
    </View>
  );
}

function renderEmptyStage(title: string) {
  return (
    <View style={styles.fallback}>
      <Text style={styles.fallbackTitle}>{title}</Text>
      <Text style={styles.fallbackText}>Secure media is connected through Impilo RTC Gateway.</Text>
    </View>
  );
}

function trackKey(track: TrackReference, index: number): string {
  return track.publication?.trackSid ?? `${track.participant?.identity ?? "participant"}-${index}`;
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
  consult: {
    minHeight: 220,
    width: "100%",
  },
  videoTrack: {
    minHeight: 220,
    width: "100%",
  },
  localOverlay: {
    position: "absolute",
    right: 12,
    top: 12,
    width: 96,
    height: 128,
    borderRadius: 10,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "rgba(148, 163, 184, 0.6)",
    backgroundColor: "#0f172a",
  },
  localOverlayVideo: {
    width: "100%",
    height: "100%",
  },
  grid: {
    minHeight: 220,
    width: "100%",
    flexDirection: "row",
    flexWrap: "wrap",
  },
  gridTile: {
    width: "50%",
    minHeight: 110,
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
  reconnectBanner: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    paddingVertical: 6,
    alignItems: "center",
    backgroundColor: "rgba(180, 83, 9, 0.9)",
  },
  reconnectText: {
    color: "#fffbeb",
    fontSize: 11,
    fontWeight: "700",
  },
  recordingBadge: {
    position: "absolute",
    right: 12,
    bottom: 12,
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 999,
    backgroundColor: "rgba(153, 27, 27, 0.9)",
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  recordingDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: "#fecaca",
    marginRight: 6,
  },
  recordingText: {
    color: "#fef2f2",
    fontSize: 11,
    fontWeight: "700",
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
