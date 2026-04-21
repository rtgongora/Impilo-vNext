import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
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
import { fetchAppointments, requestAppointment, cancelAppointment } from "../../services/appointmentService";
import type { Appointment } from "../../types";

const STATUS_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  SCHEDULED: "default",
  CONFIRMED: "secondary",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
  IN_PROGRESS: "default",
};

export function AppointmentsSection() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [showBooking, setShowBooking] = useState(false);
  const [facilityId, setFacilityId] = useState("");
  const [appointmentType, setAppointmentType] = useState("GENERAL");
  const [preferredDate, setPreferredDate] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);

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

  const handleBook = useCallback(async () => {
    setSubmitting(true);
    try {
      await requestAppointment({ facilityId, appointmentType, preferredDate, reason });
      setShowBooking(false);
      setFacilityId("");
      setReason("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setSubmitting(false);
    }
  }, [facilityId, appointmentType, preferredDate, reason, load]);

  const handleCancel = useCallback(async (id: string) => {
    try {
      await cancelAppointment(id);
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
        <Text style={styles.sectionLabel}>MY APPOINTMENTS</Text>
        <Button
          title={showBooking ? "Cancel" : "Book New"}
          variant={showBooking ? "ghost" : "primary"}
          size="sm"
          onPress={() => setShowBooking(!showBooking)}
          testID="toggle-booking"
        />
      </View>

      {showBooking ? (
        <View style={styles.bookingCard}>
          <Text style={styles.bookingTitle}>Request Appointment</Text>
          <View style={styles.formContainer}>
            <TextField
              label="Facility ID"
              value={facilityId}
              onChange={setFacilityId}
              placeholder="Enter facility ID"
              testID="booking-facility"
            />
            <Select
              label="Type"
              value={appointmentType}
              onChange={setAppointmentType}
              options={[
                { label: "General Consultation", value: "GENERAL" },
                { label: "Follow-Up", value: "FOLLOW_UP" },
                { label: "Specialist Referral", value: "SPECIALIST" },
                { label: "Lab Work", value: "LAB_WORK" },
                { label: "Vaccination", value: "VACCINATION" },
              ]}
              testID="booking-type"
            />
            <TextField
              label="Preferred Date"
              value={preferredDate}
              onChange={setPreferredDate}
              placeholder="YYYY-MM-DD"
              testID="booking-date"
            />
            <TextField
              label="Reason (optional)"
              value={reason}
              onChange={setReason}
              placeholder="Brief description"
              testID="booking-reason"
            />
            <Button
              title={submitting ? "Submitting..." : "Submit Request"}
              variant="primary"
              onPress={handleBook}
              disabled={submitting || !facilityId || !preferredDate}
              testID="submit-booking"
            />
          </View>
        </View>
      ) : null}

      {appointments.length === 0 ? (
        <View style={styles.emptyContainer}>
          <View style={styles.emptyIconCircle}>
            <Ionicons name="calendar-outline" size={32} color="#D1D5DB" />
          </View>
          <Text style={styles.emptyTitle}>No appointments</Text>
          <Text style={styles.emptyMessage}>Book your first appointment using the button above</Text>
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
                    <Button
                      title="Cancel Appointment"
                      variant="ghost"
                      size="sm"
                      onPress={() => handleCancel(appt.id)}
                      testID={`cancel-appointment-${appt.id}`}
                    />
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
  bookingCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  bookingTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 12,
  },
  formContainer: {
    gap: 12,
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
});
