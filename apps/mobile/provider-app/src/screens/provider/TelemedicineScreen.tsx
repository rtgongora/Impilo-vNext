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
import { apiClient } from "@impilo/mobile-api-client";
import { useChannel } from "@impilo/mobile-messaging";
import type { TelemedicineSession } from "../../types";

export function TelemedicineScreen() {
  const [sessions, setSessions] = useState<TelemedicineSession[]>([]);
  const [activeSession, setActiveSession] = useState<TelemedicineSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await apiClient.get<{
        data: {
          id: string;
          attributes: {
            encounter_id: string;
            patient_id: string;
            provider_id: string;
            status: TelemedicineSession["status"];
            scheduled_at: string;
            started_at?: string;
            ended_at?: string;
            session_token?: string;
            channel_id?: string;
          };
        }[];
      }>("/internal/v1/mobile/provider/telemedicine/sessions");
      setSessions(
        response.data.data.map((s) => ({
          id: s.id,
          encounterId: s.attributes.encounter_id,
          patientId: s.attributes.patient_id,
          providerId: s.attributes.provider_id,
          status: s.attributes.status,
          scheduledAt: s.attributes.scheduled_at,
          startedAt: s.attributes.started_at,
          endedAt: s.attributes.ended_at,
          sessionToken: s.attributes.session_token,
          channelId: s.attributes.channel_id,
        }))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load sessions");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const handleJoin = useCallback(async (session: TelemedicineSession) => {
    try {
      const response = await apiClient.post<{
        data: {
          id: string;
          attributes: {
            encounter_id: string;
            patient_id: string;
            provider_id: string;
            status: TelemedicineSession["status"];
            scheduled_at: string;
            started_at: string;
            session_token: string;
            channel_id: string;
          };
        };
      }>(`/internal/v1/mobile/provider/telemedicine/sessions/${session.id}/join`);
      const s = response.data.data;
      setActiveSession({
        id: s.id,
        encounterId: s.attributes.encounter_id,
        patientId: s.attributes.patient_id,
        providerId: s.attributes.provider_id,
        status: s.attributes.status,
        scheduledAt: s.attributes.scheduled_at,
        startedAt: s.attributes.started_at,
        sessionToken: s.attributes.session_token,
        channelId: s.attributes.channel_id,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to join session");
    }
  }, []);

  const handleEnd = useCallback(async () => {
    if (!activeSession) return;
    try {
      await apiClient.post(`/internal/v1/mobile/provider/telemedicine/sessions/${activeSession.id}/end`);
      setActiveSession(null);
      loadSessions();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to end session");
    }
  }, [activeSession, loadSessions]);

  // Real-time signaling for active session
  const { status: channelStatus } = useChannel(
    activeSession?.channelId ? `telehealth:${activeSession.channelId}` : "",
    {
      onMessage: (event) => {
        if (event.type === "session_ended") {
          setActiveSession(null);
          loadSessions();
        }
      },
    }
  );

  if (activeSession) {
    return (
      <Screen>
        <Header title="Telemedicine Session" />
        <View testID="telemedicine-active" style={styles.container}>
          <Card>
            <CardBody>
              <View style={styles.activeSessionCenter}>
                <Badge variant="primary">IN PROGRESS</Badge>
                <View testID="video-container" style={styles.videoContainer}>
                  <Text style={styles.videoText}>Video Stream Active</Text>
                </View>
                <View style={styles.actionRow}>
                  <Button
                    title="End Session"
                    variant="destructive"
                    onPress={handleEnd}
                    testID="end-session-btn"
                  />
                </View>
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
  actionRow: {
    flexDirection: "row",
    gap: 12,
    justifyContent: "center",
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
