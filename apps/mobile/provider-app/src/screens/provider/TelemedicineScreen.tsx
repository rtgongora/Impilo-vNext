/**
 * TelemedicineScreen — Video/audio consultation participation.
 *
 * Lists sessions with a per-session governed waiting room (admit/deny) and
 * hands the active call over to TelemedicineCallScreen.
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
import { useAuth } from "@impilo/mobile-auth";
import { useAppStore } from "../../stores/appStore";
import type { TelemedicineSession } from "../../types";
import {
  admitTelemedicineParticipant,
  denyTelemedicineParticipant,
  fetchTelemedicineWaitingRoom,
  joinProviderTelemedicineSession,
  listProviderTelemedicineSessions,
  type WaitingRoomParticipant,
} from "../../services/telemedicineService";
import { TelemedicineCallScreen } from "./TelemedicineCallScreen";

export { mapChannelHealth } from "./TelemedicineCallScreen";

const WAITING_ROOM_POLL_MS = 10_000;

/** Sessions whose waiting room is worth watching. */
const WAITING_ROOM_STATUSES = new Set(["SCHEDULED", "WAITING", "IN_PROGRESS"]);

export function TelemedicineScreen() {
  const auth = useAuth();
  const { facilityId } = useAppStore();
  const [sessions, setSessions] = useState<TelemedicineSession[]>([]);
  const [activeSession, setActiveSession] = useState<TelemedicineSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [waitingRooms, setWaitingRooms] = useState<Record<string, WaitingRoomParticipant[]>>({});
  const [admissionBusy, setAdmissionBusy] = useState<string | null>(null);
  const [admissionNotice, setAdmissionNotice] = useState<string | null>(null);

  const providerDisplayName =
    [auth.user?.given_name, auth.user?.family_name].filter(Boolean).join(" ") ||
    auth.user?.preferred_username ||
    "Impilo provider";

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

  const refreshWaitingRooms = useCallback(async (candidates: TelemedicineSession[]) => {
    const watchable = candidates.filter((session) => session.id && WAITING_ROOM_STATUSES.has(session.status));
    if (watchable.length === 0) return;
    const entries = await Promise.all(
      watchable.map(async (session) => {
        try {
          return [session.id, await fetchTelemedicineWaitingRoom(session.id)] as const;
        } catch {
          // Waiting room is best-effort; keep the previous snapshot on failure.
          return null;
        }
      })
    );
    setWaitingRooms((prev) => {
      const next = { ...prev };
      for (const entry of entries) {
        if (entry) next[entry[0]] = entry[1];
      }
      return next;
    });
  }, []);

  // Poll the governed waiting room for watchable sessions every 10s.
  useEffect(() => {
    if (activeSession || sessions.length === 0) return;
    void refreshWaitingRooms(sessions);
    const interval = setInterval(() => {
      void refreshWaitingRooms(sessions);
    }, WAITING_ROOM_POLL_MS);
    return () => clearInterval(interval);
  }, [sessions, activeSession, refreshWaitingRooms]);

  const handleJoin = useCallback(async (session: TelemedicineSession) => {
    if (!session.id) {
      setError("Cannot join session: missing identifier.");
      return;
    }
    try {
      const next = await joinProviderTelemedicineSession(session.id);
      setActiveSession(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to join session");
    }
  }, []);

  const handleAdmit = useCallback(async (session: TelemedicineSession, participant: WaitingRoomParticipant) => {
    setAdmissionBusy(participant.identity);
    setError(null);
    try {
      await admitTelemedicineParticipant(session.id, participant.identity);
      setAdmissionNotice(`${participant.displayName ?? participant.identity} admitted to the consult.`);
      await refreshWaitingRooms([session]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to admit participant");
    } finally {
      setAdmissionBusy(null);
    }
  }, [refreshWaitingRooms]);

  const handleDeny = useCallback(async (session: TelemedicineSession, participant: WaitingRoomParticipant) => {
    setAdmissionBusy(participant.identity);
    setError(null);
    try {
      await denyTelemedicineParticipant(session.id, participant.identity, "Provider declined admission");
      setAdmissionNotice(`${participant.displayName ?? participant.identity} was not admitted.`);
      await refreshWaitingRooms([session]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to deny participant");
    } finally {
      setAdmissionBusy(null);
    }
  }, [refreshWaitingRooms]);

  if (activeSession) {
    return (
      <TelemedicineCallScreen
        session={activeSession}
        displayName={providerDisplayName}
        onEnded={() => {
          setActiveSession(null);
          loadSessions();
        }}
      />
    );
  }

  return (
    <Screen>
      <Header title="Telemedicine" />
      <ScrollView testID="telemedicine-screen" style={styles.container}>
        {admissionNotice ? <Text style={styles.noticeText}>{admissionNotice}</Text> : null}
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadSessions} />
        ) : sessions.length === 0 ? (
          <EmptyState title="No scheduled sessions" message="Telemedicine sessions will appear here" />
        ) : (
          sessions.map((session) => {
            const waiting = waitingRooms[session.id] ?? [];
            return (
              <Card key={session.id}>
                <CardBody>
                  <View testID={`session-${session.id}`} style={styles.sessionRow}>
                    <View>
                      <Badge variant="secondary">{session.status}</Badge>
                      <Text style={styles.scheduledText}>
                        {`Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`}
                      </Text>
                    </View>
                    {session.status === "SCHEDULED" || session.status === "WAITING" || session.status === "IN_PROGRESS" ? (
                      <Button
                        title="Join"
                        variant="primary"
                        onPress={() => handleJoin(session)}
                        testID={`join-session-${session.id}`}
                      />
                    ) : null}
                  </View>
                  {WAITING_ROOM_STATUSES.has(session.status) ? (
                    <View style={styles.waitingRoomSection} testID="telemedicine-waiting-room">
                      <Text style={styles.waitingRoomTitle}>Waiting room</Text>
                      {waiting.length === 0 ? (
                        <Text style={styles.waitingRoomEmpty}>No one is waiting to be admitted.</Text>
                      ) : (
                        waiting.map((participant) => (
                          <View
                            key={participant.identity}
                            style={styles.waitingRow}
                            testID={`telemedicine-waiting-${participant.identity}`}
                          >
                            <View style={styles.waitingInfo}>
                              <Text style={styles.waitingName}>
                                {participant.displayName ?? participant.identity}
                              </Text>
                              {participant.waitingSince ? (
                                <Text style={styles.waitingSince}>
                                  {`Waiting since ${new Date(participant.waitingSince).toLocaleTimeString()}`}
                                </Text>
                              ) : null}
                            </View>
                            <View style={styles.waitingActions}>
                              <Button
                                title="Admit"
                                variant="primary"
                                size="sm"
                                disabled={admissionBusy !== null}
                                onPress={() => handleAdmit(session, participant)}
                                testID="telemedicine-admit-button"
                              />
                              <Button
                                title="Deny"
                                variant="outline"
                                size="sm"
                                disabled={admissionBusy !== null}
                                onPress={() => handleDeny(session, participant)}
                                testID="telemedicine-deny-button"
                              />
                            </View>
                          </View>
                        ))
                      )}
                    </View>
                  ) : null}
                </CardBody>
              </Card>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
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
  noticeText: {
    fontSize: 13,
    color: "#374151",
    marginBottom: 8,
  },
  waitingRoomSection: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: "#E5E7EB",
    gap: 8,
  },
  waitingRoomTitle: {
    fontSize: 13,
    fontWeight: "700",
    color: "#111827",
  },
  waitingRoomEmpty: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  waitingRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  waitingInfo: {
    flex: 1,
  },
  waitingName: {
    fontSize: 14,
    fontWeight: "600",
    color: "#111827",
  },
  waitingSince: {
    fontSize: 12,
    color: "#6B7280",
  },
  waitingActions: {
    flexDirection: "row",
    gap: 8,
  },
});
