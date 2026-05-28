import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView, TextInput } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Card, CardBody, FeatureMaturityBadge, LoadingSpinner } from "@impilo/mobile-design-system";
import {
  assignDispatchTask,
  completeDispatchTask,
  createDispatchTask,
  listDispatchTasks,
  listWorkflowDefinitions,
  listWorkflowFeed,
  listWorkflowInstances,
  runDeliveryAction,
  startWorkflowInstance,
  transitionWorkflowInstance,
  type WorkflowDispatchRecord,
} from "../../services/workflowDispatchService";

type AnyRecord = Record<string, unknown>;
type DispatchCommand = "create" | "assign" | "complete";

function readString(record: AnyRecord, keys: string[], fallback: string) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) {
      return value;
    }
    if (typeof value === "number") {
      return String(value);
    }
  }
  return fallback;
}

function parseJsonObject(text: string): WorkflowDispatchRecord {
  const parsed = JSON.parse(text);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("Payload must be a JSON object.");
  }
  return parsed as WorkflowDispatchRecord;
}

export function WorkflowDispatchOpsScreen() {
  const queryClient = useQueryClient();
  const [workflowPayloadText, setWorkflowPayloadText] = useState(
    JSON.stringify(
      {
        definitionId: "00000000-0000-4000-8000-000000000000",
        initiatorRef: "provider:mobile",
        subjectRef: "client:current",
        context: {
          source: "provider-mobile-flow-ops",
        },
      },
      null,
      2,
    ),
  );
  const [workflowInstanceId, setWorkflowInstanceId] = useState("");
  const [workflowTransitionText, setWorkflowTransitionText] = useState(
    JSON.stringify(
      {
        action: "COMPLETE_STEP",
        output: {
          note: "Transition submitted from provider mobile Flow/Ops.",
        },
      },
      null,
      2,
    ),
  );
  const [dispatchCommand, setDispatchCommand] = useState<DispatchCommand>("create");
  const [dispatchTaskId, setDispatchTaskId] = useState("");
  const [dispatchPayloadText, setDispatchPayloadText] = useState(
    JSON.stringify(
      {
        type: "PROVIDER_FIELD_TASK",
        priority: "NORMAL",
        assigneeId: "provider:current",
      },
      null,
      2,
    ),
  );
  const [deliveryId, setDeliveryId] = useState("");
  const [deliveryActionName, setDeliveryActionName] = useState("dispatch");
  const [deliveryPayloadText, setDeliveryPayloadText] = useState(
    JSON.stringify(
      {
        source: "provider-mobile-flow-ops",
      },
      null,
      2,
    ),
  );
  const [workflowFeedback, setWorkflowFeedback] = useState<string | null>(null);
  const [dispatchFeedback, setDispatchFeedback] = useState<string | null>(null);
  const [deliveryFeedback, setDeliveryFeedback] = useState<string | null>(null);

  const workflowsQuery = useQuery({
    queryKey: ["provider-workflow-ops"],
    queryFn: listWorkflowFeed,
  });
  const workflowDefinitionsQuery = useQuery({
    queryKey: ["provider-workflow-definitions"],
    queryFn: listWorkflowDefinitions,
  });
  const workflowInstancesQuery = useQuery({
    queryKey: ["provider-workflow-instances"],
    queryFn: listWorkflowInstances,
  });
  const dispatchQuery = useQuery({
    queryKey: ["provider-dispatch-ops"],
    queryFn: listDispatchTasks,
  });

  const startWorkflowMutation = useMutation({
    mutationFn: (payload: WorkflowDispatchRecord) => startWorkflowInstance(payload),
    onSuccess: () => {
      setWorkflowFeedback("Workflow instance start accepted.");
      void queryClient.invalidateQueries({ queryKey: ["provider-workflow-instances"] });
      void queryClient.invalidateQueries({ queryKey: ["provider-workflow-ops"] });
    },
    onError: () => setWorkflowFeedback("Workflow start failed. Verify definition, payload, and trust context."),
  });
  const transitionWorkflowMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: WorkflowDispatchRecord }) =>
      transitionWorkflowInstance(id, payload),
    onSuccess: () => {
      setWorkflowFeedback("Workflow transition accepted.");
      void queryClient.invalidateQueries({ queryKey: ["provider-workflow-instances"] });
      void queryClient.invalidateQueries({ queryKey: ["provider-workflow-ops"] });
    },
    onError: () => setWorkflowFeedback("Workflow transition failed. Verify instance, action, and trust context."),
  });
  const dispatchCommandMutation = useMutation({
    mutationFn: async (payload: WorkflowDispatchRecord) => {
      if (dispatchCommand === "create") return createDispatchTask(payload);
      if (!dispatchTaskId.trim()) throw new Error("Task id is required.");
      if (dispatchCommand === "assign") return assignDispatchTask(dispatchTaskId.trim(), payload);
      return completeDispatchTask(dispatchTaskId.trim(), payload);
    },
    onSuccess: () => {
      setDispatchFeedback("Dispatch task command accepted.");
      void queryClient.invalidateQueries({ queryKey: ["provider-dispatch-ops"] });
    },
    onError: (error) =>
      setDispatchFeedback(error instanceof Error ? error.message : "Dispatch task command failed."),
  });
  const deliveryActionMutation = useMutation({
    mutationFn: (payload: WorkflowDispatchRecord) =>
      runDeliveryAction(deliveryId.trim(), deliveryActionName.trim(), payload),
    onSuccess: () => {
      setDeliveryFeedback("Delivery action accepted.");
      void queryClient.invalidateQueries({ queryKey: ["provider-dispatch-ops"] });
    },
    onError: () => setDeliveryFeedback("Delivery action failed. Verify delivery, action, and trust context."),
  });

  const workflows = workflowsQuery.data ?? [];
  const definitions = workflowDefinitionsQuery.data ?? [];
  const instances = workflowInstancesQuery.data ?? [];
  const tasks = dispatchQuery.data ?? [];
  const isLoading =
    workflowsQuery.isLoading ||
    workflowDefinitionsQuery.isLoading ||
    workflowInstancesQuery.isLoading ||
    dispatchQuery.isLoading;
  const isError =
    workflowsQuery.isError ||
    workflowDefinitionsQuery.isError ||
    workflowInstancesQuery.isError ||
    dispatchQuery.isError;

  function submitStartWorkflow() {
    setWorkflowFeedback(null);
    try {
      startWorkflowMutation.mutate(parseJsonObject(workflowPayloadText));
    } catch (error) {
      setWorkflowFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
    }
  }

  function submitTransitionWorkflow() {
    setWorkflowFeedback(null);
    if (!workflowInstanceId.trim()) {
      setWorkflowFeedback("Workflow instance id is required.");
      return;
    }
    try {
      transitionWorkflowMutation.mutate({
        id: workflowInstanceId.trim(),
        payload: parseJsonObject(workflowTransitionText),
      });
    } catch (error) {
      setWorkflowFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
    }
  }

  function submitDispatchCommand() {
    setDispatchFeedback(null);
    try {
      dispatchCommandMutation.mutate(parseJsonObject(dispatchPayloadText));
    } catch (error) {
      setDispatchFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
    }
  }

  function submitDeliveryAction() {
    setDeliveryFeedback(null);
    if (!deliveryId.trim() || !deliveryActionName.trim()) {
      setDeliveryFeedback("Delivery id and action are required.");
      return;
    }
    try {
      deliveryActionMutation.mutate(parseJsonObject(deliveryPayloadText));
    } catch (error) {
      setDeliveryFeedback(error instanceof Error ? error.message : "Invalid JSON payload.");
    }
  }

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <View style={styles.banner}>
        <FeatureMaturityBadge
          status={workflows.length + tasks.length + instances.length > 0 ? "connected" : "partial"}
          detail="Provider mobile Flow/Ops is backed by workflow and dispatch BFF read + command endpoints."
        />
        <Text style={styles.bannerText}>
          {isLoading
            ? "Loading workflow and dispatch operator feeds..."
            : isError
              ? "Operator feeds unavailable from workflow/dispatch endpoints."
              : `Workflows: ${workflows.length} | Definitions: ${definitions.length} | Instances: ${instances.length} | Dispatch tasks: ${tasks.length}`}
        </Text>
      </View>

      {isLoading && <LoadingSpinner />}

      <Card>
        <CardBody>
          <Text style={styles.sectionTitle}>Workflow Orchestration</Text>
          <Text style={styles.hint}>Live reads from workflow feed, definitions, and instances.</Text>
          {workflows.slice(0, 6).map((workflow, index) => (
            <View key={readString(workflow, ["id", "workflowId", "instanceId"], `wf-${index}`)} style={styles.row}>
              <Text style={styles.label}>{readString(workflow, ["id", "workflowId", "instanceId"], "workflow")}</Text>
              <Text style={styles.value}>{readString(workflow, ["state", "status"], "UNKNOWN")}</Text>
            </View>
          ))}
          {instances.slice(0, 4).map((instance, index) => {
            const id = readString(instance, ["id", "instanceId", "workflowInstanceId"], `instance-${index + 1}`);
            return (
              <View key={id} style={styles.commandRow}>
                <View style={styles.rowText}>
                  <Text style={styles.label}>{id}</Text>
                  <Text style={styles.value}>{readString(instance, ["state", "status"], "UNKNOWN")}</Text>
                </View>
                <Button title="Use" size="sm" onPress={() => setWorkflowInstanceId(id)} />
              </View>
            );
          })}
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <Text style={styles.sectionTitle}>Dispatch Operations</Text>
          {tasks.slice(0, 6).map((task, index) => (
            <View key={readString(task, ["id", "taskId"], `task-${index}`)} style={styles.commandRow}>
              <View style={styles.rowText}>
                <Text style={styles.label}>{readString(task, ["id", "taskId"], "task")}</Text>
                <Text style={styles.value}>{readString(task, ["status", "state"], "UNKNOWN")}</Text>
              </View>
              <Button title="Use" size="sm" onPress={() => setDispatchTaskId(readString(task, ["id", "taskId"], ""))} />
            </View>
          ))}
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <Text style={styles.sectionTitle}>Workflow Commands</Text>
          <Text style={styles.hint}>
            Start and transition workflow instances through live BFF endpoints. No fixture mode.
          </Text>
          <Text style={styles.label}>Start instance payload</Text>
          <TextInput
            style={styles.textArea}
            value={workflowPayloadText}
            onChangeText={setWorkflowPayloadText}
            multiline
            autoCapitalize="none"
          />
          <Button
            title={startWorkflowMutation.isPending ? "Starting..." : "Start workflow instance"}
            onPress={submitStartWorkflow}
            disabled={startWorkflowMutation.isPending}
          />
          <Text style={styles.label}>Transition instance id</Text>
          <TextInput
            style={styles.input}
            value={workflowInstanceId}
            onChangeText={setWorkflowInstanceId}
            placeholder="Workflow instance UUID"
            autoCapitalize="none"
          />
          <Text style={styles.label}>Transition payload</Text>
          <TextInput
            style={styles.textArea}
            value={workflowTransitionText}
            onChangeText={setWorkflowTransitionText}
            multiline
            autoCapitalize="none"
          />
          <Button
            title={transitionWorkflowMutation.isPending ? "Transitioning..." : "Transition workflow instance"}
            onPress={submitTransitionWorkflow}
            disabled={transitionWorkflowMutation.isPending || !workflowInstanceId.trim()}
          />
          {workflowFeedback ? <Text style={styles.feedback}>{workflowFeedback}</Text> : null}
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <Text style={styles.sectionTitle}>Dispatch Task Commands</Text>
          <View style={styles.segmentRow}>
            {(["create", "assign", "complete"] as DispatchCommand[]).map((mode) => (
              <Button
                key={mode}
                title={mode}
                size="sm"
                variant={dispatchCommand === mode ? "primary" : "outline"}
                onPress={() => setDispatchCommand(mode)}
              />
            ))}
          </View>
          <Text style={styles.label}>Task id</Text>
          <TextInput
            style={styles.input}
            value={dispatchTaskId}
            onChangeText={setDispatchTaskId}
            placeholder="Required for assign/complete"
            autoCapitalize="none"
          />
          <Text style={styles.label}>Command payload</Text>
          <TextInput
            style={styles.textArea}
            value={dispatchPayloadText}
            onChangeText={setDispatchPayloadText}
            multiline
            autoCapitalize="none"
          />
          <Button
            title={dispatchCommandMutation.isPending ? "Submitting..." : "Submit dispatch command"}
            onPress={submitDispatchCommand}
            disabled={dispatchCommandMutation.isPending}
          />
          {dispatchFeedback ? <Text style={styles.feedback}>{dispatchFeedback}</Text> : null}
        </CardBody>
      </Card>

      <Card>
        <CardBody>
          <Text style={styles.sectionTitle}>Delivery Action Command</Text>
          <Text style={styles.hint}>Runs action commands against dispatch deliveries through the BFF.</Text>
          <Text style={styles.label}>Delivery id</Text>
          <TextInput
            style={styles.input}
            value={deliveryId}
            onChangeText={setDeliveryId}
            placeholder="Delivery UUID/reference"
            autoCapitalize="none"
          />
          <Text style={styles.label}>Action</Text>
          <TextInput
            style={styles.input}
            value={deliveryActionName}
            onChangeText={setDeliveryActionName}
            placeholder="dispatch, cancel, complete..."
            autoCapitalize="none"
          />
          <Text style={styles.label}>Action payload</Text>
          <TextInput
            style={styles.textArea}
            value={deliveryPayloadText}
            onChangeText={setDeliveryPayloadText}
            multiline
            autoCapitalize="none"
          />
          <Button
            title={deliveryActionMutation.isPending ? "Submitting..." : "Submit delivery action"}
            onPress={submitDeliveryAction}
            disabled={deliveryActionMutation.isPending || !deliveryId.trim() || !deliveryActionName.trim()}
          />
          {deliveryFeedback ? <Text style={styles.feedback}>{deliveryFeedback}</Text> : null}
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
  commandRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 6,
  },
  rowText: {
    flex: 1,
  },
  label: {
    fontSize: 12,
    color: "#374151",
  },
  value: {
    fontSize: 12,
    color: "#1F2937",
    fontWeight: "600",
  },
  hint: {
    fontSize: 12,
    color: "#6B7280",
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    padding: 10,
    fontSize: 12,
    color: "#111827",
    marginTop: 4,
    marginBottom: 8,
    backgroundColor: "#FFFFFF",
  },
  textArea: {
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    minHeight: 108,
    padding: 10,
    fontSize: 12,
    color: "#111827",
    marginTop: 4,
    marginBottom: 8,
    backgroundColor: "#FFFFFF",
    textAlignVertical: "top",
  },
  segmentRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 8,
  },
  feedback: {
    marginTop: 8,
    fontSize: 12,
    color: "#1F2937",
  },
});
