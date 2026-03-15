/**
 * HomeScreen — Citizen dashboard with quick-access cards and upcoming items.
 */

import React, { useState, useEffect } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
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

  return (
    <Screen>
      <Header
        title={greeting}
        rightElement={
          <Pressable onPress={() => setActiveTab("messaging")}>
            <Badge
              variant={unreadNotifications > 0 ? "destructive" : "secondary"}
            >
              {String(unreadNotifications)}
            </Badge>
          </Pressable>
        }
      />
      <View testID="home-screen" style={styles.content}>
        {/* Profile summary */}
        {profile ? (
          <Card>
            <CardBody>
              <View style={styles.profileRow}>
                <Avatar
                  name={`${profile.givenName} ${profile.familyName}`}
                  size="lg"
                />
                <View>
                  <Text style={styles.profileName}>
                    {`${profile.givenName} ${profile.familyName}`}
                  </Text>
                  <Text style={styles.profileFacility}>
                    {profile.facilityName ?? "Primary facility not set"}
                  </Text>
                </View>
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* Quick actions */}
        <View style={styles.quickActions}>
          <Button
            title="Book Appointment"
            variant="primary"
            onPress={() => setActiveTab("personal")}
            testID="quick-book-appointment"
          />
          <Button
            title="Find Services"
            variant="secondary"
            onPress={() => setActiveTab("marketplace")}
            testID="quick-find-services"
          />
          <Button
            title="Messages"
            variant="secondary"
            onPress={() => setActiveTab("messaging")}
            testID="quick-messages"
          />
          <Button
            title="Telehealth"
            variant="secondary"
            onPress={() => setActiveTab("telehealth")}
            testID="quick-telehealth"
          />
        </View>

        {/* Data sections */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : (
          <>
            {/* Upcoming appointments */}
            {upcomingAppointments.length > 0 ? (
              <Card>
                <CardHeader title="Upcoming Appointments" />
                <CardBody>
                  {upcomingAppointments.map((appt) => (
                    <View
                      key={appt.id}
                      testID={`home-appointment-${appt.id}`}
                      style={styles.listItem}
                    >
                      <Text style={styles.listItemTitle}>{appt.appointmentType}</Text>
                      <Text style={styles.listItemSubtitle}>
                        {`${appt.facilityName} \u2022 ${new Date(appt.scheduledAt).toLocaleDateString()}`}
                      </Text>
                    </View>
                  ))}
                </CardBody>
              </Card>
            ) : null}

            {/* Active prescriptions */}
            {activePrescriptions.length > 0 ? (
              <Card>
                <CardHeader title="Active Prescriptions" />
                <CardBody>
                  {activePrescriptions.map((rx) => (
                    <View
                      key={rx.id}
                      testID={`home-rx-${rx.id}`}
                      style={styles.listItem}
                    >
                      <Text style={styles.listItemTitle}>{rx.medicationName}</Text>
                      <Text style={styles.listItemSubtitle}>
                        {`${rx.dosage} \u2022 ${rx.frequency}`}
                      </Text>
                    </View>
                  ))}
                </CardBody>
              </Card>
            ) : null}

            {/* Pending results */}
            {pendingResults.length > 0 ? (
              <Card>
                <CardHeader title="Pending Results" />
                <CardBody>
                  {pendingResults.map((lab) => (
                    <View
                      key={lab.id}
                      testID={`home-lab-${lab.id}`}
                      style={styles.listItem}
                    >
                      <Text style={styles.listItemTitle}>{lab.testName}</Text>
                      <Text style={styles.listItemSubtitle}>{lab.facilityName}</Text>
                    </View>
                  ))}
                </CardBody>
              </Card>
            ) : null}
          </>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    padding: 16,
    gap: 16,
  },
  profileRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  profileName: {
    fontSize: 16,
    fontWeight: "700",
  },
  profileFacility: {
    fontSize: 13,
    color: "#6B7280",
    margin: 0,
  },
  quickActions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  listItem: {
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#F3F4F6",
  },
  listItemTitle: {
    fontSize: 14,
    fontWeight: "700",
  },
  listItemSubtitle: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
});
