/**
 * ConflictReviewScreen — Review and resolve sync conflicts.
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
  ErrorState,
} from "@impilo/mobile-design-system";
import { useConflicts } from "@impilo/mobile-offline";
import type { ConflictItem } from "../../types";

export function ConflictReviewScreen() {
  const { conflicts: rawConflicts, resolveConflict } = useConflicts();
  const [error, setError] = useState<string | null>(null);

  const conflicts: ConflictItem[] = (rawConflicts ?? []).map((c: { id: string; resourceType: string; resourceId: string; localVersion: Record<string, unknown>; serverVersion: Record<string, unknown>; conflictFields: string[]; detectedAt: string }) => ({
    id: c.id,
    resourceType: c.resourceType,
    resourceId: c.resourceId,
    localVersion: c.localVersion,
    serverVersion: c.serverVersion,
    conflictFields: c.conflictFields,
    detectedAt: c.detectedAt,
  }));

  const handleResolve = useCallback(async (id: string, resolution: "LOCAL_WINS" | "SERVER_WINS") => {
    setError(null);
    try {
      await resolveConflict(id, resolution);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resolve conflict");
    }
  }, [resolveConflict]);

  return (
    <Screen>
      <Header title="Conflict Review" />
      <ScrollView testID="conflict-review-screen" style={styles.container}>
        {error && <ErrorState title="Error" message={error} />}
        {conflicts.length === 0 ? (
          <EmptyState title="No conflicts" message="All data is synchronized" />
        ) : (
          conflicts.map((conflict) => (
            <Card key={conflict.id}>
              <CardBody>
                <View testID={`conflict-${conflict.id}`}>
                  <View style={styles.conflictHeader}>
                    <Text style={styles.boldText}>{`${conflict.resourceType} Conflict`}</Text>
                    <Badge variant="destructive">{`${conflict.conflictFields.length} fields`}</Badge>
                  </View>
                  <Text style={styles.resourceText}>{`Resource: ${conflict.resourceId}`}</Text>
                  <Text style={styles.metaText}>{`Detected: ${new Date(conflict.detectedAt).toLocaleString()}`}</Text>

                  {/* Conflict diff table */}
                  <View style={styles.diffTable}>
                    {/* Header row */}
                    <View style={styles.diffHeaderRow}>
                      <Text style={[styles.diffHeaderCell, styles.diffFieldCol]}>Field</Text>
                      <Text style={[styles.diffHeaderCell, styles.diffValueCol]}>Local</Text>
                      <Text style={[styles.diffHeaderCell, styles.diffValueCol]}>Server</Text>
                    </View>
                    {/* Data rows */}
                    {conflict.conflictFields.map((field) => (
                      <View key={field} style={styles.diffRow}>
                        <Text style={[styles.diffCell, styles.diffFieldCol, styles.fieldName]}>{field}</Text>
                        <Text style={[styles.diffCell, styles.diffValueCol, styles.localValue]}>
                          {String(conflict.localVersion[field] ?? "—")}
                        </Text>
                        <Text style={[styles.diffCell, styles.diffValueCol, styles.serverValue]}>
                          {String(conflict.serverVersion[field] ?? "—")}
                        </Text>
                      </View>
                    ))}
                  </View>

                  {/* Resolution buttons */}
                  <View style={styles.resolutionRow}>
                    <Button
                      title="Keep Local"
                      variant="primary"
                      size="sm"
                      onPress={() => handleResolve(conflict.id, "LOCAL_WINS")}
                      testID={`local-wins-${conflict.id}`}
                    />
                    <Button
                      title="Use Server"
                      variant="outline"
                      size="sm"
                      onPress={() => handleResolve(conflict.id, "SERVER_WINS")}
                      testID={`server-wins-${conflict.id}`}
                    />
                  </View>
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
  conflictHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 8,
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
  diffTable: {
    marginVertical: 12,
  },
  diffHeaderRow: {
    flexDirection: "row",
    borderBottomWidth: 1,
    borderBottomColor: "#E5E7EB",
  },
  diffRow: {
    flexDirection: "row",
  },
  diffHeaderCell: {
    padding: 4,
    fontSize: 12,
    fontWeight: "700",
  },
  diffCell: {
    padding: 4,
    fontSize: 12,
  },
  diffFieldCol: {
    flex: 1,
  },
  diffValueCol: {
    flex: 1,
  },
  fieldName: {
    fontWeight: "600",
  },
  localValue: {
    color: "#3B82F6",
  },
  serverValue: {
    color: "#F59E0B",
  },
  resolutionRow: {
    flexDirection: "row",
    gap: 8,
  },
});
