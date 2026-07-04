/**
 * TelemedicineCallScreen — Full-screen governed consult call for a joined
 * telemedicine session: adaptive LiveKit room (consult layout with local PiP
 * overlay), audio-first toggle, realtime channel health, teleconsult signals,
 * and end-call with an optional clinical note.
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  TextField,
} from "@impilo/mobile-design-system";
import { AdaptiveSessionRoomNative } from "@impilo/mobile-session";
import { useChannel } from "@impilo/mobile-messaging";
import type { TelemedicineSession } from "../../types";
import {
  endProviderTelemedicineSession,
  sendProviderTelemedicineSignal,
  requestProviderTelemedicineMediaToken,
  refreshProviderTelemedicineMediaToken,
} from "../../services/telemedicineService";

export function mapChannelHealth(status: string | undefined): {
  label: string;
  variant: "primary" | "warning" | "secondary" | "destructive";
} {
  const value = String(status ?? "").toLowerCase();
  if (value === "connected" || value === "open" || value === "ready") {
    return { label: "Realtime connected", variant: "primary" };
  }
  if (value === "connecting" || value === "reconnecting") {
    return { label: "Realtime reconnecting", variant: "warning" };
  }
  if (value === "error" || value === "closed" || value === "offline") {
    return { label: "Realtime unavailable", variant: "destructive" };
  }
  return { label: "Realtime unknown", variant: "secondary" };
}

export interface TelemedicineCallScreenProps {
  session: TelemedicineSession;
  /** Display name for the provider participant in the governed room. */
  displayName?: string;
  /** Called after the session has been ended (or the remote side completed it). */
  onEnded: () => void;
}

