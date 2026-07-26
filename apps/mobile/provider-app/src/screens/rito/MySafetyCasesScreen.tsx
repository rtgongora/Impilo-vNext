/**
 * MySafetyCasesScreen — Provider's assigned Rito cases (Quality, Safety &
 * Client Voice). Lists cases assigned to the signed-in provider with status,
 * reference, and type. Honest empty/error/unauthenticated states.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Card, CardBody, Badge, Button, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import { myCases, currentProviderActorId } from "../../services/ritoSafetyService";
import type { RitoSafetyCase } from "../../services/ritoSafetyService";

const STATUS_VARIANT: Record<string, "default" | "info" | "success" | "warning" | "error"> = {
  NEW: "info",
  OPEN: "info",
  ACKNOWLEDGED: "info",
  TRIAGED: "warning",
  ASSIGNED: "warning",
  IN_PROGRESS: "warning",
  ESCALATED: "error",
  RESOLVED: "success",
  CLOSED: "default",
  CANCELLED: "default",
};

function statusVariant(status: string): "default" | "info" | "success" | "warning" | "error" {
  return STATUS_VARIANT[status?.toUpperCase()] ?? "default";
}

export function MySafetyCasesScreen() {
  const [cases, setCases] = useState<RitoSafetyCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    const actorId = currentProviderActorId();
    if (!actorId) {
      setError(new Error("No active provider session. Sign in to view your assigned cases."));
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await myCases(actorId);
      setCases(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <LoadingSpinner size="md" />;
  }

  if (error) {
    return <ErrorState title="Could not load cases" message={error.message} onRetry={load} />;
  }

  if (cases.length === 0) {
    return (
      <EmptyState
        title="No assigned cases"
        message="Safety and quality cases assigned to you will appear here."
      />
    );
  }

  return (
    <ScrollView testID="rito-my-cases-screen" style={styles.scrollView} showsVerticalScrollIndicator={false}>
      <View style={styles.container}>
        <View style={styles.headerRow}>
          <Text style={styles.heading}>My assigned cases</Text>
          <Button title="Refresh" variant="ghost" size="sm" onPress={load} testID="rito-my-cases-refresh" />
        </View>

        {cases.map((c) => (
          <Card key={c.id}>
            <CardBody>
              <View testID={`rito-case-${c.id}`}>
                <View style={styles.badgeRow}>
                  <Text style={styles.caseTitle} numberOfLines={2}>
                    {c.title || c.caseReference}
                  </Text>
                  <Badge variant={statusVariant(c.status)}>{c.status}</Badge>
                </View>
                <View style={styles.metaRow}>
                  <Badge variant="outline">{c.caseType}</Badge>
                  {c.severity ? <Badge variant="secondary">{c.severity}</Badge> : null}
                </View>
                <Text style={styles.reference}>{c.caseReference}</Text>
                {c.createdAt ? (
                  <Text style={styles.date}>
                    {`Reported: ${new Date(c.createdAt).toLocaleDateString()}`}
                  </Text>
                ) : null}
              </View>
            </CardBody>
          </Card>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    gap: 12,
    paddingBottom: 24,
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  heading: {
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 6,
  },
  caseTitle: {
    flex: 1,
    fontSize: 15,
    fontWeight: "600",
    color: colors.gray[900],
  },
  metaRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    flexWrap: "wrap",
    marginBottom: 6,
  },
  reference: {
    fontSize: 13,
    color: colors.gray[500],
  },
  date: {
    fontSize: 12,
    color: colors.gray[400],
    marginTop: 4,
  },
});
