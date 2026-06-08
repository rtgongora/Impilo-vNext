/**
 * Native field task board for public-health / outreach operators.
 */
import React from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, RefreshControl } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Screen, Header, LoadingSpinner } from "@impilo/mobile-design-system";
import { MobileNearbyMapView } from "@impilo/mobile-ndila";
import {
  fetchFieldTasks,
  fetchOperationsHomeKpis,
  nextFieldTaskStatus,
  transitionFieldTask,
  type FieldTaskRow,
} from "../../services/publicHealthService";

export function PublicHealthFieldTasksScreen() {
  const queryClient = useQueryClient();
  const tasksQ = useQuery({ queryKey: ["ph-field-tasks"], queryFn: fetchFieldTasks });
  const homeQ = useQuery({ queryKey: ["ph-operations-home-mobile"], queryFn: fetchOperationsHomeKpis });

  const transitionM = useMutation({
    mutationFn: ({ taskId, status }: { taskId: string; status: string }) => transitionFieldTask(taskId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ph-field-tasks"] });
      queryClient.invalidateQueries({ queryKey: ["ph-operations-home-mobile"] });
    },
  });

  const kpis = homeQ.data ?? {};
  const tasks = tasksQ.data ?? [];

  return (
    <Screen>
      <Header title="Public Health Field Tasks" subtitle="Governed task board from surveillance-service" />
      <ScrollView
        style={styles.scroll}
        refreshControl={
          <RefreshControl
            refreshing={tasksQ.isRefetching}
            onRefresh={() => {
              void tasksQ.refetch();
              void homeQ.refetch();
            }}
          />
        }
      >
        <MobileNearbyMapView
          entityTypes={["PUBLIC_HEALTH_SITE", "FACILITY"]}
          autoCapture={false}
          height={180}
        />

        <View style={styles.kpiRow}>
          <Kpi label="Open tasks" value={String(kpis.field_tasks_open ?? tasks.filter((t) => t.status !== "COMPLETED").length)} />
          <Kpi label="Outbreaks" value={String(kpis.active_outbreaks ?? "—")} />
          <Kpi label="Alerts" value={String(kpis.open_alerts ?? "—")} />
        </View>

        {tasksQ.isPending ? (
          <LoadingSpinner />
        ) : tasks.length === 0 ? (
          <Text style={styles.empty}>No field tasks. Record a field activity in the web shell or outreach workflow.</Text>
        ) : (
          tasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              busy={transitionM.isPending}
              onAdvance={(status) => transitionM.mutate({ taskId: task.id, status })}
            />
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.kpi}>
      <Text style={styles.kpiValue}>{value}</Text>
      <Text style={styles.kpiLabel}>{label}</Text>
    </View>
  );
}

function TaskCard({
  task,
  busy,
  onAdvance,
}: {
  task: FieldTaskRow;
  busy: boolean;
  onAdvance: (status: string) => void;
}) {
  const next = nextFieldTaskStatus(task.status);
  return (
    <View style={styles.card}>
      <Text style={styles.cardTitle}>{task.title}</Text>
      <Text style={styles.cardMeta}>
        {task.task_type} · {task.status}
        {task.assigned_to ? ` · ${task.assigned_to}` : ""}
      </Text>
      {next ? (
        <TouchableOpacity
          disabled={busy}
          style={styles.advanceBtn}
          onPress={() => onAdvance(next)}
        >
          <Text style={styles.advanceText}>Advance → {next}</Text>
        </TouchableOpacity>
      ) : (
        <Text style={styles.done}>Completed</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1, padding: 12 },
  kpiRow: { flexDirection: "row", gap: 8, marginBottom: 12 },
  kpi: { flex: 1, backgroundColor: "#F0FDF4", borderRadius: 8, padding: 10, borderWidth: 1, borderColor: "#BBF7D0" },
  kpiValue: { fontSize: 18, fontWeight: "700", color: "#166534" },
  kpiLabel: { fontSize: 10, color: "#4B5563", marginTop: 2 },
  empty: { textAlign: "center", color: "#6B7280", marginTop: 24, fontSize: 14 },
  card: { backgroundColor: "#FFF", borderRadius: 10, borderWidth: 1, borderColor: "#E5E7EB", padding: 12, marginBottom: 10 },
  cardTitle: { fontSize: 15, fontWeight: "600", color: "#111827" },
  cardMeta: { fontSize: 12, color: "#6B7280", marginTop: 4 },
  advanceBtn: { marginTop: 8 },
  advanceText: { fontSize: 12, color: "#1D4ED8", fontWeight: "600" },
  done: { fontSize: 11, color: "#9CA3AF", marginTop: 8 },
});