export function TelemedicineCallScreen({ session, displayName, onEnded }: TelemedicineCallScreenProps) {
  // Fall back to the join-payload credentials until the shared media-token route answers.
  const [mediaRoomUrl, setMediaRoomUrl] = useState<string | undefined>(session.roomUrl);
  const [mediaToken, setMediaToken] = useState<string | undefined>(session.sessionToken);
  const [audioOnly, setAudioOnly] = useState(false);
  const [micMuted, setMicMuted] = useState(false);
  const [videoEnabled, setVideoEnabled] = useState(true);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showEndForm, setShowEndForm] = useState(false);
  const [endNote, setEndNote] = useState("");
  const [isEnding, setIsEnding] = useState(false);
  const [sendingSignal, setSendingSignal] = useState<"delay" | "nudge" | "support" | null>(null);
  const tokenRequested = useRef(false);

  const participantName = displayName ?? "Impilo provider";

  const mediaRequest = useCallback(
    () => ({
      displayName: participantName,
      ...(audioOnly ? { mediaProfile: "AUDIO_ONLY" as const } : {}),
    }),
    [participantName, audioOnly]
  );

  // Parity with the web contract: request a scoped provider token from the
  // shared teleconsult media route; keep the join-payload credentials as a
  // fallback if the route is unavailable.
  useEffect(() => {
    if (tokenRequested.current) return;
    tokenRequested.current = true;
    let cancelled = false;
    requestProviderTelemedicineMediaToken(session.id, mediaRequest())
      .then((result) => {
        if (cancelled) return;
        if (result.status === "READY" && result.token) {
          setMediaRoomUrl(result.roomUrl ?? session.roomUrl);
          setMediaToken(result.token);
        }
      })
      .catch(() => {
        if (!cancelled && !session.sessionToken) {
          setSessionNotice("Governed media token unavailable. Continue with async notes and retry shortly.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [session.id, session.roomUrl, session.sessionToken, mediaRequest]);

  const handleRefreshMedia = useCallback(async () => {
    setError(null);
    try {
      const result = await refreshProviderTelemedicineMediaToken(session.id, mediaRequest());
      if (result.status === "READY" && result.token) {
        setMediaRoomUrl(result.roomUrl ?? mediaRoomUrl);
        setMediaToken(result.token);
        setSessionNotice("Media connection refreshed.");
      } else {
        setSessionNotice("Media token refresh pending. Try again shortly.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refresh media token");
    }
  }, [session.id, mediaRequest, mediaRoomUrl]);

  const handleEnd = useCallback(async () => {
    setIsEnding(true);
    setError(null);
    try {
      await endProviderTelemedicineSession(session.id, endNote.trim() ? endNote.trim() : undefined);
      onEnded();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to end session");
    } finally {
      setIsEnding(false);
    }
  }, [session.id, endNote, onEnded]);

  const postSessionSignal = useCallback(
    async (kind: "delay" | "nudge" | "support") => {
      setSendingSignal(kind);
      try {
        await sendProviderTelemedicineSignal(session.id, kind);
        setSessionNotice(
          kind === "delay"
            ? "Delay notice sent to client."
            : kind === "nudge"
              ? "No-show nudge sent to client."
              : "Support escalation sent."
        );
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to send teleconsult signal");
      } finally {
        setSendingSignal(null);
      }
    },
    [session.id]
  );

  // Real-time signaling for the active session
  const { status: channelStatus } = useChannel(
    session.channelId ? `telehealth:${session.channelId}` : "",
    {
      onMessage: (event) => {
        if (event.type === "session_ended" || event.type === "telemedicine.session.completed") {
          onEnded();
        } else if (event.type === "telemedicine.session.client_not_joined") {
          setSessionNotice("Client has not joined yet. A no-show nudge has been sent.");
        } else if (event.type === "telemedicine.session.waiting_room.entered") {
          setSessionNotice("Client is waiting in the telemedicine room.");
        } else if (event.type === "telemedicine.session.provider_late") {
          setSessionNotice("Provider delay notification was sent to client.");
        } else if (event.type === "telemedicine.session.support_requested") {
          setSessionNotice("Join support requested. Escalation to helpdesk has been raised.");
        }
      },
    }
  );
  const channelHealth = mapChannelHealth(channelStatus);

  return (
    <Screen>
      <Header title="Telemedicine Session" />
      <ScrollView testID="telemedicine-call-screen" style={styles.container}>
        <Card>
          <CardBody>
            <View style={styles.center}>
              <Badge variant="primary">IN PROGRESS</Badge>
              <View style={styles.channelBadge}>
                <Badge variant={channelHealth.variant}>{channelHealth.label}</Badge>
              </View>
              <View testID="video-container" style={styles.videoContainer}>
                <AdaptiveSessionRoomNative
                  serverUrl={mediaRoomUrl}
                  token={mediaToken}
                  layout="consult"
                  audioOnly={audioOnly}
                  videoEnabled={videoEnabled}
                  micMuted={micMuted}
                  showReconnectBanner
                  onConnected={() => setSessionNotice("LiveKit media connected through Impilo RTC Gateway.")}
                  onDisconnected={() => setSessionNotice("LiveKit media disconnected. Clinical notes remain available.")}
                  onError={(message) => setSessionNotice(message)}
                  unavailableMessage="Live media is blocked until Telemedicine returns a governed LiveKit server URL and scoped token."
                  invalidEndpointMessage="Continue with async clinical notes and request platform support."
                />
              </View>
              <View style={styles.actionRow}>
                <Button
                  title={audioOnly ? "Audio only: on" : "Audio only: off"}
                  variant="outline"
                  onPress={() => setAudioOnly((prev) => !prev)}
                  testID="telemedicine-audio-only-toggle"
                />
                <Button
                  title={videoEnabled ? "Disable Video" : "Enable Video"}
                  variant="outline"
                  onPress={() => setVideoEnabled((prev) => !prev)}
                  testID="toggle-video-btn"
                />
                <Button
                  title={micMuted ? "Unmute Mic" : "Mute Mic"}
                  variant="outline"
                  onPress={() => setMicMuted((prev) => !prev)}
                  testID="toggle-mic-btn"
                />
                <Button
                  title="Refresh Media"
                  variant="outline"
                  onPress={handleRefreshMedia}
                  testID="refresh-media-btn"
                />
                <Button
                  title="End Call"
                  variant="destructive"
                  onPress={() => setShowEndForm(true)}
                  testID="end-session-btn"
                />
              </View>
              <View style={styles.signalRow}>
                <Button
                  title="Client Nudge"
                  variant="outline"
                  disabled={sendingSignal !== null}
                  onPress={() => postSessionSignal("nudge")}
                  testID="signal-nudge-btn"
                />
                <Button
                  title="Delay Notice"
                  variant="outline"
                  disabled={sendingSignal !== null}
                  onPress={() => postSessionSignal("delay")}
                  testID="signal-delay-btn"
                />
                <Button
                  title="Request Support"
                  variant="outline"
                  disabled={sendingSignal !== null}
                  onPress={() => postSessionSignal("support")}
                  testID="signal-support-btn"
                />
              </View>
              <Text style={styles.noticeText}>{`Realtime channel: ${channelStatus}`}</Text>
              {sessionNotice ? <Text style={styles.noticeText}>{sessionNotice}</Text> : null}
              {error ? <Text style={styles.noticeWarning}>{error}</Text> : null}
              {mediaToken ? null : (
                <Text style={styles.noticeWarning}>
                  Session token missing. Continue with async notes while support resolves media session setup.
                </Text>
              )}
            </View>
          </CardBody>
        </Card>
        {showEndForm ? (
          <Card>
            <CardBody>
              <View style={styles.endForm} testID="telemedicine-end-form">
                <TextField
                  label="Closing note (optional)"
                  value={endNote}
                  onChange={setEndNote}
                  placeholder="Optional clinical wrap-up note"
                  testID="end-call-note"
                />
                <View style={styles.actionRow}>
                  <Button
                    title={isEnding ? "Ending..." : "Confirm End Call"}
                    variant="destructive"
                    onPress={handleEnd}
                    disabled={isEnding}
                    testID="confirm-end-call"
                  />
                  <Button
                    title="Keep Session"
                    variant="ghost"
                    onPress={() => setShowEndForm(false)}
                    testID="cancel-end-call"
                  />
                </View>
              </View>
            </CardBody>
          </Card>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  center: {
    alignItems: "center",
    paddingVertical: 24,
  },
  channelBadge: {
    marginTop: 8,
  },
  videoContainer: {
    width: "100%",
    minHeight: 300,
    backgroundColor: "#1F2937",
    borderRadius: 12,
    marginVertical: 24,
    overflow: "hidden",
  },
  actionRow: {
    flexDirection: "row",
    gap: 12,
    justifyContent: "center",
    flexWrap: "wrap",
  },
  signalRow: {
    flexDirection: "row",
    gap: 8,
    flexWrap: "wrap",
    justifyContent: "center",
    marginTop: 8,
  },
  noticeText: {
    fontSize: 13,
    color: "#374151",
    marginTop: 12,
    textAlign: "center",
  },
  noticeWarning: {
    fontSize: 12,
    color: "#92400E",
    marginTop: 8,
    textAlign: "center",
  },
  endForm: {
    gap: 12,
  },
});
