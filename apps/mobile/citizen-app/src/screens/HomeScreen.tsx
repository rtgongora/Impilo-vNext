/**
 * HomeScreen — Citizen dashboard with quick-access cards and upcoming items.
 * Synced with web Experience app layout:
 * - Health ID QR prominent at top
 * - Quick actions grid
 * - Upcoming appointments with countdown
 * - Active prescriptions with badges
 */

import React, { useState, useEffect } from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
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

  const appointmentCount = upcomingAppointments.length;
  const prescriptionCount = activePrescriptions.length;

  const greeting = profile
    ? `Hello, ${profile.givenName}`
    : "Welcome";

  const nextAppointment = upcomingAppointments[0];
  const getNextAppointmentText = () => {
    if (!nextAppointment) return null;
    const date = new Date(nextAppointment.scheduledAt);
    const now = new Date();
    const diffMs = date.getTime() - now.getTime();
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHours < 24) {
      return `Today at ${date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
    }
    const diffDays = Math.floor(diffHours / 24);
    return diffDays === 1 ? "Tomorrow" : `In ${diffDays} days`;
  };

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
      <ScrollView testID="home-screen" style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Health ID Card - Prominent at top (matches web layout) */}
        <Pressable
          testID="health-id-card"
          style={styles.healthIdCard}
          onPress={() => setActiveTab("personal")}
        >
          <View style={styles.healthIdIcon}>
            <Text style={styles.healthIdIconText}>ID</Text>
          </View>
          <View style={styles.healthIdText}>
            <Text style={styles.healthIdTitle}>My Health ID</Text>
            <Text style={styles.healthIdSubtitle}>Tap to show QR code</Text>
          </View>
          <Text style={styles.chevron}>›</Text>
        </Pressable>

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

        {/* Quick actions - Grid layout (matches web) */}
        <View style={styles.quickActions}>
          <Button
            title="Book"
            variant="primary"
            onPress={() => setActiveTab("personal")}
            testID="quick-book-appointment"
            style={styles.quickActionBtn}
          />
          <Button
            title="Refill"
            variant="secondary"
            onPress={() => setActiveTab("personal")}
            testID="quick-refill"
            style={styles.quickActionBtn}
          />
          <Button
            title="Message"
            variant="secondary"
            onPress={() => setActiveTab("messaging")}
            testID="quick-messages"
            style={styles.quickActionBtn}
          />
          <Button
            title="Video"
            variant="secondary"
            onPress={() => setActiveTab("telehealth")}
            testID="quick-telehealth"
            style={styles.quickActionBtn}
          />
          <Button
            title="Records"
            variant="secondary"
            onPress={() => setActiveTab("personal")}
            testID="quick-records"
            style={styles.quickActionBtn}
          />
          <Button
            title="Find"
            variant="secondary"
            onPress={() => setActiveTab("marketplace")}
            testID="quick-find-services"
            style={styles.quickActionBtn}
          />
        </View>

        {/* Data sections */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : (
          <>
            {/* Upcoming Appointments (matches web right widget) */}
            <Card>
              <CardHeader 
                title="Upcoming" 
                rightElement={
                  appointmentCount > 0 ? (
                    <Badge variant="default">{appointmentCount}</Badge>
                  ) : null
                }
              />
              <CardBody>
                {appointmentCount > 0 ? (
                  <View style={styles.appointmentCard}>
                    <View style={styles.appointmentDate}>
                      <Text style={styles.appointmentDay}>
                        {new Date(nextAppointment.scheduledAt).toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" })}
                      </Text>
                      <Text style={styles.appointmentTime}>
                        {new Date(nextAppointment.scheduledAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                      </Text>
                    </View>
                    <View style={styles.appointmentDetails}>
                      <Text style={styles.appointmentType}>{nextAppointment.appointmentType}</Text>
                      <Text style={styles.appointmentFacility}>{nextAppointment.facilityName}</Text>
                    </View>
                  </View>
                ) : (
                  <Text style={styles.emptyText}>No upcoming appointments</Text>
                )}
              </CardBody>
            </Card>

            {/* Active Prescriptions with badge */}
            <Card>
              <CardHeader 
                title="Medications" 
                rightElement={
                  prescriptionCount > 0 ? (
                    <Badge variant="warning">{prescriptionCount}</Badge>
                  ) : null
                }
              />
              <CardBody>
                {prescriptionCount > 0 ? (
                  activePrescriptions.map((rx) => (
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
                  ))
                ) : (
                  <Text style={styles.emptyText}>No active medications</Text>
                )}
              </CardBody>
            </Card>

            {/* Pending Results */}
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
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    flex: 1,
  },
  contentContainer: {
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
  healthIdCard: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#4F46E5",
    padding: 16,
    borderRadius: 12,
    gap: 12,
  },
  healthIdIcon: {
    width: 40,
    height: 40,
    borderRadius: 8,
    backgroundColor: "#6366F1",
    alignItems: "center",
    justifyContent: "center",
  },
  healthIdIconText: {
    fontSize: 14,
    fontWeight: "700",
    color: "#FFFFFF",
  },
  healthIdText: {
    flex: 1,
  },
  healthIdTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#FFFFFF",
  },
  healthIdSubtitle: {
    fontSize: 12,
    color: "rgba(255, 255, 255, 0.8)",
  },
  chevron: {
    fontSize: 24,
    color: "#9CA3AF",
  },
  quickActions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  quickActionBtn: {
    minWidth: "30%",
    flex: 1,
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
  appointmentCard: {
    flexDirection: "row",
    paddingVertical: 4,
    gap: 12,
  },
  appointmentDate: {
    alignItems: "center",
    paddingRight: 12,
    borderRightWidth: 1,
    borderRightColor: "#E5E7EB",
  },
  appointmentDay: {
    fontSize: 12,
    fontWeight: "600",
    color: "#4F46E5",
  },
  appointmentTime: {
    fontSize: 14,
    fontWeight: "700",
  },
  appointmentDetails: {
    flex: 1,
  },
  appointmentType: {
    fontSize: 14,
    fontWeight: "600",
  },
  appointmentFacility: {
    fontSize: 12,
    color: "#6B7280",
  },
  emptyText: {
    fontSize: 13,
    color: "#9CA3AF",
    textAlign: "center",
    paddingVertical: 16,
  },
});
