import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { Card, CardBody, FeatureMaturityBadge, LoadingSpinner } from "@impilo/mobile-design-system";

type AnyRecord = Record<string, unknown>;

function normalizeItems(value: unknown): AnyRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is AnyRecord => typeof item === "object" && item !== null);
  }
  if (typeof value === "object" && value !== null) {
    const record = value as AnyRecord;
    if (Array.isArray(record.items)) {
      return normalizeItems(record.items);
    }
  }
  return [];
}

export function WorkflowDispatchOpsScreen() {
  const workflowsQuery = useQuery({
    queryKey: ["provider-workflow-ops"],
    queryFn: () => apiClient.get<{ data?: unknown }>("/internal/v1/workflows"),
  });
  const dispatchQuery = useQuery({
    queryKey: ["provider-dispatch-ops"],
    queryFn: () => apiClient.get<{ data?: unknown }>("/internal/v1/dispatch/tasks"),
  });

  const workflows = normalizeItems(workflowsQuery.data?.data?.data);
  const tasks = normalizeItems(dispatchQuery.data?.data?.data);
  const isLoading = workflowsQuery.isLoading || dispatchQuery.isLoading;
  const isError = workflowsQuery.isError || dispatchQuery.isError;

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.banner}>
        <FeatureMaturityBadge
          status={workflows.length + tasks.length > 0 ? "connected" : "partial"}
          detail="Workflow and dispatch operator feeds are sourced from BFF orchestration endpoints."
        />
        <Text style={styles.bannerText}>
          {isLoading
            ? "Loading workflow and dispatch operator feeds..."
            : isError
              ? "Operator feeds unavailable from workflow/dispatch endpoints."
              : `Workflows: ${workflows.length} | Dispatch tasks: ${tasks.length}`}
        </Text>
      </View>

      {isLoading && <LoadingSpinner />}

      <Card>
        <CardBody>
          <Text style={styles.sectionTitle}>Workflow Orchestration</Text>
          {workflows.slice(0, 6).map((workflow, index) => (
            <View key={String(workflow.id ?? `wf-${index}`)} style={styles.row}>
              <Text style={styles.label}>{String(workflow.id ?? "workflow")}</Text>
              <Text style={styles.value}>{String(workflow.state ?? workflow.status ?? "UNKNOWN")}</Text>
            </View>
          ))}
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <Text style={styles.sectionTitle}>Dispatch Operations</Text>
          {tasks.slice(0, 6).map((task, index) => (
            <View key={String(task.id ?? `task-${index}`)} style={styles.row}>
              <Text style={styles.label}>{String(task.id ?? "task")}</Text>
              <Text style={styles.value}>{String(task.status ?? task.state ?? "UNKNOWN")}</Text>
            </View>
          ))}
        </CardBody>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    gap: 12,
    padding: 12,
  },
  banner: {
    gap: 8,
  },
  bannerText: {
    fontSize: 12,
    color: "#4B5563",
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 6,
  },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 8,
    paddingVertical: 4,
  },
  label: {
    flex: 1,
    fontSize: 12,
    color: "#374151",
  },
  value: {
    fontSize: 12,
    color: "#1F2937",
    fontWeight: "600",
  },
});
