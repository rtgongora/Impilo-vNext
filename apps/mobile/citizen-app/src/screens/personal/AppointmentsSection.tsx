import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, Alert, TextInput } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  fetchAppointments,
  cancelAppointment,
  checkInAppointment,
  fetchAppointmentMessages,
  sendAppointmentMessage,
  type AppointmentMessage,
} from "../../services/appointmentService";
import type { Appointment } from "../../types";

const STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  SCHEDULED: "default",
  CONFIRMED: "secondary",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
  IN_PROGRESS: "default",
  CHECKED_IN: "default",
  NO_SHOW: "destructive",
};

export function AppointmentsSection() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [messagesById, setMessagesById] = useState<Record<string, AppointmentMessage[]>>({});
  const [draftById, setDraftById] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchAppointments({ size: 50 });
      setAppointments(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleCancel = useCallback(async (id: string) => {
    try {
      await cancelAppointment(id);
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [load]);

  const toggleMessages = useCallback(async (id: string) => {
    if (expandedId === id) {
      setExpandedId(null);
      return;
    }
    setExpandedId(id);
    try {
      const messages = await fetchAppointmentMessages(id);
      setMessagesById((prev) => ({ ...prev, [id]: messages }));
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [expandedId]);

  const handleSendMessage = useCallback(async (id: string) => {
    const draft = draftById[id]?.trim();
    if (!draft) return;
    try {
      await sendAppointmentMessage(id, draft);
      setDraftById((prev) => ({ ...prev, [id]: "" }));
      const messages = await fetchAppointmentMessages(id);
      setMessagesById((prev) => ({ ...prev, [id]: messages }));
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [draftById]);

  const handleCheckIn = useCallback(async (id: string) => {
    try {
      const result = await checkInAppointment(id);
      const encounterId = result.meta.encounter_id ?? result.data.encounter_id;
      const transactionId = result.meta.core_transaction_id;
      Alert.alert(
        "Checked in",
        encounterId
          ? `Encounter ${encounterId} is ready.${transactionId ? ` Transaction ${transactionId}.` : ""}`
          : "Your appointment is checked in. Proceed to the facility queue.",
      );
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  return (
    <View testID="appointments-section" style={styles.container}>
      <View style={styles.headerRow}>
        <View>
          <Text style={styles.sectionLabel}>MY APPOINTMENTS</Text>
          <Text style={styles.sectionSubtitle}>Confirmed scheduled care events only</Text>
        </View>
      </View>

      {appointments.length === 0 ? (
        <View style={styles.emptyContainer}>
          <View style={styles.emptyIconCircle}>
            <Ionicons name="calendar-outline" size={32} color={colors.gray[300]} />
          </View>
          <Text style={styles.emptyTitle}>No appointments yet</Text>
          <Text style={styles.emptyMessage}>
            Book a service under My Bookings. After your request is approved, the confirmed appointment
            will appear here.
          </Text>
        </View>
      ) : (
        appointments.map((appt) => {
          const dateObj = new Date(appt.scheduledAt);
          const day = dateObj.getDate();
          const month = dateObj.toLocaleString("default", { month: "short" }).toUpperCase();
          const time = dateObj.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
          return (
            <View key={appt.id} style={styles.appointmentCard}>
              <View testID={`appointment-${appt.id}`} style={styles.appointmentRow}>
                <View style={styles.dateBox}>
                  <Text style={styles.dateDay}>{day}</Text>
                  <Text style={styles.dateMonth}>{month}</Text>
                </View>
                <View style={styles.appointmentMeta}>
                  <View style={styles.typeRow}>
                    <Text style={styles.appointmentType}>{appt.appointmentType}</Text>
                    <Badge variant={STATUS_COLORS[appt.status] ?? "outline"}>
                      {appt.status}
                    </Badge>
                  </View>
                  <View style={styles.metaRow}>
                    <Ionicons name="location-outline" size={13} color={colors.gray[400]} />
                    <Text style={styles.metaText}>{appt.facilityName}</Text>
                  </View>
                  {appt.providerName ? (
                    <View style={styles.metaRow}>
                      <Ionicons name="person-outline" size={13} color={colors.gray[400]} />
                      <Text style={styles.metaText}>{`Dr. ${appt.providerName}`}</Text>
                    </View>
                  ) : null}
                  <View style={styles.metaRow}>
                    <Ionicons name="time-outline" size={13} color={colors.gray[400]} />
                    <Text style={styles.metaText}>{time}</Text>
                  </View>
                  {appt.reason ? (
                    <Text style={styles.reasonText}>{appt.reason}</Text>
                  ) : null}
                  {appt.status === "SCHEDULED" || appt.status === "CONFIRMED" ? (
                    <View style={styles.actionRow}>
                      <Button
                        title="Messages"
                        variant="outline"
                        size="sm"
                        onPress={() => toggleMessages(appt.id)}
                        testID={`messages-appointment-${appt.id}`}
                      />
                      <Button
                        title="Check in"
                        variant="default"
                        size="sm"
                        onPress={() => handleCheckIn(appt.id)}
                        testID={`check-in-appointment-${appt.id}`}
                      />
                      <Button
                        title="Cancel Appointment"
                        variant="ghost"
                        size="sm"
                        onPress={() => handleCancel(appt.id)}
                        testID={`cancel-appointment-${appt.id}`}
                      />
                    </View>
                  ) : null}
                  {expandedId === appt.id ? (
                    <View style={styles.messagePanel}>
                      {(messagesById[appt.id] ?? []).map((msg) => (
                        <Text key={msg.id} style={styles.messageLine}>
                          {msg.direction === "provider_to_citizen" ? "Provider" : "You"}: {msg.message}
                        </Text>
                      ))}
                      <View style={styles.messageComposer}>
                        <TextInput
                          style={styles.messageInput}
                          value={draftById[appt.id] ?? ""}
                          onChangeText={(text) => setDraftById((prev) => ({ ...prev, [appt.id]: text }))}
                          placeholder="Message your care team…"
                        />
                        <Button
                          title="Send"
                          variant="default"
                          size="sm"
                          onPress={() => handleSendMessage(appt.id)}
                        />
                      </View>
                    </View>
                  ) : null}
                </View>
              </View>
            </View>
          );
        })
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 4,
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[500],
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  sectionSubtitle: {
    fontSize: 12,
    color: colors.gray[400],
    marginTop: 2,
  },
  emptyContainer: {
    alignItems: "center",
    paddingVertical: 48,
    gap: 12,
  },
  emptyIconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: colors.gray[100],
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 4,
  },
  emptyTitle: {
    fontSize: 17,
    fontWeight: "600",
    color: colors.gray[700],
  },
  emptyMessage: {
    fontSize: 14,
    color: colors.gray[400],
    textAlign: "center",
    paddingHorizontal: 24,
  },
  appointmentCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  appointmentRow: {
    flexDirection: "row",
    gap: 14,
    alignItems: "flex-start",
  },
  dateBox: {
    width: 52,
    height: 60,
    borderRadius: 12,
    backgroundColor: "#F0FDF4",
    alignItems: "center",
    justifyContent: "center",
    gap: 2,
    flexShrink: 0,
  },
  dateDay: {
    fontSize: 22,
    fontWeight: "700",
    color: "#059669",
    lineHeight: 26,
  },
  dateMonth: {
    fontSize: 10,
    fontWeight: "700",
    color: "#059669",
    letterSpacing: 0.5,
  },
  appointmentMeta: {
    flex: 1,
    gap: 4,
  },
  typeRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 4,
  },
  appointmentType: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.gray[900],
    flex: 1,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  metaText: {
    fontSize: 13,
    color: colors.gray[500],
  },
  reasonText: {
    fontSize: 12,
    color: colors.gray[400],
    marginTop: 2,
  },
  actionRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8,
  },
  messagePanel: {
    marginTop: 10,
    padding: 10,
    borderRadius: 10,
    backgroundColor: colors.gray[50],
    gap: 6,
  },
  messageLine: {
    fontSize: 12,
    color: colors.gray[700],
  },
  messageComposer: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginTop: 4,
  },
  messageInput: {
    flex: 1,
    borderWidth: 1,
    borderColor: colors.gray[200],
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    fontSize: 13,
    backgroundColor: "#FFFFFF",
  },
});
