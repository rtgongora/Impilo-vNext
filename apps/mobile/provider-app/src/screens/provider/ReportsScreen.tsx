/**
 * ReportsScreen — Daily summary analytics and metrics.
 */
import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Screen, Header, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchReportSummary } from "../../services/queueService";

export function ReportsScreen() {
  const { data: summary, isLoading } = useQuery({ queryKey: ["reports-summary"], queryFn: fetchReportSummary });

  if (isLoading) return <Screen><Header title="Reports" /><LoadingSpinner /></Screen>;

  const metrics = [
    { label: "Encounters Today", value: summary?.encountersToday ?? 0, color: "#3B82F6" },
    { label: "Prescriptions Today", value: summary?.prescriptionsToday ?? 0, color: "#22C55E" },
    { label: "Lab Orders Today", value: summary?.labOrdersToday ?? 0, color: "#F59E0B" },
  ];

  return (
    <Screen><Header title="Reports & Analytics" />
      <View style={styles.container}>
        <Text style={styles.title}>Today's Summary</Text>
        <View style={styles.grid}>
          {metrics.map((m) => (
            <View key={m.label} style={[styles.card, { borderLeftColor: m.color }]}>
              <Text style={[styles.value, { color: m.color }]}>{m.value}</Text>
              <Text style={styles.label}>{m.label}</Text>
            </View>
          ))}
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 16 },
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  grid: { gap: 12 },
  card: { backgroundColor: "#FFF", borderRadius: 12, padding: 20, borderLeftWidth: 4 },
  value: { fontSize: 36, fontWeight: "900" },
  label: { fontSize: 13, color: "#6B7280", marginTop: 4 },
});
