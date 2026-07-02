/**
 * QueueDefinitionsScreen — read-only viewer of a facility's queue definitions.
 *
 * Queue definitions are owned by TUSO facility service-point/workspace configuration and materialised
 * into PCT for care operations (there is no create/edit route at the BFF), so this surface is
 * intentionally read-only: it lets facility staff confirm the queue configuration for the
 * QUEUES setup step. Keyed on the provider's active facility (session X-Facility-ID via appStore).
 * Backed by the live GET /internal/v1/queue/definitions?facility_id=. Real data only — no mock rows.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Badge,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { fetchQueueDefinitions, type QueueDefinition } from "../../services/queueService";

export function QueueDefinitionsScreen() {
  const { facilityId } = useAppStore();
  const [definitions, setDefinitions] = useState<QueueDefinition[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      setDefinitions(await fetchQueueDefinitions(facilityId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load queue definitions");
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <Screen>
      <Header title="Queue Definitions" />
      <View testID="queue-definitions-screen" style={styles.container}>
        <Text style={styles.note}>
          Queue definitions are owned by TUSO facility configuration (service points) and materialised
          into PCT for care operations. Shown here read-only, to confirm the QUEUES setup step.
        </Text>

        {!facilityId ? (
          <EmptyState title="No active facility" message="Select a facility to view its queue definitions" />
        ) : loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Unavailable" message={error} onRetry={load} />
        ) : definitions.length === 0 ? (
          <EmptyState title="No queue definitions" message="This facility has no configured queues" />
        ) : (
          <ScrollView style={styles.scrollArea} contentContainerStyle={styles.scrollContent}>
            {definitions.map((q) => (
              <Card key={q.id || q.name}>
                <CardBody>
                  <View testID={`queue-${q.id || q.name}`} style={styles.row}>
                    <View style={styles.info}>
                      <Text style={styles.boldText}>{q.name}</Text>
                      {q.serviceType ? <Text style={styles.detailText}>{q.serviceType}</Text> : null}
                    </View>
                    <Badge variant={q.active ? "success" : "secondary"}>
                      {q.status ?? (q.active ? "Active" : "Inactive")}
                    </Badge>
                  </View>
                </CardBody>
              </Card>
            ))}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={load} testID="refresh-queue-definitions" />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  note: { fontSize: 12, color: "#6B7280", marginBottom: 12 },
  scrollArea: { flex: 1 },
  scrollContent: { gap: 12, paddingBottom: 16 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  info: { flex: 1, gap: 2 },
  boldText: { fontWeight: "700", color: "#111827" },
  detailText: { fontSize: 13, color: "#6B7280" },
  refreshContainer: { marginTop: 8 },
});
