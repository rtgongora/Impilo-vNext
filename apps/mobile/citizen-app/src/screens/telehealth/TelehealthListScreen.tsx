/**
 * TelehealthListScreen — List teleconsult sessions and request new ones.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
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

const SESSION_ICON_MAP: Record<string, React.ComponentProps<typeof Ionicons>["name"]> = {
  VIDEO: "videocam",
  AUDIO: "call",
  CHAT: "chatbubble",
};

const SESSION_ICON_COLOR: Record<string, string> = {
  VIDEO: "#1E40AF",
  AUDIO: "#059669",
  CHAT: "#7C3AED",
};

const SESSION_ICON_BG: Record<string, string> = {
  VIDEO: "#EFF6FF",
  AUDIO: "#D1FAE5",
  CHAT: "#F5F3FF",
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
          <Text style={styles.sectionLabel}>MY TELECONSULTATIONS</Text>
          <TouchableOpacity
            onPress={() => setShowRequest(!showRequest)}
            style={[styles.requestBtn, showRequest && styles.requestBtnCancel]}
            testID="toggle-telehealth-request"
          >
            {!showRequest && <Ionicons name="videocam-outline" size={16} color="#FFFFFF" style={styles.btnIcon} />}
            <Text style={[styles.requestBtnText, showRequest && styles.requestBtnTextCancel]}>
              {showRequest ? "Cancel" : "Request"}
            </Text>
          </TouchableOpacity>
        </View>

        {showRequest ? (
          <View style={styles.formCard}>
            <Text style={styles.formTitle}>Request Teleconsultation</Text>
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
              <TouchableOpacity
                onPress={handleRequest}
                disabled={submitting || !reason}
                style={[styles.submitBtn, (submitting || !reason) && styles.submitBtnDisabled]}
                testID="submit-telehealth-request"
              >
                <Text style={styles.submitBtnText}>{submitting ? "Submitting..." : "Submit Request"}</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : null}

        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={load} />
        ) : null}

        {isLoading ? (
          <View style={styles.centered}>
            <LoadingSpinner size="md" />
          </View>
        ) : sessions.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Ionicons name="videocam-outline" size={48} color="#D1D5DB" />
            <Text style={styles.emptyTitle}>No teleconsultations</Text>
            <Text style={styles.emptyMessage}>Request your first teleconsultation using the button above</Text>
          </View>
        ) : (
          sessions.map((session) => (
            <View key={session.id} style={styles.sessionCard} testID={`telehealth-session-${session.id}`}>
              <View
                style={[
                  styles.sessionIconCircle,
                  { backgroundColor: SESSION_ICON_BG[session.sessionType] ?? "#F3F4F6" },
                ]}
              >
                <Ionicons
                  name={SESSION_ICON_MAP[session.sessionType] ?? "videocam"}
                  size={22}
                  color={SESSION_ICON_COLOR[session.sessionType] ?? "#6B7280"}
                />
              </View>
              <View style={styles.sessionInfo}>
                <Text style={styles.sessionType}>{session.sessionType}</Text>
                <View style={styles.metaRow}>
                  <Ionicons name="person-outline" size={12} color="#9CA3AF" />
                  <Text style={styles.providerText}>{`Dr. ${session.providerName}`}</Text>
                </View>
                <View style={styles.metaRow}>
                  <Ionicons name="time-outline" size={12} color="#9CA3AF" />
                  <Text style={styles.scheduledText}>
                    {new Date(session.scheduledAt).toLocaleString()}
                  </Text>
                </View>
                {session.notes ? (
                  <Text style={styles.notesText}>{session.notes}</Text>
                ) : null}
              </View>
              <View style={styles.sessionActions}>
                <Badge variant={STATUS_COLORS[session.status] ?? "outline"}>
                  {session.status}
                </Badge>
                {session.status === "SCHEDULED" || session.status === "IN_PROGRESS" ? (
                  <TouchableOpacity
                    style={styles.joinBtn}
                    onPress={() => setActiveSession(session)}
                    testID={`join-session-${session.id}`}
                  >
                    <Ionicons name="enter-outline" size={14} color="#FFFFFF" />
                    <Text style={styles.joinBtnText}>
                      {session.status === "IN_PROGRESS" ? "Rejoin" : "Join"}
                    </Text>
                  </TouchableOpacity>
                ) : null}
              </View>
            </View>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  container: {
    padding: 16,
    gap: 12,
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 4,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: "700",
    color: "#9CA3AF",
    letterSpacing: 0.8,
  },
  requestBtn: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#059669",
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    gap: 5,
  },
  requestBtnCancel: {
    backgroundColor: "#F3F4F6",
  },
  requestBtnText: {
    fontSize: 13,
    fontWeight: "600",
    color: "#FFFFFF",
  },
  requestBtnTextCancel: {
    color: "#6B7280",
  },
  btnIcon: {
    marginRight: 1,
  },
  formCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 3,
  },
  formTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 14,
  },
  formContainer: {
    gap: 12,
  },
  submitBtn: {
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#059669",
    paddingVertical: 12,
    borderRadius: 12,
  },
  submitBtnDisabled: {
    opacity: 0.5,
  },
  submitBtnText: {
    fontSize: 15,
    fontWeight: "600",
    color: "#FFFFFF",
  },
  centered: {
    alignItems: "center",
    paddingVertical: 40,
  },
  emptyContainer: {
    alignItems: "center",
    paddingVertical: 56,
    gap: 10,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#374151",
  },
  emptyMessage: {
    fontSize: 14,
    color: "#9CA3AF",
    textAlign: "center",
    paddingHorizontal: 24,
  },
  sessionCard: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  sessionIconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 2,
  },
  sessionInfo: {
    flex: 1,
    gap: 3,
  },
  sessionType: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 2,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  providerText: {
    fontSize: 13,
    color: "#374151",
  },
  scheduledText: {
    fontSize: 12,
    color: "#6B7280",
  },
  notesText: {
    fontSize: 12,
    color: "#9CA3AF",
    marginTop: 2,
  },
  sessionActions: {
    alignItems: "flex-end",
    gap: 8,
  },
  joinBtn: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#059669",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    gap: 4,
  },
  joinBtnText: {
    fontSize: 12,
    fontWeight: "600",
    color: "#FFFFFF",
  },
});
