/**
 * QueueStatusSection — Real-time queue position tracking.
 */
import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchQueueStatus } from "../../services/queueStatusService";
import { useAppStore } from "../../stores/appStore";

const STATUS_COLORS: Record<string, string> = { WAITING: "#F59E0B", CALLED: "#22C55E", COMPLETED: "#6B7280", CANCELLED: "#EF4444" };

export function QueueStatusSection() {
  const profile = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";

  const { data: tickets = [], isLoading } = useQuery({
    queryKey: ["queue-status", patientId], queryFn: () => fetchQueueStatus(patientId), enabled: !!patientId, refetchInterval: 15000,
  });

  if (isLoading) return <LoadingSpinner />;

  if (tickets.length === 0) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyTitle}>No Active Queue</Text>
        <Text style={styles.emptyText}>You are not currently in any queue. Join remotely from the Marketplace or check in at a facility.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {tickets.map((t) => (
        <View key={t.id} style={[styles.ticket, { borderLeftColor: STATUS_COLORS[t.status] ?? "#6B7280" }]}>
          <View style={styles.ticketHeader}>
            <Text style={styles.ticketNumber}>{t.ticketNumber}</Text>
            <Badge label={t.status} variant={t.status === "CALLED" ? "success" : t.status === "WAITING" ? "warning" : "default"} />
          </View>
          <Text style={styles.facilityName}>{t.facilityName}</Text>
          <Text style={styles.serviceType}>{t.serviceType}</Text>
          {t.position != null && t.status === "WAITING" && (
            <View style={styles.positionRow}>
              <Text style={styles.positionLabel}>Position in queue:</Text>
              <Text style={styles.positionValue}>{t.position}</Text>
            </View>
          )}
          {t.estimatedWait != null && t.status === "WAITING" && (
            <Text style={styles.waitTime}>Estimated wait: ~{t.estimatedWait} min</Text>
          )}
          <Text style={styles.joinedAt}>Joined: {new Date(t.joinedAt).toLocaleTimeString()}</Text>
          {t.calledAt && <Text style={styles.calledAt}>Called: {new Date(t.calledAt).toLocaleTimeString()}</Text>}
        </View>
      ))}
      <Text style={styles.refreshNote}>Auto-refreshing every 15 seconds</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  empty: { alignItems: "center", paddingVertical: 40 },
  emptyTitle: { fontSize: 18, fontWeight: "600", color: "#374151" },
  emptyText: { fontSize: 14, color: "#9CA3AF", textAlign: "center", marginTop: 8 },
  ticket: { backgroundColor: "#F9FAFB", borderRadius: 12, padding: 16, borderLeftWidth: 4, gap: 4 },
  ticketHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  ticketNumber: { fontSize: 24, fontWeight: "900", color: "#111827" },
  facilityName: { fontSize: 15, fontWeight: "600", color: "#374151" },
  serviceType: { fontSize: 13, color: "#6B7280" },
  positionRow: { flexDirection: "row", alignItems: "center", gap: 8, marginTop: 8 },
  positionLabel: { fontSize: 13, color: "#6B7280" },
  positionValue: { fontSize: 28, fontWeight: "900", color: "#2563EB" },
  waitTime: { fontSize: 13, color: "#F59E0B", fontWeight: "600" },
  joinedAt: { fontSize: 11, color: "#9CA3AF" },
  calledAt: { fontSize: 11, color: "#22C55E" },
  refreshNote: { fontSize: 11, color: "#D1D5DB", textAlign: "center" },
});
