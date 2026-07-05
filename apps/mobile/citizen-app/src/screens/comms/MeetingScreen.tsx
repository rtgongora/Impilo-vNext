import React, { useCallback, useEffect, useRef, useState } from "react";
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { AdaptiveSessionRoomNative } from "@impilo/mobile-session";
import {
  endMeeting,
  joinMeeting,
  raiseHand,
  sendMeetingReaction,
  type CommsMeeting,
} from "../../services/khulumaCommsService";

const REACTIONS = ["👏", "👍", "❤️"] as const;
const KNOCK_POLL_MS = 4000;

/**
 * MeetingScreen — join-capable W5 MEETING surface (template mobileParity: JOIN_CAPABLE).
 * Handles the KNOCK lobby (re-knocks every 4s while WAITING), then renders the shared
 * AdaptiveSessionRoomNative grid with raise-hand + reactions.
 *
 * Parity note (web-only by design this wave): lobby moderation (admit/deny), cohost
 * management, invite-link minting, agenda/notes editing and the attendance summary live
 * in the web /meet room; mobile participants join, publish, raise hands and react.
 */
export function MeetingScreen({
  conversationId,
  title,
  onLeave,
}: {
  conversationId: string;
  title?: string | null;
  onLeave: () => void;
}) {
  const [join, setJoin] = useState<CommsMeeting | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [handRaised, setHandRaised] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);

  const knock = useCallback(async () => {
    try {
      const result = await joinMeeting(conversationId);
      if (!mountedRef.current) return;
      setJoin(result);
      setError(null);
      const status = result.status ?? (result.accessToken ? "READY" : "UNAVAILABLE");
      if (status === "WAITING") {
        timerRef.current = setTimeout(() => void knock(), KNOCK_POLL_MS);
      }
    } catch {
      if (!mountedRef.current) return;
      setError("Could not reach the meeting service.");
    }
  }, [conversationId]);

  useEffect(() => {
    mountedRef.current = true;
    void knock();
    return () => {
      mountedRef.current = false;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [knock]);

  const handleLeave = useCallback(async () => {
    // Leaving is not ending: only disconnect this participant; the host ends the room.
    onLeave();
  }, [onLeave]);

  const status = join?.status ?? (join?.accessToken ? "READY" : join ? "UNAVAILABLE" : "JOINING");
  const ready = status === "READY" && !!join?.accessToken && !!join?.roomUrl;

  if (!ready) {
    return (
      <View testID="meeting-lobby" style={styles.lobby}>
        {status === "JOINING" && <ActivityIndicator testID="meeting-joining" />}
        {status === "WAITING" && (
          <>
            <Text testID="meeting-waiting" style={styles.lobbyTitle}>
              Waiting for a host to let you in…
            </Text>
            <Text style={styles.muted}>{title ?? "Meeting"} — you have knocked.</Text>
          </>
        )}
        {status === "DENIED" && (
          <Text testID="meeting-denied" style={styles.lobbyTitle}>
            The host did not admit you to this meeting.
          </Text>
        )}
        {(status === "UNAVAILABLE" || error) && (
          <Text testID="meeting-unavailable" style={styles.muted}>
            {error ?? join?.error ?? "Meeting media is unavailable right now."}
          </Text>
        )}
        <TouchableOpacity testID="meeting-leave" style={styles.leaveButton} onPress={handleLeave}>
          <Text style={styles.leaveText}>Back</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View testID="meeting-room" style={styles.container}>
      <Text style={styles.title}>{title ?? "Meeting"}</Text>
      <View style={styles.stage}>
        <AdaptiveSessionRoomNative
          serverUrl={join?.roomUrl ?? undefined}
          token={join?.accessToken ?? undefined}
          layout="grid"
          videoEnabled
          micMuted={false}
          showReconnectBanner
          showRecordingBadge
          unavailableMessage="Meeting media is blocked until Impilo returns a governed room URL and scoped token."
        />
      </View>
      <View style={styles.controls}>
        <TouchableOpacity
          testID="meeting-raise-hand"
          style={[styles.controlButton, handRaised && styles.controlActive]}
          onPress={() => {
            const next = !handRaised;
            setHandRaised(next);
            void raiseHand(conversationId, next).catch(() => undefined);
          }}
        >
          <Text style={styles.controlText}>{handRaised ? "Lower hand" : "Raise hand"}</Text>
        </TouchableOpacity>
        {REACTIONS.map((emoji) => (
          <TouchableOpacity
            key={emoji}
            testID={`meeting-reaction-${emoji}`}
            style={styles.controlButton}
            onPress={() => void sendMeetingReaction(conversationId, emoji).catch(() => undefined)}
          >
            <Text style={styles.controlText}>{emoji}</Text>
          </TouchableOpacity>
        ))}
        <TouchableOpacity testID="meeting-leave" style={styles.leaveButton} onPress={handleLeave}>
          <Text style={styles.leaveText}>Leave</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

/** Host-side end (kept for the host affordance in the hub). */
export async function endMeetingRoom(conversationId: string): Promise<void> {
  await endMeeting(conversationId).catch(() => undefined);
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#0b0f14", padding: 12 },
  title: { color: "#fff", fontSize: 16, fontWeight: "600", marginBottom: 8 },
  stage: { flex: 1, borderRadius: 8, overflow: "hidden" },
  controls: { flexDirection: "row", gap: 8, marginTop: 10, alignItems: "center", flexWrap: "wrap" },
  controlButton: { backgroundColor: "#1f2937", borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8 },
  controlActive: { backgroundColor: "#d97706" },
  controlText: { color: "#fff", fontSize: 13 },
  leaveButton: { backgroundColor: "#b91c1c", borderRadius: 8, paddingHorizontal: 14, paddingVertical: 8, marginLeft: "auto" },
  leaveText: { color: "#fff", fontWeight: "600" },
  lobby: { flex: 1, alignItems: "center", justifyContent: "center", gap: 10, backgroundColor: "#0b0f14", padding: 20 },
  lobbyTitle: { color: "#fff", fontSize: 15, fontWeight: "600", textAlign: "center" },
  muted: { color: "#9ca3af", fontSize: 13, textAlign: "center" },
});
