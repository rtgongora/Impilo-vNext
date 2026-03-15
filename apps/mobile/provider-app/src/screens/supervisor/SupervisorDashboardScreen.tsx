/**
 * SupervisorDashboardScreen — Facility operations dashboard with KPI tiles.
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
import { getFacilityMetrics } from "../../services/supportService";
import { useAppStore } from "../../stores/appStore";
import type { FacilityMetrics } from "../../types";

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
        <LoadingSpinner size="lg" />
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

  const tiles = [
    { label: "Patients Seen", value: metrics.patientsSeenToday, color: "#3B82F6" },
    { label: "Open Encounters", value: metrics.encountersOpen, color: "#F59E0B" },
    { label: "Closed Encounters", value: metrics.encountersClosed, color: "#10B981" },
    { label: "Avg Wait (min)", value: metrics.averageWaitTimeMinutes, color: "#6366F1" },
    { label: "Pending Tasks", value: metrics.pendingTasks, color: "#8B5CF6" },
    { label: "Overdue Escalations", value: metrics.overdueEscalations, color: metrics.overdueEscalations > 0 ? "#DC2626" : "#6B7280" },
    { label: "Stock Alerts", value: metrics.stockAlerts, color: metrics.stockAlerts > 0 ? "#DC2626" : "#6B7280" },
  ];

  return (
    <Screen>
      <Header title={`${metrics.facilityName} — Dashboard`} />
      <ScrollView testID="supervisor-dashboard" style={styles.container}>
        <Text style={styles.dateText}>{`Date: ${metrics.date}`}</Text>
        <View style={styles.tilesGrid}>
          {tiles.map((tile) => (
            <Card key={tile.label}>
              <CardBody>
                <View style={styles.tileCenter}>
                  <Text style={[styles.tileValue, { color: tile.color }]}>
                    {String(tile.value)}
                  </Text>
                  <Text style={styles.tileLabel}>{tile.label}</Text>
                </View>
              </CardBody>
            </Card>
          ))}
        </View>
        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={loadMetrics} testID="refresh-metrics" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  dateText: {
    fontSize: 14,
    color: "#6B7280",
    marginBottom: 12,
  },
  tilesGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
  },
  tileCenter: {
    alignItems: "center",
  },
  tileValue: {
    fontSize: 28,
    fontWeight: "700",
  },
  tileLabel: {
    fontSize: 12,
    color: "#6B7280",
  },
  refreshContainer: {
    marginTop: 16,
  },
});
