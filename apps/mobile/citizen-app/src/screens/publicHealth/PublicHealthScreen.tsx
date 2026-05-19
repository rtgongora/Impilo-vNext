import React, { useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Card, CardBody, CardHeader, LoadingSpinner, Screen } from "@impilo/mobile-design-system";
import {
  fetchCitizenPublicHealthAlerts,
  fetchCitizenPublicHealthSummary,
  type CitizenPublicHealthAlert,
  type CitizenPublicHealthSummary,
} from "../../services/publicHealthService";

const ACCENT = "#0EA5E9";

export function PublicHealthScreen() {
  const [summary, setSummary] = useState<CitizenPublicHealthSummary | null>(null);
  const [alerts, setAlerts] = useState<CitizenPublicHealthAlert[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([fetchCitizenPublicHealthSummary(), fetchCitizenPublicHealthAlerts()])
      .then(([s, a]) => {
        setSummary(s);
        setAlerts(a);
      })
      .finally(() => setLoading(false));
  }, []);

  const sortedKpis = Object.entries(summary?.kpis ?? {}).sort((a, b) => b[1] - a[1]);

  return (
    <Screen>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.header}>
          <Ionicons name="pulse" size={22} color={ACCENT} />
          <Text style={styles.title}>Public Health</Text>
        </View>
        <Text style={styles.subtitle}>Community updates and outbreak awareness in your area.</Text>

        {loading ? (
          <View style={styles.loadingWrap}>
            <LoadingSpinner size="sm" />
          </View>
        ) : (
          <>
            <Card variant="elevated" padding="md">
              <CardHeader title="Operational snapshot" />
              <CardBody>
                {sortedKpis.length === 0 ? (
                  <Text style={styles.muted}>No public-health metrics available yet.</Text>
                ) : (
                  sortedKpis.slice(0, 4).map(([label, value]) => (
                    <View key={label} style={styles.row}>
                      <Text style={styles.metricLabel}>{label.replace(/_/g, " ")}</Text>
                      <Text style={styles.metricValue}>{value}</Text>
                    </View>
                  ))
                )}
              </CardBody>
            </Card>

            <Card variant="elevated" padding="md">
              <CardHeader title="Current alerts" />
              <CardBody>
                {alerts.length === 0 ? (
                  <Text style={styles.muted}>No active alerts.</Text>
                ) : (
                  alerts.slice(0, 5).map((alert) => (
                    <View key={alert.id} style={styles.alertRow}>
                      <View>
                        <Text style={styles.metricLabel}>{alert.syndromeCode}</Text>
                        <Text style={styles.muted}>Severity: {alert.severity}</Text>
                      </View>
                      <Text style={styles.badge}>{alert.acknowledged ? "Seen" : "New"}</Text>
                    </View>
                  ))
                )}
              </CardBody>
            </Card>
          </>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 12, paddingBottom: 32 },
  header: { flexDirection: "row", alignItems: "center", gap: 8 },
  title: { fontSize: 18, fontWeight: "700", color: "#0F172A" },
  subtitle: { color: "#64748B", fontSize: 12 },
  loadingWrap: { paddingVertical: 24, alignItems: "center" },
  row: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 6 },
  metricLabel: { color: "#0F172A", fontWeight: "600", textTransform: "capitalize" },
  metricValue: { color: ACCENT, fontWeight: "700" },
  muted: { color: "#64748B", fontSize: 12 },
  alertRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 8,
    borderBottomColor: "#E2E8F0",
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  badge: {
    fontSize: 11,
    fontWeight: "700",
    color: "#0369A1",
    backgroundColor: "#E0F2FE",
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
  },
});
