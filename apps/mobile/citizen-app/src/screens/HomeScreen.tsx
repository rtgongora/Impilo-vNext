/**
 * HomeScreen — Citizen dashboard with quick-access cards and upcoming items.
 * Modern card layout with green brand accent.
 */

import React, { useState, useEffect } from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
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

const GREEN = "#059669";

const QUICK_ACTIONS = [
  { id: "book",     label: "Book",    icon: "calendar-outline",   tab: "personal",    color: "#059669", bg: "#D1FAE5" },
  { id: "refill",   label: "Refill",  icon: "refresh-outline",    tab: "personal",    color: "#1E40AF", bg: "#DBEAFE" },
  { id: "message",  label: "Message", icon: "chatbubble-outline",  tab: "messaging",   color: "#7C3AED", bg: "#EDE9FE" },
  { id: "telehealth", label: "Video", icon: "videocam-outline",   tab: "telehealth",  color: "#0891B2", bg: "#CFFAFE" },
  { id: "records",  label: "Records", icon: "folder-outline",     tab: "personal",    color: "#D97706", bg: "#FEF3C7" },
  { id: "find",     label: "Find",    icon: "search-outline",     tab: "marketplace", color: "#DC2626", bg: "#FEE2E2" },
] as const;

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
  const nextAppointment = upcomingAppointments[0];

  const greeting = profile
    ? `Hello, ${profile.givenName} 👋`
    : "Welcome back";

  return (
    <Screen>
      <Header
        title={greeting}
        rightElement={
          <Pressable
            onPress={() => setActiveTab("messaging")}
            style={styles.notifBtn}
            hitSlop={8}
          >
            <Ionicons name="notifications-outline" size={24} color={GREEN} />
            {unreadNotifications > 0 ? (
              <View style={styles.notifDot}>
                <Text style={styles.notifDotText}>
                  {unreadNotifications > 9 ? "9+" : String(unreadNotifications)}
                </Text>
              </View>
            ) : null}
          </Pressable>
        }
      />

      <ScrollView
        testID="home-screen"
        style={styles.scroll}
        contentContainerStyle={styles.content}
        showsVerticalScrollIndicator={false}
      >
        {/* Health ID Banner */}
        <Pressable
          testID="health-id-card"
          onPress={() => setActiveTab("personal")}
          style={styles.healthIdBanner}
        >
          <View style={styles.healthIdLeft}>
            <View style={styles.healthIdIconWrap}>
              <Ionicons name="id-card" size={24} color="#FFFFFF" />
            </View>
            <View>
              <Text style={styles.healthIdTitle}>My Health ID</Text>
              <Text style={styles.healthIdSub}>Tap to show QR code</Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={18} color="rgba(255,255,255,0.6)" />
        </Pressable>

        {/* Profile card */}
        {profile ? (
          <Card variant="elevated" padding="md">
            <CardBody>
              <View style={styles.profileRow}>
                <Avatar
                  name={`${profile.givenName} ${profile.familyName}`}
                  size="lg"
                />
                <View style={styles.profileText}>
                  <Text style={styles.profileName}>
                    {`${profile.givenName} ${profile.familyName}`}
                  </Text>
                  <Text style={styles.profileFacility}>
                    {profile.facilityName ?? "Primary facility not set"}
                  </Text>
                  {profile.cpid ? (
                    <Text style={styles.profileCpid}>ID: {profile.cpid}</Text>
                  ) : null}
                </View>
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* Quick Actions Grid */}
        <Text style={styles.sectionLabel}>Quick Actions</Text>
        <View style={styles.quickGrid}>
          {QUICK_ACTIONS.map((action) => (
            <Pressable
              key={action.id}
              testID={`quick-${action.id}`}
              onPress={() => setActiveTab(action.tab as Parameters<typeof setActiveTab>[0])}
              style={({ pressed }) => [
                styles.quickCard,
                { opacity: pressed ? 0.85 : 1 },
              ]}
            >
              <View style={[styles.quickIconCircle, { backgroundColor: action.bg }]}>
                <Ionicons name={action.icon as never} size={22} color={action.color} />
              </View>
              <Text style={[styles.quickLabel, { color: action.color }]}>
                {action.label}
              </Text>
            </Pressable>
          ))}
        </View>

        {/* Data sections */}
        {isLoading ? (
          <View style={styles.loadingBox}>
            <LoadingSpinner size="sm" />
          </View>
        ) : (
          <>
            {/* Upcoming Appointment */}
            <Card variant="elevated" padding="md">
              <CardHeader
                title="Upcoming Appointment"
                rightElement={
                  appointmentCount > 0 ? (
                    <Badge variant="primary">{appointmentCount}</Badge>
                  ) : null
                }
              />
              <CardBody>
                {nextAppointment ? (
                  <View style={styles.apptRow}>
                    <View style={styles.apptDateBox}>
                      <Text style={styles.apptDay}>
                        {new Date(nextAppointment.scheduledAt).toLocaleDateString([], {
                          weekday: "short",
                        })}
                      </Text>
                      <Text style={styles.apptDate}>
                        {new Date(nextAppointment.scheduledAt).getDate()}
                      </Text>
                      <Text style={styles.apptMonth}>
                        {new Date(nextAppointment.scheduledAt).toLocaleDateString([], {
                          month: "short",
                        })}
                      </Text>
                    </View>
                    <View style={styles.apptDetails}>
                      <Text style={styles.apptType}>{nextAppointment.appointmentType}</Text>
                      <View style={styles.apptMetaRow}>
                        <Ionicons name="location-outline" size={12} color="#6B7280" />
                        <Text style={styles.apptFacility}>{nextAppointment.facilityName}</Text>
                      </View>
                      <View style={styles.apptMetaRow}>
                        <Ionicons name="time-outline" size={12} color="#6B7280" />
                        <Text style={styles.apptTime}>
                          {new Date(nextAppointment.scheduledAt).toLocaleTimeString([], {
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </Text>
                      </View>
                    </View>
                    <Badge variant="success">Scheduled</Badge>
                  </View>
                ) : (
                  <View style={styles.emptyRow}>
                    <Ionicons name="calendar-outline" size={32} color="#D1D5DB" />
                    <Text style={styles.emptyText}>No upcoming appointments</Text>
                    <Button
                      title="Book Now"
                      variant="outline"
                      size="sm"
                      onPress={() => setActiveTab("personal")}
                    />
                  </View>
                )}
              </CardBody>
            </Card>

            {/* Active Medications */}
            <Card variant="elevated" padding="md">
              <CardHeader
                title="Medications"
                rightElement={
                  prescriptionCount > 0 ? (
                    <Badge variant="info">{prescriptionCount} active</Badge>
                  ) : null
                }
              />
              <CardBody>
                {prescriptionCount > 0 ? (
                  activePrescriptions.map((rx, idx) => (
                    <View
                      key={rx.id}
                      testID={`home-rx-${rx.id}`}
                      style={[
                        styles.listRow,
                        idx < prescriptionCount - 1 ? styles.listRowBorder : undefined,
                      ]}
                    >
                      <View style={styles.rxIconWrap}>
                        <Ionicons name="medical" size={16} color="#1E40AF" />
                      </View>
                      <View style={styles.listText}>
                        <Text style={styles.listTitle}>{rx.medicationName}</Text>
                        <Text style={styles.listSub}>{`${rx.dosage} · ${rx.frequency}`}</Text>
                      </View>
                      <Badge variant="info" size="sm">{rx.refillsRemaining} refills</Badge>
                    </View>
                  ))
                ) : (
                  <View style={styles.emptyRow}>
                    <Ionicons name="medical-outline" size={32} color="#D1D5DB" />
                    <Text style={styles.emptyText}>No active medications</Text>
                  </View>
                )}
              </CardBody>
            </Card>

            {/* Pending Lab Results */}
            {pendingResults.length > 0 ? (
              <Card variant="elevated" padding="md">
                <CardHeader
                  title="Pending Results"
                  rightElement={
                    <Badge variant="warning">{pendingResults.length}</Badge>
                  }
                />
                <CardBody>
                  {pendingResults.map((lab, idx) => (
                    <View
                      key={lab.id}
                      testID={`home-lab-${lab.id}`}
                      style={[
                        styles.listRow,
                        idx < pendingResults.length - 1 ? styles.listRowBorder : undefined,
                      ]}
                    >
                      <View style={[styles.rxIconWrap, { backgroundColor: "#FEF3C7" }]}>
                        <Ionicons name="flask" size={16} color="#D97706" />
                      </View>
                      <View style={styles.listText}>
                        <Text style={styles.listTitle}>{lab.testName}</Text>
                        <Text style={styles.listSub}>{lab.facilityName}</Text>
                      </View>
                      <Badge variant="warning" size="sm">{lab.status}</Badge>
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
  scroll: {
    flex: 1,
  },
  content: {
    padding: 16,
    gap: 16,
    paddingBottom: 32,
  },
  notifBtn: {
    position: "relative",
    padding: 4,
  },
  notifDot: {
    position: "absolute",
    top: 0,
    right: 0,
    backgroundColor: "#DC2626",
    borderRadius: 8,
    minWidth: 16,
    height: 16,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 3,
    borderWidth: 1.5,
    borderColor: "#FFFFFF",
  },
  notifDotText: {
    fontSize: 9,
    color: "#FFFFFF",
    fontWeight: "700",
  },
  healthIdBanner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: GREEN,
    padding: 16,
    borderRadius: 16,
    shadowColor: GREEN,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 12,
    elevation: 6,
  },
  healthIdLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  healthIdIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: "rgba(255,255,255,0.2)",
    alignItems: "center",
    justifyContent: "center",
  },
  healthIdTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#FFFFFF",
  },
  healthIdSub: {
    fontSize: 12,
    color: "rgba(255,255,255,0.75)",
    marginTop: 2,
  },
  profileRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
  },
  profileText: {
    flex: 1,
    gap: 2,
  },
  profileName: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  profileFacility: {
    fontSize: 13,
    color: "#6B7280",
  },
  profileCpid: {
    fontSize: 11,
    color: "#9CA3AF",
    fontFamily: "monospace",
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    color: "#6B7280",
    letterSpacing: 0.8,
    textTransform: "uppercase",
    marginBottom: -8,
  },
  quickGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  quickCard: {
    width: "30%",
    flex: 1,
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 12,
    alignItems: "center",
    gap: 8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  quickIconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
  },
  quickLabel: {
    fontSize: 12,
    fontWeight: "600",
    textAlign: "center",
  },
  loadingBox: {
    paddingVertical: 32,
    alignItems: "center",
  },
  apptRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
  },
  apptDateBox: {
    alignItems: "center",
    backgroundColor: "#F0FDF4",
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 12,
    minWidth: 52,
  },
  apptDay: {
    fontSize: 11,
    color: GREEN,
    fontWeight: "600",
    textTransform: "uppercase",
  },
  apptDate: {
    fontSize: 22,
    fontWeight: "800",
    color: GREEN,
    lineHeight: 26,
  },
  apptMonth: {
    fontSize: 11,
    color: GREEN,
    fontWeight: "600",
    textTransform: "uppercase",
  },
  apptDetails: {
    flex: 1,
    gap: 4,
  },
  apptType: {
    fontSize: 15,
    fontWeight: "700",
    color: "#111827",
  },
  apptMetaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  apptFacility: {
    fontSize: 12,
    color: "#6B7280",
  },
  apptTime: {
    fontSize: 12,
    color: "#6B7280",
  },
  emptyRow: {
    alignItems: "center",
    paddingVertical: 16,
    gap: 8,
  },
  emptyText: {
    fontSize: 13,
    color: "#9CA3AF",
    textAlign: "center",
  },
  listRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 10,
  },
  listRowBorder: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "#F3F4F6",
  },
  rxIconWrap: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
  },
  listText: {
    flex: 1,
    gap: 2,
  },
  listTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  listSub: {
    fontSize: 12,
    color: "#6B7280",
  },
});
