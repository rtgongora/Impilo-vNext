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
import { getMyTasks } from "../../services/taskService";
import { listEncounters } from "../../services/encounterService";
import type { Task, Encounter } from "../../types";

const PRIORITY_VARIANT: Record<string, string> = {
  URGENT: "destructive",
  HIGH: "warning",
  MEDIUM: "secondary",
  LOW: "outline",
};

export function ProviderDashboardScreen() {
  const { facilityId } = useAppStore();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [openEncounters, setOpenEncounters] = useState<Encounter[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const [taskResult, encounterResult] = await Promise.all([
        getMyTasks(undefined, 0, 50),
        listEncounters(undefined, 0, 20),
      ]);
      setTasks(taskResult.tasks);
      setOpenEncounters(
        encounterResult.encounters.filter((e) => e.status === "IN_PROGRESS")
      );
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

  return (
    <Screen>
      <Header title="Worklist" />
      <ScrollView testID="provider-dashboard" style={styles.container}>
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
                <Text style={styles.tileNumber}>{String(openEncounters.length)}</Text>
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
                    { color: overdueTasks.length > 0 ? "#DC2626" : "#111827" },
                  ]}
                >
                  {String(overdueTasks.length)}
                </Text>
                <Text style={styles.tileLabel}>Overdue</Text>
              </View>
            </CardBody>
          </Card>
        </View>

        {/* Task list */}
        <CardHeader>My Tasks</CardHeader>
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
            <CardHeader>Open Encounters</CardHeader>
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
  summaryRow: {
    flexDirection: "row",
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
