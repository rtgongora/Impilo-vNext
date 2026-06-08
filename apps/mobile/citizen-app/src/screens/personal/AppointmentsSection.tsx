import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Button,
  Badge,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchAppointments,
  cancelAppointment,
  checkInAppointment,
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
            <Ionicons name="calendar-outline" size={32} color="#D1D5DB" />
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
                    <Ionicons name="location-outline" size={13} color="#9CA3AF" />
                    <Text style={styles.metaText}>{appt.facilityName}</Text>
                  </View>
                  {appt.providerName ? (
                    <View style={styles.metaRow}>
                      <Ionicons name="person-outline" size={13} color="#9CA3AF" />
                      <Text style={styles.metaText}>{`Dr. ${appt.providerName}`}</Text>
                    </View>
                  ) : null}
                  <View style={styles.metaRow}>
                    <Ionicons name="time-outline" size={13} color="#9CA3AF" />
                    <Text style={styles.metaText}>{time}</Text>
                  </View>
                  {appt.reason ? (
                    <Text style={styles.reasonText}>{appt.reason}</Text>
                  ) : null}
                  {appt.status === "SCHEDULED" || appt.status === "CONFIRMED" ? (
                    <View style={styles.actionRow}>
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
    color: "#6B7280",
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  sectionSubtitle: {
    fontSize: 12,
    color: "#9CA3AF",
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
    backgroundColor: "#F3F4F6",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 4,
  },
  emptyTitle: {
    fontSize: 17,
    fontWeight: "600",
    color: "#374151",
  },
  emptyMessage: {
    fontSize: 14,
    color: "#9CA3AF",
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
    color: "#111827",
    flex: 1,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  metaText: {
    fontSize: 13,
    color: "#6B7280",
  },
  reasonText: {
    fontSize: 12,
    color: "#9CA3AF",
    marginTop: 2,
  },
  actionRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8,
  },
});
