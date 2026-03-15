/**
 * HomeScreen — Citizen dashboard with quick-access cards and upcoming items.
 */

import React, { useState, useEffect } from "react";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  Avatar,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../stores/appStore";
import { fetchAppointments } from "../services/appointmentService";
import { fetchPrescriptions } from "../services/prescriptionService";
import { fetchLabResults } from "../services/labResultService";
import type { Appointment, Prescription, LabResult } from "../types";

export function HomeScreen() {
  const { profile, setActiveTab, unreadNotifications } = useAppStore();
  const [upcomingAppointments, setUpcomingAppointments] = useState<Appointment[]>([]);
  const [activePrescriptions, setActivePrescriptions] = useState<Prescription[]>([]);
  const [pendingResults, setPendingResults] = useState<LabResult[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      fetchAppointments({ status: "SCHEDULED", size: 3 }).catch(() => ({ items: [] as Appointment[], totalElements: 0, hasNext: false })),
      fetchPrescriptions({ status: "ACTIVE", size: 3 }).catch(() => ({ items: [] as Prescription[], totalElements: 0, hasNext: false })),
      fetchLabResults({ status: "PROCESSING", size: 3 }).catch(() => ({ items: [] as LabResult[], totalElements: 0, hasNext: false })),
    ]).then(([appts, rxs, labs]) => {
      setUpcomingAppointments(appts.items);
      setActivePrescriptions(rxs.items);
      setPendingResults(labs.items);
      setIsLoading(false);
    });
  }, []);

  const greeting = profile
    ? `Hello, ${profile.givenName}`
    : "Welcome";

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, {
      title: greeting,
      rightElement: React.createElement(
        "div",
        { style: { position: "relative", cursor: "pointer" }, onClick: () => setActiveTab("messaging") },
        React.createElement(Badge, {
          variant: unreadNotifications > 0 ? "destructive" : "secondary",
          children: String(unreadNotifications),
        })
      ),
    }),
    React.createElement(
      "div",
      { "data-testid": "home-screen", style: { padding: "16px", display: "flex", flexDirection: "column", gap: "16px" } },

      // Profile summary
      profile
        ? React.createElement(
            Card,
            null,
            React.createElement(
              CardBody,
              null,
              React.createElement(
                "div",
                { style: { display: "flex", alignItems: "center", gap: "12px" } },
                React.createElement(Avatar, {
                  name: `${profile.givenName} ${profile.familyName}`,
                  size: "lg",
                }),
                React.createElement(
                  "div",
                  null,
                  React.createElement("strong", { style: { fontSize: "16px" } }, `${profile.givenName} ${profile.familyName}`),
                  React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: 0 } }, profile.facilityName ?? "Primary facility not set")
                )
              )
            )
          )
        : null,

      // Quick actions
      React.createElement(
        "div",
        { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" } },
        React.createElement(Button, {
          title: "Book Appointment",
          variant: "primary",
          onPress: () => setActiveTab("personal"),
          testID: "quick-book-appointment",
        }),
        React.createElement(Button, {
          title: "Find Services",
          variant: "secondary",
          onPress: () => setActiveTab("marketplace"),
          testID: "quick-find-services",
        }),
        React.createElement(Button, {
          title: "Messages",
          variant: "secondary",
          onPress: () => setActiveTab("messaging"),
          testID: "quick-messages",
        }),
        React.createElement(Button, {
          title: "Telehealth",
          variant: "secondary",
          onPress: () => setActiveTab("telehealth"),
          testID: "quick-telehealth",
        })
      ),

      // Upcoming appointments
      isLoading
        ? React.createElement(LoadingSpinner, { size: "sm" })
        : React.createElement(
            React.Fragment,
            null,
            upcomingAppointments.length > 0
              ? React.createElement(
                  Card,
                  null,
                  React.createElement(CardHeader, { title: "Upcoming Appointments" }),
                  React.createElement(
                    CardBody,
                    null,
                    upcomingAppointments.map((appt) =>
                      React.createElement(
                        "div",
                        {
                          key: appt.id,
                          "data-testid": `home-appointment-${appt.id}`,
                          style: { padding: "8px 0", borderBottom: "1px solid #F3F4F6" },
                        },
                        React.createElement("strong", { style: { fontSize: "14px" } }, appt.appointmentType),
                        React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                          `${appt.facilityName} \u2022 ${new Date(appt.scheduledAt).toLocaleDateString()}`
                        )
                      )
                    )
                  )
                )
              : null,

            // Active prescriptions
            activePrescriptions.length > 0
              ? React.createElement(
                  Card,
                  null,
                  React.createElement(CardHeader, { title: "Active Prescriptions" }),
                  React.createElement(
                    CardBody,
                    null,
                    activePrescriptions.map((rx) =>
                      React.createElement(
                        "div",
                        {
                          key: rx.id,
                          "data-testid": `home-rx-${rx.id}`,
                          style: { padding: "8px 0", borderBottom: "1px solid #F3F4F6" },
                        },
                        React.createElement("strong", { style: { fontSize: "14px" } }, rx.medicationName),
                        React.createElement("p", { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                          `${rx.dosage} \u2022 ${rx.frequency}`
                        )
                      )
                    )
                  )
                )
              : null,

            // Pending results
            pendingResults.length > 0
              ? React.createElement(
                  Card,
                  null,
                  React.createElement(CardHeader, { title: "Pending Results" }),
                  React.createElement(
                    CardBody,
                    null,
                    pendingResults.map((lab) =>
                      React.createElement(
                        "div",
                        {
                          key: lab.id,
                          "data-testid": `home-lab-${lab.id}`,
                          style: { padding: "8px 0", borderBottom: "1px solid #F3F4F6" },
                        },
                        React.createElement("strong", { style: { fontSize: "14px" } }, lab.testName),
                        React.createElement(
                          "p",
                          { style: { fontSize: "13px", color: "#6B7280", margin: "2px 0" } },
                          lab.facilityName
                        )
                      )
                    )
                  )
                )
              : null
          )
    )
  );
}
