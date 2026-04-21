import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  Card,
  CardBody,
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

const PRIORITY_COLORS: Record<string, string> = {
  URGENT: "#DC2626",
  HIGH: "#F59E0B",
  NORMAL: "#3B82F6",
  LOW: "#6B7280",
};

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
        <View style={styles.centerContainer}>
          <LoadingSpinner size="lg" />
        </View>
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
      <ScrollView testID="outreach-dashboard" style={styles.container} contentContainerStyle={styles.contentContainer}>
        <View style={styles.heroBanner}>
          <View style={styles.heroLeft}>
            <View style={styles.heroIconCircle}>
              <Ionicons name="map-outline" size={32} color="#FFFFFF" />
            </View>
            <View style={styles.heroText}>
              <Text style={styles.heroTitle}>Outreach Mode</Text>
              <Text style={styles.heroDate}>{new Date().toLocaleDateString("en-GB", { weekday: "long", day: "numeric", month: "long" })}</Text>
            </View>
          </View>
          {(!isOnline || pendingCount > 0) && (
            <View style={styles.syncChip}>
              <Ionicons
                name={isOnline ? "cloud-upload-outline" : "cloud-offline-outline"}
                size={14}
                color={isOnline ? "#FEF3C7" : "#FEE2E2"}
              />
              <Text style={[styles.syncChipText, { color: isOnline ? "#FEF3C7" : "#FEE2E2" }]}>
                {isOnline ? `${pendingCount} pending` : "Offline"}
              </Text>
            </View>
          )}
        </View>

        <View style={styles.tilesGrid}>
          <View style={styles.tileCard}>
            <View style={[styles.tileIconCircle, { backgroundColor: "#DBEAFE" }]}>
              <Ionicons name="home-outline" size={20} color="#3B82F6" />
            </View>
            <Text style={[styles.tileValue, { color: "#3B82F6" }]}>{String(households.length)}</Text>
            <Text style={styles.tileLabel}>Households</Text>
          </View>
          <View style={styles.tileCard}>
            <View style={[styles.tileIconCircle, { backgroundColor: "#EDE9FE" }]}>
              <Ionicons name="list-outline" size={20} color="#6366F1" />
            </View>
            <Text style={[styles.tileValue, { color: "#6366F1" }]}>{String(todayTasks.length)}</Text>
            <Text style={styles.tileLabel}>Today's Tasks</Text>
          </View>
          <View style={styles.tileCard}>
            <View style={[styles.tileIconCircle, { backgroundColor: overdueVisits.length > 0 ? "#FEE2E2" : "#F3F4F6" }]}>
              <Ionicons name="time-outline" size={20} color={overdueVisits.length > 0 ? "#DC2626" : "#9CA3AF"} />
            </View>
            <Text style={[styles.tileValue, { color: overdueVisits.length > 0 ? "#DC2626" : "#111827" }]}>{String(overdueVisits.length)}</Text>
            <Text style={styles.tileLabel}>Overdue Visits</Text>
          </View>
          <View style={styles.tileCard}>
            <View style={[styles.tileIconCircle, { backgroundColor: highRisk.length > 0 ? "#FEF3C7" : "#F3F4F6" }]}>
              <Ionicons name="warning-outline" size={20} color={highRisk.length > 0 ? "#F59E0B" : "#9CA3AF"} />
            </View>
            <Text style={[styles.tileValue, { color: highRisk.length > 0 ? "#F59E0B" : "#111827" }]}>{String(highRisk.length)}</Text>
            <Text style={styles.tileLabel}>High Risk</Text>
          </View>
        </View>

        <View style={styles.sectionHeader}>
          <Ionicons name="calendar-outline" size={16} color="#374151" />
          <Text style={styles.sectionHeaderText}>Today's Schedule</Text>
        </View>

        {todayTasks.length === 0 ? (
          <View style={styles.emptySchedule}>
            <Ionicons name="checkmark-circle-outline" size={32} color="#D1D5DB" />
            <Text style={styles.emptyScheduleText}>No outreach tasks for today</Text>
          </View>
        ) : (
          todayTasks.map((task) => (
            <View
              key={task.id}
              style={[styles.taskCard, { borderLeftColor: PRIORITY_COLORS[task.priority] ?? "#6B7280" }]}
              testID={`outreach-task-${task.id}`}
            >
              <View style={styles.taskCardInner}>
                <View style={[styles.taskDot, { backgroundColor: PRIORITY_COLORS[task.priority] ?? "#6B7280" }]} />
                <View style={styles.taskContent}>
                  <Text style={styles.taskTitle}>{task.title}</Text>
                  {task.patientName && (
                    <View style={styles.taskPatientRow}>
                      <Ionicons name="person-outline" size={11} color="#9CA3AF" />
                      <Text style={styles.taskPatient}>{task.patientName}</Text>
                    </View>
                  )}
                </View>
                <Badge variant="outline">{task.priority}</Badge>
              </View>
            </View>
          ))
        )}

        <TouchableOpacity style={styles.refreshButton} onPress={loadData} testID="refresh-outreach">
          <Ionicons name="refresh-outline" size={16} color="#1E40AF" />
          <Text style={styles.refreshButtonText}>Refresh</Text>
        </TouchableOpacity>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 32,
  },
  centerContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  heroBanner: {
    backgroundColor: "#D97706",
    borderRadius: 20,
    padding: 20,
    marginBottom: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    shadowColor: "#D97706",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 10,
    elevation: 5,
  },
  heroLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
  },
  heroIconCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: "rgba(255,255,255,0.2)",
    alignItems: "center",
    justifyContent: "center",
  },
  heroText: {
    gap: 3,
  },
  heroTitle: {
    fontSize: 20,
    fontWeight: "800",
    color: "#FFFFFF",
    letterSpacing: 0.3,
  },
  heroDate: {
    fontSize: 13,
    color: "rgba(255,255,255,0.85)",
    fontWeight: "500",
  },
  syncChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: "rgba(0,0,0,0.15)",
    borderRadius: 20,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  syncChipText: {
    fontSize: 11,
    fontWeight: "600",
  },
  tilesGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
    marginBottom: 20,
  },
  tileCard: {
    width: "47%",
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    alignItems: "center",
    gap: 6,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
  },
  tileIconCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 2,
  },
  tileValue: {
    fontSize: 26,
    fontWeight: "700",
  },
  tileLabel: {
    fontSize: 12,
    color: "#6B7280",
    fontWeight: "500",
  },
  sectionHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 12,
  },
  sectionHeaderText: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  emptySchedule: {
    alignItems: "center",
    paddingVertical: 32,
    gap: 8,
  },
  emptyScheduleText: {
    color: "#9CA3AF",
    fontSize: 14,
  },
  taskCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    marginBottom: 10,
    borderLeftWidth: 4,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 1,
  },
  taskCardInner: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    gap: 10,
  },
  taskDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  taskContent: {
    flex: 1,
    gap: 3,
  },
  taskTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  taskPatientRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  taskPatient: {
    fontSize: 12,
    color: "#6B7280",
  },
  refreshButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    borderWidth: 1.5,
    borderColor: "#1E40AF",
    borderRadius: 12,
    paddingVertical: 12,
    marginTop: 16,
    backgroundColor: "#EFF6FF",
  },
  refreshButtonText: {
    color: "#1E40AF",
    fontWeight: "600",
    fontSize: 14,
  },
});
