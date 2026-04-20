/**
 * ProviderDashboardScreen — Worklist/task queue for the provider.
 *
 * Shows assigned tasks, today's encounters, overdue items, and quick actions.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
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
import { useAppStore } from "../../stores/appStore";
import { useEncounterStore } from "../../stores/encounterStore";
import { getMyTasks } from "../../services/taskService";
import { listEncounters } from "../../services/encounterService";
import { getFacilityMetrics } from "../../services/supportService";
import type { Task, Encounter, FacilityMetrics, ProviderTabKey } from "../../types";

const PRIORITY_VARIANT: Record<string, string> = {
  URGENT: "destructive",
  HIGH: "warning",
  MEDIUM: "secondary",
  LOW: "outline",
};

export function ProviderDashboardScreen() {
  const { facilityId, facilityName, workspaceName, setProviderTab } = useAppStore();
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
  const launchActions: Array<{
    key: string;
    title: string;
    subtitle: string;
    target: ProviderTabKey;
  }> = [
    {
      key: "find-patient",
      title: "Find patient",
      subtitle: "Search and start a new encounter",
      target: "patients",
    },
    {
      key: "queue",
      title: "Open queue",
      subtitle: "Pick up the next waiting patient",
      target: "queue",
    },
    {
      key: "results",
      title: "Review results",
      subtitle: "Check new lab and imaging output",
      target: "results",
    },
    {
      key: "tools",
      title: "Clinical tools",
      subtitle: "Notes, plans, CDS, and handoff",
      target: "tools",
    },
  ];

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
      <Header title="Launch Care" />
      <ScrollView testID="provider-dashboard" style={styles.container}>
        <Card>
          <CardBody>
            <View style={styles.heroHeader}>
              <View style={styles.heroText}>
                <Text style={styles.heroEyebrow}>Provider Experience</Text>
                <Text style={styles.heroTitle}>Start service delivery fast</Text>
                <Text style={styles.heroSubtitle}>
                  Launch client care, resume active encounters, and keep the shift moving from one surface.
                </Text>
              </View>
              <TouchableOpacity
                style={styles.heroPrimaryCta}
                onPress={() => setProviderTab(activeEncounter ? "encounter" : "patients")}
                testID="launch-primary-action"
              >
                <Text style={styles.heroPrimaryCtaText}>
                  {activeEncounter ? "Resume encounter" : "Launch client experience"}
                </Text>
              </TouchableOpacity>
            </View>

            <View style={styles.contextRow}>
              <Text style={styles.contextPill}>
                {facilityName ? `Facility: ${facilityName}` : `Facility ID: ${facilityId}`}
              </Text>
              {workspaceName ? <Text style={styles.contextPill}>{`Workspace: ${workspaceName}`}</Text> : null}
              {activeEncounter ? (
                <Text style={[styles.contextPill, styles.contextPillActive]}>
                  {`Encounter active: ${activeEncounter.encounterType}`}
                </Text>
              ) : null}
            </View>
          </CardBody>
        </Card>

        {/* Summary tiles */}
        <View style={styles.summaryRow}>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text style={styles.tileNumber}>{String(pendingTasks.length)}</Text>
                <Text style={styles.tileLabel}>Pending Tasks</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text style={styles.tileNumber}>{String(metrics?.patientsSeenToday ?? openEncounters.length)}</Text>
                <Text style={styles.tileLabel}>Seen Today</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text
                  style={[
                    styles.tileNumber,
                    { color: overdueTasks.length > 0 ? "#DC2626" : "#111827" },
                  ]}
                >
                  {String(metrics?.encountersOpen ?? overdueTasks.length)}
                </Text>
                <Text style={styles.tileLabel}>Open Visits</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text
                  style={[
                    styles.tileNumber,
                    { color: (metrics?.stockAlerts ?? 0) > 0 ? "#DC2626" : "#111827" },
                  ]}
                >
                  {String(metrics?.stockAlerts ?? 0)}
                </Text>
                <Text style={styles.tileLabel}>Stock Alerts</Text>
              </View>
            </CardBody>
          </Card>
        </View>

        <Card>
          <CardBody>
            <View style={styles.sectionHeader}>
              <Text style={styles.sectionTitle}>Launch client experience</Text>
              <Text style={styles.sectionSubtitle}>Quick paths into the work you are most likely to start next.</Text>
            </View>
            <View style={styles.launchGrid}>
              {launchActions.map((action) => (
                <TouchableOpacity
                  key={action.key}
                  style={styles.launchCard}
                  onPress={() => setProviderTab(action.target)}
                  testID={`launch-${action.key}`}
                >
                  <Text style={styles.launchTitle}>{action.title}</Text>
                  <Text style={styles.launchSubtitle}>{action.subtitle}</Text>
                </TouchableOpacity>
              ))}
              {activeEncounter ? (
                <TouchableOpacity
                  style={[styles.launchCard, styles.launchCardActive]}
                  onPress={() => setProviderTab("encounter")}
                  testID="launch-resume-encounter"
                >
                  <Text style={styles.launchTitle}>Resume encounter</Text>
                  <Text style={styles.launchSubtitle}>
                    Continue the live chart, orders, notes, and disposition workflow.
                  </Text>
                </TouchableOpacity>
              ) : null}
            </View>
          </CardBody>
        </Card>

        {/* Task list */}
        <CardHeader title="My Tasks" subtitle="Assigned work that still needs attention this shift." />
        {pendingTasks.length === 0 ? (
          <EmptyState title="No pending tasks" message="All caught up" />
        ) : (
          pendingTasks.map((task) => (
            <Card key={task.id}>
              <CardBody>
                <TouchableOpacity
                  testID={`task-${task.id}`}
                  onPress={() => setSelectedTaskId(task.id === selectedTaskId ? null : task.id)}
                >
                  <View style={styles.taskHeader}>
                    <Text style={styles.bold}>{task.title}</Text>
                    <Badge
                      variant={PRIORITY_VARIANT[task.priority] as "destructive" | "secondary" | "outline"}
                    >
                      {task.priority}
                    </Badge>
                  </View>
                  {task.patientName ? (
                    <Text style={styles.patientName}>
                      {`Patient: ${task.patientName}`}
                    </Text>
                  ) : null}
                  <View style={styles.taskMeta}>
                    <Badge variant="outline">{task.taskType}</Badge>
                    {task.dueAt ? (
                      <Text style={styles.dueDate}>
                        {`Due: ${new Date(task.dueAt).toLocaleDateString()}`}
                      </Text>
                    ) : null}
                  </View>
                </TouchableOpacity>
              </CardBody>
            </Card>
          ))
        )}

        {/* Open encounters section */}
        {openEncounters.length > 0 ? (
          <>
            <CardHeader title="Open Encounters" subtitle="Recent active visits you may need to resume or complete." />
            {openEncounters.map((enc) => (
              <Card key={enc.id}>
                <CardBody>
                  <View testID={`encounter-${enc.id}`}>
                    <Text style={styles.bold}>{`${enc.encounterType} Visit`}</Text>
                    <Text style={styles.encounterTime}>
                      {`Started: ${new Date(enc.startedAt).toLocaleTimeString()}`}
                    </Text>
                    {enc.chiefComplaint ? (
                      <Text style={styles.chiefComplaint}>
                        {`CC: ${enc.chiefComplaint}`}
                      </Text>
                    ) : null}
                  </View>
                </CardBody>
              </Card>
            ))}
          </>
        ) : null}

        <View style={styles.refreshContainer}>
          <Button
            title="Refresh"
            variant="outline"
            onPress={loadDashboard}
            testID="refresh-dashboard"
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  heroHeader: {
    gap: 16,
  },
  heroText: {
    gap: 6,
  },
  heroEyebrow: {
    fontSize: 11,
    fontWeight: "700",
    color: "#0F766E",
    textTransform: "uppercase",
    letterSpacing: 0.8,
  },
  heroTitle: {
    fontSize: 22,
    fontWeight: "700",
    color: "#111827",
  },
  heroSubtitle: {
    fontSize: 13,
    color: "#4B5563",
    lineHeight: 20,
  },
  heroPrimaryCta: {
    backgroundColor: "#0F766E",
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    alignSelf: "flex-start",
  },
  heroPrimaryCtaText: {
    color: "#FFFFFF",
    fontSize: 13,
    fontWeight: "700",
  },
  contextRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 16,
  },
  contextPill: {
    backgroundColor: "#F3F4F6",
    color: "#374151",
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    fontSize: 12,
    fontWeight: "600",
  },
  contextPillActive: {
    backgroundColor: "#DBEAFE",
    color: "#1D4ED8",
  },
  summaryRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
    marginBottom: 16,
  },
  tileCenter: {
    alignItems: "center",
  },
  tileNumber: {
    fontSize: 24,
    fontWeight: "700",
  },
  tileLabel: {
    fontSize: 12,
    color: "#6B7280",
  },
  sectionHeader: {
    marginBottom: 12,
    gap: 4,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  sectionSubtitle: {
    fontSize: 12,
    color: "#6B7280",
    lineHeight: 18,
  },
  launchGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  launchCard: {
    flexBasis: "47%",
    backgroundColor: "#F9FAFB",
    borderWidth: 1,
    borderColor: "#E5E7EB",
    borderRadius: 14,
    padding: 14,
    minHeight: 96,
  },
  launchCardActive: {
    backgroundColor: "#ECFDF5",
    borderColor: "#A7F3D0",
  },
  launchTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 6,
  },
  launchSubtitle: {
    fontSize: 12,
    color: "#6B7280",
    lineHeight: 18,
  },
  taskHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  bold: {
    fontWeight: "bold",
  },
  patientName: {
    fontSize: 14,
    color: "#6B7280",
    marginVertical: 4,
  },
  taskMeta: {
    flexDirection: "row",
    gap: 8,
    marginTop: 4,
  },
  dueDate: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  encounterTime: {
    fontSize: 14,
    color: "#6B7280",
  },
  chiefComplaint: {
    fontSize: 14,
  },
  refreshContainer: {
    marginTop: 16,
  },
});
