/**
 * TelehealthSessionScreen — Active teleconsult session with join/end flow.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  TextField,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { joinSession, endSession, fetchSession } from "../../services/telehealthService";
import { useChannel } from "@impilo/mobile-messaging";
import type { TelehealthSession } from "../../types";

interface TelehealthSessionScreenProps {
  session: TelehealthSession;
  onBack: () => void;
}

export function TelehealthSessionScreen({ session: initialSession, onBack }: TelehealthSessionScreenProps) {
  const [session, setSession] = useState<TelehealthSession>(initialSession);
  const [sessionToken, setSessionToken] = useState<string | null>(null);
  const [sessionChannel, setSessionChannel] = useState<string | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [isEnding, setIsEnding] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [endNotes, setEndNotes] = useState("");
  const [showEndForm, setShowEndForm] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [supportNotice, setSupportNotice] = useState<string | null>(null);

  // Real-time channel for session events
  const channel = useChannel(sessionChannel ?? `telehealth-${session.id}`, {
    onMessage: (event) => {
      if (event.type === "telemedicine.session.provider_late") {
        setSupportNotice("Your provider is running late. Please stay in the waiting room.");
      } else if (event.type === "telemedicine.session.video_failed") {
        setSupportNotice("Video connection failed. Open Impilo audio fallback or request support.");
      } else if (event.type === "telemedicine.session.audio_failed") {
        setSupportNotice("Audio connection failed. Use secure chat or request helpdesk support.");
      } else if (event.type === "telemedicine.session.completed" || event.type === "session_ended") {
        setSupportNotice("Your teleconsultation has ended. Follow-up steps are available in Impilo.");
        setSession((prev) => ({ ...prev, status: "COMPLETED" }));
        setSessionToken(null);
      }
    },
  });

  // Elapsed timer when in session
  useEffect(() => {
    if (session.status === "IN_PROGRESS" && sessionToken) {
      const interval = setInterval(() => {
        setElapsed((prev) => prev + 1);
      }, 1000);
      return () => clearInterval(interval);
    }
  }, [session.status, sessionToken]);

  const handleJoin = useCallback(async () => {
    setIsJoining(true);
    setError(null);
    try {
      const result = await joinSession(session.id);
      setSession(result.session);
      setSessionToken(result.token);
      setSessionChannel(result.channel);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsJoining(false);
    }
  }, [session.id]);

  const handleEnd = useCallback(async () => {
    setIsEnding(true);
    setError(null);
    try {
      await endSession(session.id, endNotes || undefined);
      const updated = await fetchSession(session.id);
      setSession(updated);
      setSessionToken(null);
      setShowEndForm(false);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsEnding(false);
    }
  }, [session.id, endNotes]);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  return (
    <Screen>
      <Header
        title="Teleconsultation"
        leftElement={
          <Button
            title={"\u2190 Back"}
            variant="ghost"
            size="sm"
            onPress={onBack}
            testID="session-back"
          />
        }
      />
      <ScrollView testID="telehealth-session-screen" style={styles.scrollView} contentContainerStyle={styles.container}>
        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={() => setError(null)} />
        ) : null}

        {/* Session info */}
        <Card>
          <CardHeader title="Session Details" />
          <CardBody>
            <View style={styles.sessionDetails}>
              <View style={styles.badgeRow}>
                <Text style={styles.boldText}>{session.sessionType}</Text>
                <Badge
                  variant={session.status === "IN_PROGRESS" ? "default" : session.status === "COMPLETED" ? "secondary" : "outline"}
                >
                  {session.status}
                </Badge>
              </View>
              <Text style={styles.infoText}>{`Provider: Dr. ${session.providerName}`}</Text>
              <Text style={styles.infoTextSecondary}>
                {`Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`}
              </Text>
              {session.status === "IN_PROGRESS" && sessionToken ? (
                <Text style={styles.durationText}>
                  {`Duration: ${formatTime(elapsed)}`}
                </Text>
              ) : null}
              {channel.isConnected ? (
                <Text style={styles.connectedText}>{"\u25CF Connected to session channel"}</Text>
              ) : null}
            </View>
          </CardBody>
        </Card>
        {supportNotice ? (
          <Card>
            <CardHeader title="Telemedicine Support" />
            <CardBody>
              <Text style={styles.infoText}>{supportNotice}</Text>
            </CardBody>
          </Card>
        ) : null}

        {/* Active session area */}
        {sessionToken ? (
          <Card>
            <CardBody>
              <View testID="active-session-area" style={styles.activeSessionArea}>
                <Text style={styles.activeSessionText}>
                  {session.sessionType === "VIDEO"
                    ? "Video session active"
                    : session.sessionType === "AUDIO"
                      ? "Audio session active"
                      : "Chat session active"}
                </Text>
              </View>

              {/* Session controls */}
              <View style={styles.controlsRow}>
                <Button
                  title="End Session"
                  variant="primary"
                  onPress={() => setShowEndForm(true)}
                  testID="end-session-btn"
                />
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* End session form */}
        {showEndForm ? (
          <Card>
            <CardHeader title="End Session" />
            <CardBody>
              <View style={styles.formContainer}>
                <TextField
                  label="Notes (optional)"
                  value={endNotes}
                  onChange={setEndNotes}
                  placeholder="Any post-session notes"
                  testID="end-session-notes"
                />
                <View style={styles.buttonRow}>
                  <Button
                    title={isEnding ? "Ending..." : "Confirm End"}
                    variant="primary"
                    onPress={handleEnd}
                    disabled={isEnding}
                    testID="confirm-end-session"
                  />
                  <Button
                    title="Continue"
                    variant="ghost"
                    onPress={() => setShowEndForm(false)}
                  />
                </View>
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* Pre-session: Join button */}
        {!sessionToken && (session.status === "SCHEDULED" || session.status === "IN_PROGRESS") ? (
          <View style={styles.joinContainer}>
            <Button
              title={isJoining ? "Joining..." : "Join Session"}
              variant="primary"
              size="lg"
              onPress={handleJoin}
              disabled={isJoining}
              testID="join-session"
            />
          </View>
        ) : null}

        {/* Post-session summary */}
        {session.status === "COMPLETED" ? (
          <Card>
            <CardHeader title="Session Summary" />
            <CardBody>
              <Text style={styles.summaryText}>
                {`Started: ${session.startedAt ? new Date(session.startedAt).toLocaleString() : "N/A"}`}
              </Text>
              <Text style={styles.summaryText}>
                {`Ended: ${session.endedAt ? new Date(session.endedAt).toLocaleString() : "N/A"}`}
              </Text>
              {session.notes ? (
                <Text style={styles.summaryText}>{`Notes: ${session.notes}`}</Text>
              ) : null}
            </CardBody>
          </Card>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 16,
  },
  sessionDetails: {
    gap: 8,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
  },
  boldText: {
    fontWeight: "700",
  },
  infoText: {
    fontSize: 14,
  },
  infoTextSecondary: {
    fontSize: 14,
    color: "#6B7280",
  },
  durationText: {
    fontSize: 20,
    fontWeight: "700",
    color: "#2563EB",
    marginVertical: 8,
  },
  connectedText: {
    fontSize: 12,
    color: "#009739",
  },
  activeSessionArea: {
    backgroundColor: "#111827",
    borderRadius: 12,
    height: 300,
    justifyContent: "center",
    alignItems: "center",
    marginBottom: 16,
  },
  activeSessionText: {
    color: "white",
    fontSize: 16,
  },
  controlsRow: {
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
  },
  formContainer: {
    gap: 12,
  },
  buttonRow: {
    flexDirection: "row",
    gap: 8,
  },
  joinContainer: {
    alignItems: "center",
  },
  summaryText: {
    fontSize: 14,
    marginVertical: 4,
  },
});
