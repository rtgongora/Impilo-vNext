/**
 * TelehealthListScreen — List teleconsult sessions and request new ones.
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
  Select,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { fetchSessions, requestTeleconsult } from "../../services/telehealthService";
import type { TelehealthSession } from "../../types";
import { TelehealthSessionScreen } from "./TelehealthSessionScreen";

const STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  REQUESTED: "outline",
  SCHEDULED: "default",
  IN_PROGRESS: "secondary",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

export function TelehealthListScreen() {
  const [sessions, setSessions] = useState<TelehealthSession[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showRequest, setShowRequest] = useState(false);
  const [reason, setReason] = useState("");
  const [preferredDate, setPreferredDate] = useState("");
  const [sessionType, setSessionType] = useState("VIDEO");
  const [providerId, setProviderId] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [activeSession, setActiveSession] = useState<TelehealthSession | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchSessions({ size: 50 });
      setSessions(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRequest = useCallback(async () => {
    setSubmitting(true);
    try {
      await requestTeleconsult({
        reason,
        preferredDate: preferredDate || undefined,
        sessionType,
        providerId: providerId || undefined,
      });
      setShowRequest(false);
      setReason("");
      setPreferredDate("");
      setProviderId("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [reason, preferredDate, sessionType, providerId, load]);

  // Active session view
  if (activeSession) {
    return (
      <TelehealthSessionScreen
        session={activeSession}
        onBack={() => {
          setActiveSession(null);
          load();
        }}
      />
    );
  }

  return (
    <Screen>
      <Header title="Telehealth" />
      <ScrollView testID="telehealth-list-screen" style={styles.scrollView} contentContainerStyle={styles.container}>
        <View style={styles.headerRow}>
          <Text style={styles.heading}>Teleconsultations</Text>
          <Button
            title={showRequest ? "Cancel" : "Request Teleconsult"}
            variant={showRequest ? "ghost" : "primary"}
            size="sm"
            onPress={() => setShowRequest(!showRequest)}
            testID="toggle-telehealth-request"
          />
        </View>

        {/* Request form */}
        {showRequest ? (
          <Card>
            <CardHeader title="Request Teleconsultation" />
            <CardBody>
              <View style={styles.formContainer}>
                <TextField
                  label="Reason for Consultation"
                  value={reason}
                  onChange={setReason}
                  placeholder="Describe your concern"
                  testID="telehealth-reason"
                />
                <Select
                  label="Session Type"
                  value={sessionType}
                  onChange={setSessionType}
                  options={[
                    { label: "Video Call", value: "VIDEO" },
                    { label: "Audio Call", value: "AUDIO" },
                    { label: "Chat", value: "CHAT" },
                  ]}
                  testID="telehealth-type"
                />
                <TextField
                  label="Preferred Date (optional)"
                  value={preferredDate}
                  onChange={setPreferredDate}
                  placeholder="YYYY-MM-DD"
                  testID="telehealth-date"
                />
                <TextField
                  label="Preferred Provider ID (optional)"
                  value={providerId}
                  onChange={setProviderId}
                  placeholder="Leave blank for next available"
                  testID="telehealth-provider"
                />
                <Button
                  title={submitting ? "Submitting..." : "Submit Request"}
                  variant="primary"
                  onPress={handleRequest}
                  disabled={submitting || !reason}
                  testID="submit-telehealth-request"
                />
              </View>
            </CardBody>
          </Card>
        ) : null}

        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={load} />
        ) : null}

        {/* Sessions list */}
        {isLoading ? (
          <LoadingSpinner size="md" />
        ) : sessions.length === 0 ? (
          <EmptyState
            title="No teleconsultations"
            message="Request your first teleconsultation using the button above"
          />
        ) : (
          sessions.map((session) => (
            <Card key={session.id}>
              <CardBody>
                <View testID={`telehealth-session-${session.id}`} style={styles.sessionRow}>
                  <View>
                    <View style={styles.badgeRow}>
                      <Text style={styles.boldText}>{session.sessionType}</Text>
                      <Badge variant={STATUS_COLORS[session.status] ?? "outline"}>
                        {session.status}
                      </Badge>
                    </View>
                    <Text style={styles.providerText}>
                      {`Dr. ${session.providerName}`}
                    </Text>
                    <Text style={styles.scheduledText}>
                      {`Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`}
                    </Text>
                    {session.notes ? (
                      <Text style={styles.notesText}>{session.notes}</Text>
                    ) : null}
                  </View>
                  {session.status === "SCHEDULED" || session.status === "IN_PROGRESS" ? (
                    <Button
                      title={session.status === "IN_PROGRESS" ? "Rejoin" : "Join"}
                      variant="primary"
                      size="sm"
                      onPress={() => setActiveSession(session)}
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
  scrollView: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 16,
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  formContainer: {
    gap: 12,
  },
  sessionRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 4,
  },
  boldText: {
    fontWeight: "700",
  },
  providerText: {
    fontSize: 14,
    color: "#374151",
    marginVertical: 2,
  },
  scheduledText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  notesText: {
    fontSize: 13,
    color: "#9CA3AF",
    marginVertical: 2,
  },
});
