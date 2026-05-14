/**
 * ProviderDashboardScreen — Worklist/task queue for the provider.
 *
 * Shows assigned tasks, today's encounters, overdue items, and quick actions.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { appStore, useAppStore } from "../../stores/appStore";
import { useEncounterStore } from "../../stores/encounterStore";
import { getMyTasks } from "../../services/taskService";
import { listEncounters } from "../../services/encounterService";
import { getFacilityMetrics } from "../../services/supportService";
import type { Task, Encounter, FacilityMetrics, ProviderTabKey } from "../../types";

const BLUE = "#1E40AF";

const LAUNCH_ACTIONS: Array<{
  key: string;
  title: string;
  subtitle: string;
  target: ProviderTabKey;
  icon: string;
  color: string;
  bg: string;
}> = [
  { key: "find-patient", title: "Find Patient", subtitle: "Search and start a new encounter", target: "patients", icon: "search-outline", color: "#059669", bg: "#D1FAE5" },
  { key: "queue",        title: "Open Queue",   subtitle: "Pick up the next waiting patient",  target: "queue",    icon: "people-outline", color: BLUE, bg: "#DBEAFE" },
  { key: "results",      title: "Lab Results",  subtitle: "Check new lab and imaging output",  target: "results",  icon: "flask-outline",  color: "#7C3AED", bg: "#EDE9FE" },
  { key: "tools",        title: "Clinical Tools", subtitle: "Notes, plans, CDS, and handoff", target: "tools",    icon: "construct-outline", color: "#D97706", bg: "#FEF3C7" },
];

const PRIORITY_VARIANT: Record<string, string> = {
  URGENT: "destructive",
  HIGH: "warning",
  MEDIUM: "secondary",
  LOW: "outline",
};

export function ProviderDashboardScreen() {
  const { facilityId, facilityName, workspaceName, setProviderTab, setMode } = useAppStore();
  const { activeEncounter } = useEncounterStore();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [openEncounters, setOpenEncounters] = useState<Encounter[]>([]);
  const [metrics, setMetrics] = useState<FacilityMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    if (!facilityId) {
      setTasks([]);
      setOpenEncounters([]);
      setMetrics(null);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [taskResult, encounterResult, metricsResult] = await Promise.all([
        getMyTasks(undefined, 0, 50),
        listEncounters(undefined, 0, 20),
        getFacilityMetrics(facilityId).catch(() => null),
      ]);
      setTasks(taskResult.tasks);
      setOpenEncounters(
        encounterResult.encounters.filter((e) => e.status === "IN_PROGRESS")
      );
      setMetrics(metricsResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dashboard");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  if (loading) {
    return (
      <Screen>
        <Header title="Worklist" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Worklist" />
        <ErrorState title="Error" message={error} onRetry={loadDashboard} />
      </Screen>
    );
  }

  const pendingTasks = tasks.filter((t) => t.status === "PENDING" || t.status === "IN_PROGRESS");
  const overdueTasks = tasks.filter((t) => t.status === "OVERDUE");

  if (!facilityId) {
    return (
      <Screen>
        <Header title="Launch Care" />
        <EmptyState
          title="Select a facility first"
          message="Choose your facility and workspace before starting service delivery."
          action={{
            label: "Refresh",
            onPress: loadDashboard,
          }}
        />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Launch Care" accent={BLUE} />
      <ScrollView
        testID="provider-dashboard"
        style={styles.scroll}
        contentContainerStyle={styles.container}
        showsVerticalScrollIndicator={false}
      >
        {/* Hero banner */}
        <View style={styles.heroBanner}>
          <View style={styles.heroLeft}>
            <Text style={styles.heroEyebrow}>Provider Mode</Text>
            <Text style={styles.heroTitle}>
              {activeEncounter ? "Encounter in progress" : "Start service delivery"}
            </Text>
            <View style={styles.contextRow}>
              {facilityName ? (
                <View style={styles.contextChip}>
                  <Ionicons name="business-outline" size={11} color="rgba(255,255,255,0.8)" />
                  <Text style={styles.contextChipText}>{facilityName}</Text>
                </View>
              ) : null}
              {workspaceName ? (
                <View style={styles.contextChip}>
                  <Ionicons name="desktop-outline" size={11} color="rgba(255,255,255,0.8)" />
                  <Text style={styles.contextChipText}>{workspaceName}</Text>
                </View>
              ) : null}
              {activeEncounter ? (
                <View style={[styles.contextChip, styles.contextChipActive]}>
                  <Ionicons name="pulse" size={11} color="#FFFFFF" />
                  <Text style={styles.contextChipText}>{activeEncounter.encounterType}</Text>
                </View>
              ) : null}
            </View>
          </View>
          <Pressable
            style={({ pressed }) => [styles.heroCtaBtn, { opacity: pressed ? 0.85 : 1 }]}
            onPress={() => setProviderTab(activeEncounter ? "encounter" : "patients")}
            testID="launch-primary-action"
          >
            <Ionicons
              name={activeEncounter ? "pulse" : "add-circle"}
              size={20}
              color={BLUE}
            />
            <Text style={styles.heroCtaText}>
              {activeEncounter ? "Resume" : "New Encounter"}
            </Text>
          </Pressable>
        </View>

        {/* Summary metrics */}
        <View style={styles.metricsRow}>
          {[
            { label: "Pending", value: pendingTasks.length, color: "#111827", icon: "list" },
            { label: "Seen Today", value: metrics?.patientsSeenToday ?? openEncounters.length, color: "#059669", icon: "checkmark-circle" },
            { label: "Open Visits", value: metrics?.encountersOpen ?? overdueTasks.length, color: overdueTasks.length > 0 ? "#DC2626" : "#111827", icon: "medical" },
            { label: "Stock Alerts", value: metrics?.stockAlerts ?? 0, color: (metrics?.stockAlerts ?? 0) > 0 ? "#DC2626" : "#111827", icon: "warning" },
          ].map((m) => (
            <Card key={m.label} variant="elevated" padding="sm" style={styles.metricCard}>
              <CardBody>
                <View style={styles.metricContent}>
                  <Ionicons name={m.icon as never} size={18} color={m.color} />
                  <Text style={[styles.metricValue, { color: m.color }]}>{String(m.value)}</Text>
                  <Text style={styles.metricLabel}>{m.label}</Text>
                </View>
              </CardBody>
            </Card>
          ))}
        </View>

        {/* Quick launch actions */}
        <Text style={styles.sectionLabel}>Quick Launch</Text>
        <View style={styles.launchGrid}>
          {LAUNCH_ACTIONS.map((action) => (
            <Pressable
              key={action.key}
              style={({ pressed }) => [styles.launchCard, { opacity: pressed ? 0.88 : 1 }]}
              onPress={() => setProviderTab(action.target)}
              testID={`launch-${action.key}`}
            >
              <View style={[styles.launchIconCircle, { backgroundColor: action.bg }]}>
                <Ionicons name={action.icon as never} size={22} color={action.color} />
              </View>
              <Text style={[styles.launchTitle, { color: action.color }]}>{action.title}</Text>
              <Text style={styles.launchSubtitle}>{action.subtitle}</Text>
            </Pressable>
          ))}
          {activeEncounter ? (
            <Pressable
              style={({ pressed }) => [styles.launchCard, styles.launchCardActive, { opacity: pressed ? 0.88 : 1 }]}
              onPress={() => setProviderTab("encounter")}
              testID="launch-resume-encounter"
            >
              <View style={[styles.launchIconCircle, { backgroundColor: "#D1FAE5" }]}>
                <Ionicons name="pulse" size={22} color="#059669" />
              </View>
              <Text style={[styles.launchTitle, { color: "#059669" }]}>Resume</Text>
              <Text style={styles.launchSubtitle}>Continue live encounter</Text>
            </Pressable>
          ) : null}
        </View>

        <Text style={styles.sectionLabel}>Safety, support &amp; comms</Text>
        <View style={styles.supportRow}>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => setProviderTab("messaging")}
            testID="launch-comms-hub"
          >
            <Ionicons name="chatbubbles-outline" size={16} color={BLUE} />
            <Text style={styles.supportChipText}>Comms</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => {
              appStore.getState().setClinicalToolsInitialTab("telemedicine");
              setProviderTab("tools");
            }}
            testID="launch-telehealth-tools"
          >
            <Ionicons name="videocam-outline" size={16} color="#0891B2" />
            <Text style={styles.supportChipText}>Telehealth</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => {
              appStore.getState().setClinicalToolsInitialTab("learning");
              setProviderTab("tools");
            }}
            testID="launch-fundo-learning"
          >
            <Ionicons name="school-outline" size={16} color="#1E40AF" />
            <Text style={styles.supportChipText}>Fundo</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() => {
              appStore.getState().setSupervisorEntryTab("escalations");
              setMode("supervisor");
            }}
            testID="launch-system-support"
          >
            <Ionicons name="construct-outline" size={16} color="#7C3AED" />
            <Text style={styles.supportChipText}>Support</Text>
          </Pressable>
          <Pressable
            style={({ pressed }) => [styles.supportChip, styles.supportChipSos, { opacity: pressed ? 0.88 : 1 }]}
            onPress={() =>
              Alert.alert(
                "SOS & escalations",
                "For immediate danger to life, call your local emergency number.\n\nFor facility incidents, outages, safeguarding, or security events, open Support to file a ticket or use your facility escalation protocol.",
                [
                  { text: "Cancel", style: "cancel" },
                  {
                    text: "Open support",
                    onPress: () => {
                      appStore.getState().setSupervisorEntryTab("escalations");
                      setMode("supervisor");
                    },
                  },
                ],
              )
            }
            testID="launch-sos-info"
          >
            <Ionicons name="warning-outline" size={16} color="#B91C1C" />
            <Text style={[styles.supportChipText, { color: "#B91C1C" }]}>SOS</Text>
          </Pressable>
        </View>

        {/* Task list */}
        <Text style={styles.sectionLabel}>My Tasks</Text>
        {pendingTasks.length === 0 ? (
          <EmptyState title="No pending tasks" message="All caught up for this shift" />
        ) : (
          pendingTasks.map((task) => (
            <Card key={task.id} variant="elevated" padding="md">
              <CardBody>
                <Pressable
                  testID={`task-${task.id}`}
                  onPress={() => setSelectedTaskId(task.id === selectedTaskId ? null : task.id)}
                >
                  <View style={styles.taskRow}>
                    <View style={styles.taskLeft}>
                      <Text style={styles.taskTitle}>{task.title}</Text>
                      {task.patientName ? (
                        <View style={styles.taskMeta}>
                          <Ionicons name="person-outline" size={12} color="#6B7280" />
                          <Text style={styles.taskPatient}>{task.patientName}</Text>
                        </View>
                      ) : null}
                      <View style={styles.taskMeta}>
                        <Badge variant="outline" size="sm">{task.taskType}</Badge>
                        {task.dueAt ? (
                          <Text style={styles.taskDue}>
                            {`Due ${new Date(task.dueAt).toLocaleDateString()}`}
                          </Text>
                        ) : null}
                      </View>
                    </View>
                    <Badge variant={PRIORITY_VARIANT[task.priority] as "destructive" | "secondary" | "outline"} size="sm">
                      {task.priority}
                    </Badge>
                  </View>
                </Pressable>
              </CardBody>
            </Card>
          ))
        )}

        {/* Open encounters */}
        {openEncounters.length > 0 ? (
          <>
            <Text style={styles.sectionLabel}>Open Encounters</Text>
            {openEncounters.map((enc) => (
              <Card key={enc.id} variant="elevated" padding="md">
                <CardBody>
                  <View testID={`encounter-${enc.id}`} style={styles.encounterRow}>
                    <View style={styles.encounterIconWrap}>
                      <Ionicons name="pulse" size={20} color={BLUE} />
                    </View>
                    <View style={styles.encounterText}>
                      <Text style={styles.taskTitle}>{`${enc.encounterType} Visit`}</Text>
                      <Text style={styles.taskPatient}>
                        {`Started ${new Date(enc.startedAt).toLocaleTimeString()}`}
                      </Text>
                      {enc.chiefComplaint ? (
                        <Text style={styles.taskDue}>{`CC: ${enc.chiefComplaint}`}</Text>
                      ) : null}
                    </View>
                    <Badge variant="info" size="sm">Active</Badge>
                  </View>
                </CardBody>
              </Card>
            ))}
          </>
        ) : null}

        <View style={styles.refreshRow}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={loadDashboard}
            testID="refresh-dashboard"
            icon={<Ionicons name="refresh-outline" size={16} color="#059669" />}
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 12,
    paddingBottom: 32,
  },
  heroBanner: {
    backgroundColor: BLUE,
    borderRadius: 20,
    padding: 20,
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    gap: 12,
    shadowColor: BLUE,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 12,
    elevation: 6,
  },
  heroLeft: {
    flex: 1,
    gap: 6,
  },
  heroEyebrow: {
    fontSize: 11,
    fontWeight: "700",
    color: "rgba(255,255,255,0.7)",
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
  heroTitle: {
    fontSize: 20,
    fontWeight: "800",
    color: "#FFFFFF",
    letterSpacing: -0.3,
  },
  contextRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6,
    marginTop: 4,
  },
  contextChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: "rgba(255,255,255,0.15)",
    borderRadius: 999,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  contextChipActive: {
    backgroundColor: "rgba(255,255,255,0.25)",
  },
  contextChipText: {
    fontSize: 11,
    color: "rgba(255,255,255,0.9)",
    fontWeight: "500",
  },
  heroCtaBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 14,
  },
  heroCtaText: {
    fontSize: 13,
    fontWeight: "700",
    color: BLUE,
  },
  metricsRow: {
    flexDirection: "row",
    gap: 8,
  },
  metricCard: {
    flex: 1,
  },
  metricContent: {
    alignItems: "center",
    gap: 2,
  },
  metricValue: {
    fontSize: 22,
    fontWeight: "800",
  },
  metricLabel: {
    fontSize: 10,
    color: "#6B7280",
    textAlign: "center",
  },
  sectionLabel: {
    fontSize: 12,
    fontWeight: "700",
    color: "#6B7280",
    letterSpacing: 0.8,
    textTransform: "uppercase",
    marginTop: 4,
  },
  launchGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
  },
  launchCard: {
    flexBasis: "47%",
    flex: 1,
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 14,
    minHeight: 110,
    gap: 6,
    alignItems: "flex-start",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 4,
    elevation: 2,
  },
  launchCardActive: {
    borderWidth: 1.5,
    borderColor: "#A7F3D0",
  },
  launchIconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 2,
  },
  launchTitle: {
    fontSize: 14,
    fontWeight: "700",
  },
  launchSubtitle: {
    fontSize: 11,
    color: "#6B7280",
    lineHeight: 16,
  },
  taskRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 12,
  },
  taskLeft: {
    flex: 1,
    gap: 4,
  },
  taskTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  taskMeta: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  taskPatient: {
    fontSize: 12,
    color: "#6B7280",
  },
  taskDue: {
    fontSize: 11,
    color: "#9CA3AF",
  },
  encounterRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  encounterIconWrap: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
  },
  encounterText: {
    flex: 1,
    gap: 2,
  },
  refreshRow: {
    alignItems: "center",
    marginTop: 8,
  },
  supportRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  supportChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 12,
    backgroundColor: "#FFFFFF",
    borderWidth: 1,
    borderColor: "#E5E7EB",
  },
  supportChipSos: {
    borderColor: "#FECACA",
    backgroundColor: "#FEF2F2",
  },
  supportChipText: {
    fontSize: 13,
    fontWeight: "700",
    color: "#1F2937",
  },
});
