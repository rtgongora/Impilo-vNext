/**
 * AppointmentsSection — View, request, and manage appointments.
 */

import React, { useState, useEffect, useCallback } from "react";
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

  if (isLoading) return React.createElement(LoadingSpinner, { size: "md" });
  if (error) return React.createElement(ErrorState, { title: "Error", message: error.message, onRetry: load });

  return React.createElement(
    "div",
    { "data-testid": "appointments-section", style: { display: "flex", flexDirection: "column", gap: "16px" } },

    React.createElement(
      "div",
      { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
      React.createElement("h3", { style: { margin: 0 } }, "Appointments"),
      React.createElement(Button, {
        title: showBooking ? "Cancel" : "Book New",
        variant: showBooking ? "ghost" : "primary",
        size: "sm",
        onPress: () => setShowBooking(!showBooking),
        testID: "toggle-booking",
      })
    ),

    showBooking
      ? React.createElement(
          Card,
          null,
          React.createElement(CardHeader, { title: "Request Appointment" }),
          React.createElement(
            CardBody,
            null,
            React.createElement(
              "div",
              { style: { display: "flex", flexDirection: "column", gap: "12px" } },
              React.createElement(TextField, {
                label: "Facility ID",
                value: facilityId,
                onChange: setFacilityId,
                placeholder: "Enter facility ID",
                testID: "booking-facility",
              }),
              React.createElement(Select, {
                label: "Type",
                value: appointmentType,
                onChange: setAppointmentType,
                options: [
                  { label: "General Consultation", value: "GENERAL" },
                  { label: "Follow-Up", value: "FOLLOW_UP" },
                  { label: "Specialist Referral", value: "SPECIALIST" },
                  { label: "Lab Work", value: "LAB_WORK" },
                  { label: "Vaccination", value: "VACCINATION" },
                ],
                testID: "booking-type",
              }),
              React.createElement(TextField, {
                label: "Preferred Date",
                value: preferredDate,
                onChange: setPreferredDate,
                placeholder: "YYYY-MM-DD",
                testID: "booking-date",
              }),
              React.createElement(TextField, {
                label: "Reason (optional)",
                value: reason,
                onChange: setReason,
                placeholder: "Brief description",
                testID: "booking-reason",
              }),
              React.createElement(Button, {
                title: submitting ? "Submitting..." : "Submit Request",
                variant: "primary",
                onPress: handleBook,
                disabled: submitting || !facilityId || !preferredDate,
                testID: "submit-booking",
              })
            )
          )
        )
      : null,

    appointments.length === 0
      ? React.createElement(EmptyState, {
          title: "No appointments",
          message: "Book your first appointment using the button above",
        })
      : appointments.map((appt) =>
          React.createElement(
            Card,
            { key: appt.id },
            React.createElement(
              CardBody,
              null,
              React.createElement(
                "div",
                {
                  "data-testid": `appointment-${appt.id}`,
                  style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" },
                },
                React.createElement(
                  "div",
                  null,
                  React.createElement(
                    "div",
                    { style: { display: "flex", gap: "8px", alignItems: "center", marginBottom: "4px" } },
                    React.createElement("strong", null, appt.appointmentType),
                    React.createElement(Badge, {
                      variant: STATUS_COLORS[appt.status] ?? "outline",
                      children: appt.status,
                    })
                  ),
                  React.createElement("p", { style: { fontSize: "14px", color: "#374151", margin: "2px 0" } }, appt.facilityName),
                  appt.providerName
                    ? React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } }, `Dr. ${appt.providerName}`)
                    : null,
                  React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                    new Date(appt.scheduledAt).toLocaleString()
                  ),
                  appt.reason
                    ? React.createElement("p", { style: { fontSize: "13px", color: "#9CA3AF", margin: "2px 0" } }, appt.reason)
                    : null
                ),
                appt.status === "SCHEDULED" || appt.status === "CONFIRMED"
                  ? React.createElement(Button, {
                      title: "Cancel",
                      variant: "ghost",
                      size: "sm",
                      onPress: () => handleCancel(appt.id),
                      testID: `cancel-appointment-${appt.id}`,
                    })
                  : null
              )
            )
          )
        )
  );
}
