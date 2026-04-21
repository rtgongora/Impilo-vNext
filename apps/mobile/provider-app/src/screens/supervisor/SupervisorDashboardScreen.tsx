import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import {
  Screen,
  Header,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { getFacilityMetrics } from "../../services/supportService";
import { useAppStore } from "../../stores/appStore";
import type { FacilityMetrics } from "../../types";

type TileConfig = {
  label: string;
  value: number;
  color: string;
  bgColor: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
};

export function SupervisorDashboardScreen() {
  const { facilityId } = useAppStore();
  const [metrics, setMetrics] = useState<FacilityMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadMetrics = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const m = await getFacilityMetrics(facilityId);
      setMetrics(m);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load metrics");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    loadMetrics();
  }, [loadMetrics]);

  if (loading) {
    return (
      <Screen>
        <Header title="Dashboard" />
        <View style={styles.centerContainer}>
          <LoadingSpinner size="lg" />
        </View>
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Dashboard" />
        <ErrorState title="Error" message={error} onRetry={loadMetrics} />
      </Screen>
    );
  }

  if (!metrics) return null;

  const tiles: TileConfig[] = [
    { label: "Patients Seen", value: metrics.patientsSeenToday, color: "#3B82F6", bgColor: "#DBEAFE", icon: "people-outline" },
    { label: "Open Encounters", value: metrics.encountersOpen, color: "#F59E0B", bgColor: "#FEF3C7", icon: "folder-open-outline" },
    { label: "Closed Encounters", value: metrics.encountersClosed, color: "#059669", bgColor: "#D1FAE5", icon: "checkmark-done-outline" },
    { label: "Avg Wait (min)", value: metrics.averageWaitTimeMinutes, color: "#6366F1", bgColor: "#EDE9FE", icon: "time-outline" },
    { label: "Pending Tasks", value: metrics.pendingTasks, color: "#8B5CF6", bgColor: "#F3E8FF", icon: "list-outline" },
    { label: "Overdue Escalations", value: metrics.overdueEscalations, color: metrics.overdueEscalations > 0 ? "#DC2626" : "#6B7280", bgColor: metrics.overdueEscalations > 0 ? "#FEE2E2" : "#F3F4F6", icon: "alert-circle-outline" },
    { label: "Stock Alerts", value: metrics.stockAlerts, color: metrics.stockAlerts > 0 ? "#DC2626" : "#6B7280", bgColor: metrics.stockAlerts > 0 ? "#FEE2E2" : "#F3F4F6", icon: "cube-outline" },
  ];

  return (
    <Screen>
      <Header title="Dashboard" />
      <ScrollView testID="supervisor-dashboard" style={styles.container} contentContainerStyle={styles.contentContainer}>
        <View style={styles.heroBanner}>
          <View style={styles.heroLeft}>
            <View style={styles.heroIconCircle}>
              <Ionicons name="bar-chart" size={36} color="#FFFFFF" />
            </View>
            <View style={styles.heroText}>
              <Text style={styles.heroLabel}>Supervisor Dashboard</Text>
              <Text style={styles.heroFacility}>{metrics.facilityName}</Text>
              <Text style={styles.heroDate}>{metrics.date}</Text>
            </View>
          </View>
        </View>

        <View style={styles.tilesGrid}>
          {tiles.map((tile) => (
            <View key={tile.label} style={styles.tileCard}>
              <View style={[styles.tileIconCircle, { backgroundColor: tile.bgColor }]}>
                <Ionicons name={tile.icon} size={22} color={tile.color} />
              </View>
              <Text style={[styles.tileValue, { color: tile.color }]}>
                {String(tile.value)}
              </Text>
              <Text style={styles.tileLabel}>{tile.label}</Text>
            </View>
          ))}
        </View>

        <TouchableOpacity style={styles.refreshButton} onPress={loadMetrics} testID="refresh-metrics">
          <Ionicons name="refresh-outline" size={16} color="#7C3AED" />
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
    backgroundColor: "#7C3AED",
    borderRadius: 20,
    padding: 22,
    marginBottom: 20,
    flexDirection: "row",
    alignItems: "center",
    shadowColor: "#7C3AED",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 12,
    elevation: 6,
  },
  heroLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 16,
  },
  heroIconCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: "rgba(255,255,255,0.2)",
    alignItems: "center",
    justifyContent: "center",
  },
  heroText: {
    gap: 2,
  },
  heroLabel: {
    fontSize: 18,
    fontWeight: "800",
    color: "#FFFFFF",
    letterSpacing: 0.2,
  },
  heroFacility: {
    fontSize: 14,
    color: "rgba(255,255,255,0.9)",
    fontWeight: "600",
  },
  heroDate: {
    fontSize: 12,
    color: "rgba(255,255,255,0.7)",
    marginTop: 2,
  },
  tilesGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
    marginBottom: 16,
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
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 2,
  },
  tileValue: {
    fontSize: 28,
    fontWeight: "700",
  },
  tileLabel: {
    fontSize: 11,
    color: "#6B7280",
    fontWeight: "500",
    textAlign: "center",
  },
  refreshButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    borderWidth: 1.5,
    borderColor: "#7C3AED",
    borderRadius: 12,
    paddingVertical: 12,
    marginTop: 8,
    backgroundColor: "#F5F3FF",
  },
  refreshButtonText: {
    color: "#7C3AED",
    fontWeight: "600",
    fontSize: 14,
  },
});
