/**
 * TelemedicineScreen — Video/audio consultation participation.
 *
 * Manages telemedicine session lifecycle: join → in-progress → end.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useChannel } from "@impilo/mobile-messaging";
import { useAuth } from "@impilo/mobile-auth";
import { useAppStore } from "../../stores/appStore";
import type { TelemedicineSession } from "../../types";
import {
  endProviderTelemedicineSession,
  joinProviderTelemedicineSession,
  listProviderTelemedicineSessions,
  sendProviderTelemedicineSignal,
} from "../../services/telemedicineService";
import { LiveKitMobileConsultRoom } from "./LiveKitMobileConsultRoom";

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

export function TelemedicineScreen() {
  const auth = useAuth();
  const { facilityId } = useAppStore();
  const [sessions, setSessions] = useState<TelemedicineSession[]>([]);
  const [activeSession, setActiveSession] = useState<TelemedicineSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);
  const [micMuted, setMicMuted] = useState(false);
  const [videoEnabled, setVideoEnabled] = useState(true);
  const [sendingSignal, setSendingSignal] = useState<"delay" | "nudge" | "support" | null>(null);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSessions(await listProviderTelemedicineSessions({
        facilityId,
        providerId: auth.user?.sub,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load sessions");
    } finally {
      setLoading(false);
    }
  }, [auth.user?.sub, facilityId]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const handleJoin = useCallback(async (session: TelemedicineSession) => {
    if (!session.id) {
      setError("Cannot join session: missing identifier.");
      return;
    }
    try {
      const next = await joinProviderTelemedicineSession(session.id);
      setActiveSession(next);
      if (!next.channelId) {
        setSessionNotice("Session joined, but realtime channel is missing. Use async notes and retry reconnect.");
      } else {
        setSessionNotice(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to join session");
    }
  }, []);

  const handleEnd = useCallback(async () => {
    if (!activeSession) return;
    try {
      await endProviderTelemedicineSession(activeSession.id);
      setActiveSession(null);
      loadSessions();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to end session");
    }
  }, [activeSession, loadSessions]);

  const postSessionSignal = useCallback(
    async (kind: "delay" | "nudge" | "support") => {
      if (!activeSession?.id) return;
      setSendingSignal(kind);
      try {
        await sendProviderTelemedicineSignal(activeSession.id, kind);
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
    [activeSession?.id]
  );

  // Real-time signaling for active session
  const { status: channelStatus } = useChannel(
    activeSession?.channelId ? `telehealth:${activeSession.channelId}` : "",
    {
      onMessage: (event) => {
        if (event.type === "session_ended" || event.type === "telemedicine.session.completed") {
          setActiveSession(null);
          loadSessions();
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

  if (activeSession) {
    return (
      <Screen>
        <Header title="Telemedicine Session" />
        <View testID="telemedicine-active" style={styles.container}>
          <Card>
            <CardBody>
              <View style={styles.activeSessionCenter}>
                <Badge variant="primary">IN PROGRESS</Badge>
                <View style={styles.channelBadge}>
                  <Badge variant={channelHealth.variant}>{channelHealth.label}</Badge>
                </View>
                <View testID="video-container" style={styles.videoContainer}>
                  <LiveKitMobileConsultRoom
                    serverUrl={activeSession.roomUrl}
                    token={activeSession.sessionToken}
                    videoEnabled={videoEnabled}
                    micMuted={micMuted}
                    onConnected={() => setSessionNotice("LiveKit media connected through Impilo RTC Gateway.")}
                    onDisconnected={() => setSessionNotice("LiveKit media disconnected. Clinical notes remain available.")}
                    onError={(message) => setSessionNotice(message)}
                  />
                </View>
                <View style={styles.actionRow}>
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
                    title="End Session"
                    variant="destructive"
                    onPress={handleEnd}
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
                {activeSession.sessionToken ? null : (
                  <Text style={styles.noticeWarning}>
                    Session token missing. Continue with async notes while support resolves media session setup.
                  </Text>
                )}
              </View>
            </CardBody>
          </Card>
        </View>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Telemedicine" />
      <ScrollView testID="telemedicine-screen" style={styles.container}>
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadSessions} />
        ) : sessions.length === 0 ? (
          <EmptyState title="No scheduled sessions" message="Telemedicine sessions will appear here" />
        ) : (
          sessions.map((session) => (
            <Card key={session.id}>
              <CardBody>
                <View testID={`session-${session.id}`} style={styles.sessionRow}>
                  <View>
                    <Badge variant="secondary">{session.status}</Badge>
                    <Text style={styles.scheduledText}>
                      {`Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`}
                    </Text>
                  </View>
                  {session.status === "SCHEDULED" || session.status === "WAITING" ? (
                    <Button
                      title="Join"
                      variant="primary"
                      onPress={() => handleJoin(session)}
                      testID={`join-session-${session.id}`}
                    />
                  ) : null}
                </View>
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  activeSessionCenter: {
    alignItems: "center",
    paddingVertical: 48,
  },
  videoContainer: {
    width: "100%",
    height: 300,
    backgroundColor: "#1F2937",
    borderRadius: 12,
    marginVertical: 24,
    alignItems: "center",
    justifyContent: "center",
  },
  videoText: {
    color: "#FFFFFF",
    fontSize: 18,
  },
  mediaText: {
    color: "#D1D5DB",
    fontSize: 12,
    textAlign: "center",
    marginTop: 8,
    paddingHorizontal: 12,
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
  channelBadge: {
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
  sessionRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  scheduledText: {
    fontSize: 14,
    marginTop: 4,
  },
});
