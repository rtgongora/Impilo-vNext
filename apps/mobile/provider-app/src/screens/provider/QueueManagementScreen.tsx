/**
 * QueueManagementScreen — Real-time queue view with call-next, stats, and completion.
 */
import React, { useState, useCallback } from "react";
import { View, Text, FlatList, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Screen, Header, Card, CardBody, Button, Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchQueue, callNext, completeEntry, fetchQueueStats } from "../../services/queueService";

export function QueueManagementScreen() {
  const queryClient = useQueryClient();
  const { data: queue = [], isLoading } = useQuery({ queryKey: ["provider-queue"], queryFn: fetchQueue, refetchInterval: 10000 });
  const { data: stats } = useQuery({ queryKey: ["queue-stats"], queryFn: fetchQueueStats, refetchInterval: 10000 });

  const callMutation = useMutation({ mutationFn: () => callNext("default"), onSuccess: () => queryClient.invalidateQueries({ queryKey: ["provider-queue"] }) });
  const completeMutation = useMutation({ mutationFn: (id: string) => completeEntry(id), onSuccess: () => queryClient.invalidateQueries({ queryKey: ["provider-queue"] }) });

  const priorityColors: Record<string, string> = { URGENT: "#DC2626", HIGH: "#F59E0B", NORMAL: "#3B82F6", LOW: "#6B7280" };

  return (
    <Screen>
      <Header title="Queue Management" />
      <View style={styles.container}>
        {stats && (
          <View style={styles.statsRow}>
            <View style={styles.statCard}><Text style={styles.statNum}>{stats.waiting}</Text><Text style={styles.statLabel}>Waiting</Text></View>
            <View style={styles.statCard}><Text style={styles.statNum}>{stats.inProgress}</Text><Text style={styles.statLabel}>In Progress</Text></View>
            <View style={styles.statCard}><Text style={styles.statNum}>{stats.completedToday}</Text><Text style={styles.statLabel}>Today</Text></View>
          </View>
        )}
        <Button label="Call Next Patient" onPress={() => callMutation.mutate()} disabled={callMutation.isPending} />
        {isLoading ? <LoadingSpinner /> : (
          <FlatList
            data={queue as Array<Record<string, unknown>>}
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => (
              <View style={[styles.queueItem, { borderLeftColor: priorityColors[String(item.priority)] ?? "#6B7280" }]}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.patientName}>{String(item.patient_name ?? "Patient")}</Text>
                  <Text style={styles.meta}>{String(item.service_point ?? "")} · {String(item.status)}</Text>
                </View>
                {item.status === "IN_PROGRESS" && (
                  <TouchableOpacity style={styles.completeBtn} onPress={() => completeMutation.mutate(String(item.id))}>
                    <Text style={styles.completeBtnText}>Complete</Text>
                  </TouchableOpacity>
                )}
              </View>
            )}
          />
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 12 },
  statsRow: { flexDirection: "row", gap: 8 },
  statCard: { flex: 1, backgroundColor: "#F9FAFB", borderRadius: 12, padding: 12, alignItems: "center" },
  statNum: { fontSize: 24, fontWeight: "700", color: "#111827" },
  statLabel: { fontSize: 11, color: "#6B7280" },
  queueItem: { flexDirection: "row", alignItems: "center", backgroundColor: "#FFF", borderRadius: 8, padding: 12, marginBottom: 8, borderLeftWidth: 4 },
  patientName: { fontSize: 14, fontWeight: "600", color: "#111827" },
  meta: { fontSize: 12, color: "#6B7280" },
  completeBtn: { backgroundColor: "#22C55E", paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6 },
  completeBtnText: { color: "#FFF", fontSize: 12, fontWeight: "600" },
});
