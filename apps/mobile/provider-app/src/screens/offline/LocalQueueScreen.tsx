/**
 * LocalQueueScreen — View and manage the offline operation queue.
 */

import React, { useState, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  Badge,
  EmptyState,
} from "@impilo/mobile-design-system";
import { useSyncEngine } from "@impilo/mobile-offline";
import type { SyncQueueItem, SyncItemStatus } from "../../types";

const STATUS_COLORS: Record<SyncItemStatus, string> = {
  PENDING: "secondary",
  SYNCING: "primary",
  SYNCED: "primary",
  FAILED: "destructive",
  CONFLICT: "destructive",
};

export function LocalQueueScreen() {
  const { queue, pendingCount, retryFailed, triggerSync } = useSyncEngine();
  const [filter, setFilter] = useState<SyncItemStatus | "ALL">("ALL");

  const items: SyncQueueItem[] = (queue ?? []).map((op: { id: string; type: string; resourceType: string; resourceId: string; payload: Record<string, unknown>; status: string; attempts: number; lastAttemptAt?: string; error?: string; createdAt: string }) => ({
    id: op.id,
    operationType: op.type as SyncQueueItem["operationType"],
    resourceType: op.resourceType,
    resourceId: op.resourceId,
    payload: op.payload,
    status: op.status as SyncItemStatus,
    attemptCount: op.attempts,
    lastAttemptAt: op.lastAttemptAt,
    errorMessage: op.error,
    createdAt: op.createdAt,
  }));

  const filtered = filter === "ALL" ? items : items.filter((i) => i.status === filter);

  return (
    <Screen>
      <Header title="Sync Queue" />
      <ScrollView testID="local-queue-screen" style={styles.container}>
        {/* Filter tabs */}
        <View style={styles.filterRow}>
          {(["ALL", "PENDING", "SYNCING", "SYNCED", "FAILED", "CONFLICT"] as const).map((s) => (
            <Button
              key={s}
              title={s === "ALL" ? `All (${items.length})` : `${s} (${items.filter((i) => i.status === s).length})`}
              variant={filter === s ? "primary" : "outline"}
              size="sm"
              onPress={() => setFilter(s)}
              testID={`filter-${s.toLowerCase()}`}
            />
          ))}
        </View>

        {/* Actions */}
        <View style={styles.actionsRow}>
          <Button title="Sync All" variant="primary" onPress={triggerSync} testID="sync-all-btn" />
          <Button title="Retry Failed" variant="outline" onPress={retryFailed} testID="retry-failed-btn" />
        </View>

        {/* Queue items */}
        {filtered.length === 0 ? (
          <EmptyState title="Queue empty" message="No operations in queue" />
        ) : (
          filtered.map((item) => (
            <Card key={item.id}>
              <CardBody>
                <View testID={`queue-item-${item.id}`}>
                  <View style={styles.itemHeader}>
                    <Text style={styles.boldText}>{`${item.operationType} ${item.resourceType}`}</Text>
                    <Badge
                      variant={STATUS_COLORS[item.status] as "primary" | "destructive" | "secondary"}
                    >
                      {item.status}
                    </Badge>
                  </View>
                  <Text style={styles.resourceText}>{`Resource: ${item.resourceId}`}</Text>
                  <Text style={styles.metaText}>
                    {`Attempts: ${item.attemptCount} | Created: ${new Date(item.createdAt).toLocaleTimeString()}`}
                  </Text>
                  {item.errorMessage && (
                    <Text style={styles.errorText}>{item.errorMessage}</Text>
                  )}
                </View>
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  filterRow: {
    flexDirection: "row",
    gap: 4,
    marginBottom: 16,
    flexWrap: "wrap",
  },
  actionsRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 12,
  },
  itemHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  boldText: {
    fontWeight: "700",
  },
  resourceText: {
    fontSize: 12,
    color: "#6B7280",
  },
  metaText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  errorText: {
    fontSize: 12,
    color: "#DC2626",
    marginTop: 4,
  },
});
