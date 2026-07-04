import React, { useCallback, useEffect, useRef, useState } from "react";
import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import { VideoView } from "@livekit/react-native";
import { createLocalVideoTrack, type LocalVideoTrack } from "livekit-client";

export interface PreJoinChoices {
  micEnabled: boolean;
  cameraEnabled: boolean;
}

export interface PreJoinNativeProps {
  /** Called when the user confirms joining with their current device choices. */
  onJoin: (choices: PreJoinChoices) => void;
  /** Initial microphone toggle state. Default true. */
  initialMicEnabled?: boolean;
  /** Initial camera toggle state. Default true. */
  initialCameraEnabled?: boolean;
  /** Heading shown above the preview. */
  title?: string;
  /** Join button label. Default "Join session". */
  joinLabel?: string;
  /** Disable the join button (e.g. while a token is being fetched). */
  joinDisabled?: boolean;
}

/**
 * Pre-join device check for adaptive sessions: local camera preview plus
 * mic/camera toggles and a join button. Uses the LiveKit WebRTC globals the
 * apps already register in main.tsx (registerGlobals) — no room connection and
 * no additional native dependencies are required.
 */
export function PreJoinNative({
  onJoin,
  initialMicEnabled = true,
  initialCameraEnabled = true,
  title = "Ready to join?",
  joinLabel = "Join session",
  joinDisabled = false,
}: PreJoinNativeProps) {
  const [micEnabled, setMicEnabled] = useState(initialMicEnabled);
  const [cameraEnabled, setCameraEnabled] = useState(initialCameraEnabled);
  const [previewTrack, setPreviewTrack] = useState<LocalVideoTrack | undefined>(undefined);
  const [previewError, setPreviewError] = useState<string | undefined>(undefined);
  const previewRef = useRef<LocalVideoTrack | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;

    const stopPreview = () => {
      previewRef.current?.stop();
      previewRef.current = undefined;
      setPreviewTrack(undefined);
    };

    if (!cameraEnabled) {
      stopPreview();
      return stopPreview;
    }

    createLocalVideoTrack()
      .then((track) => {
        if (cancelled) {
          track.stop();
          return;
        }
        previewRef.current = track;
        setPreviewTrack(track);
        setPreviewError(undefined);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setPreviewError(error instanceof Error ? error.message : "Camera preview unavailable.");
      });

    return () => {
      cancelled = true;
      stopPreview();
    };
  }, [cameraEnabled]);

  const handleJoin = useCallback(() => {
    onJoin({ micEnabled, cameraEnabled });
  }, [cameraEnabled, micEnabled, onJoin]);

  return (
    <View style={styles.container} testID="prejoin">
      <Text style={styles.title}>{title}</Text>
      <View style={styles.preview} testID="prejoin-preview">
        {cameraEnabled && previewTrack ? (
          <VideoView videoTrack={previewTrack} style={styles.previewVideo} objectFit="cover" mirror />
        ) : (
          <View style={styles.previewFallback}>
            <Text style={styles.previewFallbackText}>
              {cameraEnabled
                ? previewError ?? "Starting camera preview…"
                : "Camera off — you will join without video."}
            </Text>
          </View>
        )}
      </View>
      <View style={styles.toggleRow}>
        <TouchableOpacity
          style={[styles.toggle, micEnabled ? styles.toggleOn : styles.toggleOff]}
          onPress={() => setMicEnabled((prev) => !prev)}
          testID="prejoin-toggle-mic"
          accessibilityRole="button"
          accessibilityLabel={micEnabled ? "Mute microphone" : "Unmute microphone"}
        >
          <Text style={styles.toggleText}>{micEnabled ? "Mic on" : "Mic off"}</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.toggle, cameraEnabled ? styles.toggleOn : styles.toggleOff]}
          onPress={() => setCameraEnabled((prev) => !prev)}
          testID="prejoin-toggle-camera"
          accessibilityRole="button"
          accessibilityLabel={cameraEnabled ? "Turn camera off" : "Turn camera on"}
        >
          <Text style={styles.toggleText}>{cameraEnabled ? "Camera on" : "Camera off"}</Text>
        </TouchableOpacity>
      </View>
      <TouchableOpacity
        style={[styles.joinButton, joinDisabled ? styles.joinButtonDisabled : null]}
        onPress={handleJoin}
        disabled={joinDisabled}
        testID="prejoin-join"
        accessibilityRole="button"
        accessibilityLabel={joinLabel}
      >
        <Text style={styles.joinButtonText}>{joinLabel}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: "100%",
    borderRadius: 12,
    padding: 16,
    backgroundColor: "#020617",
  },
  title: {
    color: "#f8fafc",
    fontSize: 16,
    fontWeight: "700",
    textAlign: "center",
  },
  preview: {
    marginTop: 12,
    minHeight: 200,
    borderRadius: 10,
    overflow: "hidden",
    backgroundColor: "#0f172a",
  },
  previewVideo: {
    minHeight: 200,
    width: "100%",
  },
  previewFallback: {
    minHeight: 200,
    alignItems: "center",
    justifyContent: "center",
    padding: 16,
  },
  previewFallbackText: {
    color: "#cbd5e1",
    fontSize: 12,
    textAlign: "center",
  },
  toggleRow: {
    marginTop: 12,
    flexDirection: "row",
    justifyContent: "center",
    gap: 12,
  },
  toggle: {
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderWidth: 1,
  },
  toggleOn: {
    backgroundColor: "rgba(22, 101, 52, 0.6)",
    borderColor: "#4ade80",
  },
  toggleOff: {
    backgroundColor: "rgba(51, 65, 85, 0.6)",
    borderColor: "#94a3b8",
  },
  toggleText: {
    color: "#f8fafc",
    fontSize: 12,
    fontWeight: "700",
  },
  joinButton: {
    marginTop: 16,
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center",
    backgroundColor: "#009739",
  },
  joinButtonDisabled: {
    opacity: 0.5,
  },
  joinButtonText: {
    color: "#f8fafc",
    fontSize: 14,
    fontWeight: "700",
  },
});
