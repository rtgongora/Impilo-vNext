/**
 * MaintenanceTasksScreen — biomedical/technician open maintenance worklist.
 * Complete a task and (optionally) return equipment to service.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Card, CardHeader, CardBody, Button, Badge, LoadingSpinner, EmptyState } from "@impilo/mobile-design-system";
import { listOpenMaintenance, completeMaintenance, type MaintenanceTask } from "../../services/equipmentService";

export function MaintenanceTasksScreen() {
  const [tasks, setTasks] = useState<MaintenanceTask[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setTasks(await listOpenMaintenance());
    } catch (e) {
      setError(e as Error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const complete = useCallback(
    async (taskId: string) => {
      setBusyId(taskId);
      try {
        await completeMaintenance(taskId, true);
        await load();
      } catch (e) {
        setError(e as Error);
      } finally {
        setBusyId(null);
      }
    },
    [load],
  );

  if (loading) return <LoadingSpinner />;
  if (error) return <Text style={styles.error}>Could not load maintenance tasks.</Text>;
  if (tasks.length === 0) return <EmptyState title="No open tasks" message="There are no open maintenance tasks." />;

  return (
    <ScrollView style={styles.container}>
      {tasks.map((t) => (
        <Card key={t.task_id}>
          <CardHeader title={t.title} />
          <CardBody>
            <View style={styles.badges}>
              <Badge label={t.task_type} />
              <Badge label={t.status} />
              <Badge label={t.priority} />
            </View>
            <Button
              title={busyId === t.task_id ? "Completing…" : "Complete + return to service"}
              onPress={() => complete(t.task_id)}
              disabled={busyId === t.task_id}
            />
          </CardBody>
        </Card>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 12 },
  badges: { flexDirection: "row", gap: 6, marginBottom: 8 },
  error: { color: "#b91c1c", padding: 12 },
});
