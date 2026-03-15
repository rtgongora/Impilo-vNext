/**
 * OutreachDashboardScreen — Outreach mode home with daily stats and GPS tracking.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Button,
  Badge,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { getHouseholds } from "../../services/householdService";
import { getMyTasks } from "../../services/taskService";
import { useSyncEngine } from "@impilo/mobile-offline";
import type { Household, Task } from "../../types";

export function OutreachDashboardScreen() {
  const { facilityId, isOnline } = useAppStore();
  const { status: syncStatus, pendingCount } = useSyncEngine();
  const [households, setHouseholds] = useState<Household[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const [hhResult, taskResult] = await Promise.all([
        getHouseholds(facilityId, 0, 100),
        getMyTasks(undefined, 0, 50),
      ]);
      setHouseholds(hhResult.households);
      setTasks(taskResult.tasks.filter((t) => t.taskType === "OUTREACH" || t.taskType === "COMMUNITY_VISIT"));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load outreach data");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return (
      <Screen>
        <Header title="Outreach" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Outreach" />
        <ErrorState title="Error" message={error} onRetry={loadData} />
      </Screen>
    );
  }

  const overdueVisits = households.filter(
    (h) => h.nextVisitDate && new Date(h.nextVisitDate) < new Date()
  );
  const highRisk = households.filter((h) => h.riskCategory === "HIGH");
  const todayTasks = tasks.filter((t) => t.status === "PENDING" || t.status === "IN_PROGRESS");

  return (
    <Screen>
      <Header title="Outreach Dashboard" />
      <ScrollView testID="outreach-dashboard" style={styles.container}>
        {/* Sync status */}
        {(!isOnline || pendingCount > 0) && (
          <Card>
            <CardBody>
              <View style={styles.syncRow}>
                <Badge
                  variant={isOnline ? "secondary" : "destructive"}
                >
                  {isOnline ? "Online" : "Offline"}
                </Badge>
                {pendingCount > 0 && (
                  <Text style={styles.syncText}>{`${pendingCount} items pending sync`}</Text>
                )}
              </View>
            </CardBody>
          </Card>
        )}

        {/* Summary tiles */}
        <View style={styles.tilesGrid}>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text style={styles.tileValue}>{String(households.length)}</Text>
                <Text style={styles.tileLabel}>Households</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text style={styles.tileValue}>{String(todayTasks.length)}</Text>
                <Text style={styles.tileLabel}>Today's Tasks</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text
                  style={[
                    styles.tileValue,
                    { color: overdueVisits.length > 0 ? "#DC2626" : "#111827" },
                  ]}
                >
                  {String(overdueVisits.length)}
                </Text>
                <Text style={styles.tileLabel}>Overdue Visits</Text>
              </View>
            </CardBody>
          </Card>
          <Card>
            <CardBody>
              <View style={styles.tileCenter}>
                <Text
                  style={[
                    styles.tileValue,
                    { color: highRisk.length > 0 ? "#F59E0B" : "#111827" },
                  ]}
                >
                  {String(highRisk.length)}
                </Text>
                <Text style={styles.tileLabel}>High Risk</Text>
              </View>
            </CardBody>
          </Card>
        </View>

        {/* Today's schedule */}
        <CardHeader>Today's Schedule</CardHeader>
        {todayTasks.length === 0 ? (
          <Text style={styles.emptySchedule}>No outreach tasks for today</Text>
        ) : (
          todayTasks.map((task) => (
            <Card key={task.id}>
              <CardBody>
                <View testID={`outreach-task-${task.id}`}>
                  <Text style={styles.taskTitle}>{task.title}</Text>
                  {task.patientName && (
                    <Text style={styles.taskPatient}>{task.patientName}</Text>
                  )}
                  <Badge variant="outline">{task.priority}</Badge>
                </View>
              </CardBody>
            </Card>
          ))
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={loadData} testID="refresh-outreach" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  syncRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  syncText: {
    fontSize: 14,
  },
  tilesGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
    marginTop: 12,
  },
  tileCenter: {
    alignItems: "center",
  },
  tileValue: {
    fontSize: 24,
    fontWeight: "700",
  },
  tileLabel: {
    fontSize: 12,
    color: "#6B7280",
  },
  emptySchedule: {
    color: "#6B7280",
    padding: 8,
  },
  taskTitle: {
    fontWeight: "700",
  },
  taskPatient: {
    fontSize: 14,
    color: "#6B7280",
  },
  refreshContainer: {
    marginTop: 16,
  },
});
