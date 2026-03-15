/**
 * AppointmentsSection — View, request, and manage appointments.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
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
        <Text style={styles.heading}>Appointments</Text>
        <Button
          title={showBooking ? "Cancel" : "Book New"}
          variant={showBooking ? "ghost" : "primary"}
          size="sm"
          onPress={() => setShowBooking(!showBooking)}
          testID="toggle-booking"
        />
      </View>

      {showBooking ? (
        <Card>
          <CardHeader title="Request Appointment" />
          <CardBody>
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
          </CardBody>
        </Card>
      ) : null}

      {appointments.length === 0 ? (
        <EmptyState
          title="No appointments"
          message="Book your first appointment using the button above"
        />
      ) : (
        appointments.map((appt) => (
          <Card key={appt.id}>
            <CardBody>
              <View testID={`appointment-${appt.id}`} style={styles.appointmentRow}>
                <View>
                  <View style={styles.badgeRow}>
                    <Text style={styles.boldText}>{appt.appointmentType}</Text>
                    <Badge variant={STATUS_COLORS[appt.status] ?? "outline"}>
                      {appt.status}
                    </Badge>
                  </View>
                  <Text style={styles.facilityText}>{appt.facilityName}</Text>
                  {appt.providerName ? (
                    <Text style={styles.secondaryText}>{`Dr. ${appt.providerName}`}</Text>
                  ) : null}
                  <Text style={styles.secondaryText}>
                    {new Date(appt.scheduledAt).toLocaleString()}
                  </Text>
                  {appt.reason ? (
                    <Text style={styles.tertiaryText}>{appt.reason}</Text>
                  ) : null}
                </View>
                {appt.status === "SCHEDULED" || appt.status === "CONFIRMED" ? (
                  <Button
                    title="Cancel"
                    variant="ghost"
                    size="sm"
                    onPress={() => handleCancel(appt.id)}
                    testID={`cancel-appointment-${appt.id}`}
                  />
                ) : null}
              </View>
            </CardBody>
          </Card>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
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
  appointmentRow: {
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
  facilityText: {
    fontSize: 14,
    color: "#374151",
    marginVertical: 2,
  },
  secondaryText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  tertiaryText: {
    fontSize: 13,
    color: "#9CA3AF",
    marginVertical: 2,
  },
});
