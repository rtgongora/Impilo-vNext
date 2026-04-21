import React from "react";
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header, LoadingSpinner } from "@impilo/mobile-design-system";
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
            <View style={styles.statCard}>
              <View style={[styles.statIconCircle, { backgroundColor: "#DBEAFE" }]}>
                <Ionicons name="hourglass-outline" size={20} color="#3B82F6" />
              </View>
              <Text style={[styles.statNum, { color: "#3B82F6" }]}>{stats.waiting}</Text>
              <Text style={styles.statLabel}>Waiting</Text>
            </View>
            <View style={styles.statCard}>
              <View style={[styles.statIconCircle, { backgroundColor: "#FEF3C7" }]}>
                <Ionicons name="pulse" size={20} color="#F59E0B" />
              </View>
              <Text style={[styles.statNum, { color: "#F59E0B" }]}>{stats.inProgress}</Text>
              <Text style={styles.statLabel}>In Progress</Text>
            </View>
            <View style={styles.statCard}>
              <View style={[styles.statIconCircle, { backgroundColor: "#D1FAE5" }]}>
                <Ionicons name="checkmark-circle" size={20} color="#059669" />
              </View>
              <Text style={[styles.statNum, { color: "#059669" }]}>{stats.completedToday}</Text>
              <Text style={styles.statLabel}>Today</Text>
            </View>
          </View>
        )}

        <TouchableOpacity
          style={[styles.callNextButton, callMutation.isPending && styles.callNextButtonDisabled]}
          onPress={() => callMutation.mutate()}
          disabled={callMutation.isPending}
        >
          {callMutation.isPending ? (
            <LoadingSpinner size="sm" />
          ) : (
            <>
              <Text style={styles.callNextButtonText}>Call Next Patient</Text>
              <Ionicons name="chevron-forward" size={22} color="#FFFFFF" />
            </>
          )}
        </TouchableOpacity>

        {isLoading ? (
          <View style={styles.centerLoader}>
            <LoadingSpinner size="md" />
          </View>
        ) : (
          <FlatList
            data={queue as Array<Record<string, unknown>>}
            keyExtractor={(item) => String(item.id)}
            showsVerticalScrollIndicator={false}
            renderItem={({ item }) => (
              <View style={[styles.queueItem, { borderLeftColor: priorityColors[String(item.priority)] ?? "#6B7280" }]}>
                <View style={styles.queueItemLeft}>
                  <View style={[styles.priorityDot, { backgroundColor: priorityColors[String(item.priority)] ?? "#6B7280" }]} />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.patientName}>{String(item.patient_name ?? "Patient")}</Text>
                    <View style={styles.metaRow}>
                      <Ionicons name="location-outline" size={11} color="#9CA3AF" />
                      <Text style={styles.meta}>{String(item.service_point ?? "")}</Text>
                      <Text style={styles.metaDot}>·</Text>
                      <Text style={styles.meta}>{String(item.status)}</Text>
                    </View>
                  </View>
                </View>
                {item.status === "IN_PROGRESS" && (
                  <TouchableOpacity
                    style={[styles.completeBtn, completeMutation.isPending && styles.completeBtnDisabled]}
                    onPress={() => completeMutation.mutate(String(item.id))}
                    disabled={completeMutation.isPending}
                  >
                    <Ionicons name="checkmark-outline" size={14} color="#FFFFFF" />
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
  container: {
    flex: 1,
    padding: 16,
    gap: 14,
    backgroundColor: "#F8FAFC",
  },
  statsRow: {
    flexDirection: "row",
    gap: 10,
  },
  statCard: {
    flex: 1,
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 14,
    alignItems: "center",
    gap: 4,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
  },
  statIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 4,
  },
  statNum: {
    fontSize: 26,
    fontWeight: "700",
  },
  statLabel: {
    fontSize: 11,
    color: "#6B7280",
    fontWeight: "500",
  },
  callNextButton: {
    backgroundColor: "#1E40AF",
    borderRadius: 16,
    paddingVertical: 16,
    paddingHorizontal: 20,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    shadowColor: "#1E40AF",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 4,
  },
  callNextButtonDisabled: {
    opacity: 0.6,
    shadowOpacity: 0,
  },
  callNextButtonText: {
    color: "#FFFFFF",
    fontSize: 16,
    fontWeight: "700",
    letterSpacing: 0.3,
  },
  centerLoader: {
    alignItems: "center",
    paddingVertical: 40,
  },
  queueItem: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    borderLeftWidth: 4,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 1,
  },
  queueItemLeft: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  priorityDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  patientName: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 3,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  meta: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  metaDot: {
    fontSize: 12,
    color: "#D1D5DB",
  },
  completeBtn: {
    backgroundColor: "#059669",
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 10,
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  completeBtnDisabled: {
    opacity: 0.6,
  },
  completeBtnText: {
    color: "#FFFFFF",
    fontSize: 12,
    fontWeight: "600",
  },
});
