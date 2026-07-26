import React, { useMemo, useState } from "react";
import { View, Text, StyleSheet, Pressable, Alert, Linking } from "react-native";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Card, CardBody, FeatureMaturityBadge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import {
  applyCoreTransactionAction,
  fetchNompiloContext,
  listCoreTransactions,
  requestNompiloHandoff,
} from "../../services/coreTransactionService";

type AnyRecord = Record<string, unknown>;

function asRecord(value: unknown): AnyRecord {
  return typeof value === "object" && value !== null ? (value as AnyRecord) : {};
}

function transactionEnvelope(row: AnyRecord): AnyRecord {
  return asRecord(row.transaction ?? row);
}

function transactionId(row: AnyRecord): string {
  return String(transactionEnvelope(row).id ?? row.id ?? "");
}

function nextActionLabel(row: AnyRecord): string | undefined {
  const actions = row.nextActions;
  if (!Array.isArray(actions) || actions.length === 0) return undefined;
  const first = asRecord(actions[0]);
  return typeof first.label === "string" ? first.label : undefined;
}

function nompiloGuidance(nompilo: AnyRecord): string {
  const current = nompilo.currentGuidance;
  if (Array.isArray(current) && current.length > 0) {
    const message = asRecord(current[0]).message;
    if (typeof message === "string" && message.trim()) return message;
  }
  if (typeof nompilo.guidanceHint === "string" && nompilo.guidanceHint.trim()) {
    return nompilo.guidanceHint;
  }
  return "Select handoff when staffed support is required.";
}

export function CoreTransactionJourneyShellScreen() {
  const [selectedId, setSelectedId] = useState<string>("");

  const { data: transactions = [], isLoading, isError, refetch } = useQuery({
    queryKey: ["provider-core-transactions"],
    queryFn: () => listCoreTransactions(),
  });

  const activeId = selectedId || (transactions[0] ? transactionId(transactions[0]) : "");
  const activeRow = useMemo(
    () => transactions.find((row) => transactionId(row) === activeId),
    [transactions, activeId],
  );

  const nompiloQ = useQuery({
    queryKey: ["provider-core-transactions", activeId, "nompilo"],
    queryFn: () => fetchNompiloContext(activeId),
    enabled: activeId.length > 0,
  });

  const handoff = useMutation({
    mutationFn: () => requestNompiloHandoff(activeId, { handoffType: "CALL_CENTER" }),
    onSuccess: () => {
      Alert.alert("Handoff requested", "Nompilo staffed callback has been queued for this transaction.");
      void nompiloQ.refetch();
    },
    onError: () => Alert.alert("Handoff failed", "Could not request Nompilo handoff for this transaction."),
  });

  const applyAction = useMutation({
    mutationFn: (actionCode: string) => applyCoreTransactionAction(activeId, actionCode),
    onSuccess: () => {
      Alert.alert("Action applied", "Core transaction action submitted to BFF.");
      void refetch();
    },
    onError: () => Alert.alert("Action failed", "Could not apply the selected next action."),
  });

  const trust = asRecord(activeRow?.trustContext);
  const audit = asRecord(activeRow?.auditSummary);
  const nextAction = activeRow ? nextActionLabel(activeRow) : undefined;
  const nextActionCode = Array.isArray(activeRow?.nextActions)
    ? String(asRecord(asRecord(activeRow?.nextActions?.[0]).actionCode ?? asRecord(activeRow?.nextActions?.[0]).code) ?? "")
    : "";

  return (
    <View style={styles.container}>
      <View style={styles.banner}>
        <FeatureMaturityBadge
          status={transactions.length > 0 ? "connected" : "partial"}
          detail="Workflow + dispatch deliveries composed at /internal/v1/core-transactions."
        />
        <Text style={styles.bannerText}>
          {isLoading
            ? "Loading provider core-transaction journey shell..."
            : isError
              ? "Core-transaction endpoint unavailable."
              : `Loaded ${transactions.length} core transaction(s) including delivery bridge rows.`}
        </Text>
      </View>

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        transactions.slice(0, 20).map((transaction, index) => {
          const id = transactionId(transaction);
          const tx = transactionEnvelope(transaction);
          const selected = id === activeId;
          const action = nextActionLabel(transaction);
          const clientSummary = asRecord(transaction.clientSummary);
          return (
            <Pressable key={id || `tx-${index}`} onPress={() => setSelectedId(id)}>
              <Card>
                <CardBody>
                  <Text style={[styles.title, selected && styles.titleSelected]}>{id || "Unknown transaction"}</Text>
                  <Text style={styles.meta}>
                    {String(tx.type ?? "UNKNOWN")} · {String(tx.currentState ?? "UNKNOWN")}
                  </Text>
                  {clientSummary.clientAlias ? (
                    <Text style={styles.meta}>Client: {String(clientSummary.clientAlias)}</Text>
                  ) : null}
                  {action ? <Text style={styles.nextAction}>Next: {action}</Text> : null}
                </CardBody>
              </Card>
            </Pressable>
          );
        })
      )}

      {activeId ? (
        <View style={styles.detailBox}>
          <Text style={styles.detailTitle}>Selected transaction</Text>
          <Text style={styles.meta}>Purpose: {String(trust.purposeOfUse ?? "—")}</Text>
          <Text style={styles.meta}>Correlation: {String(audit.correlationId ?? "—")}</Text>
          {nextAction ? (
            <Pressable
              style={[styles.actionButton, applyAction.isPending && styles.handoffDisabled]}
              disabled={applyAction.isPending || !nextActionCode}
              onPress={() => nextActionCode && applyAction.mutate(nextActionCode)}
            >
              <Text style={styles.actionButtonText}>
                {applyAction.isPending ? "Applying…" : `Apply: ${nextAction}`}
              </Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}

      {activeId ? (
        <View style={styles.handoffBox}>
          <Text style={styles.handoffTitle}>Nompilo companion</Text>
          <Text style={styles.meta}>{nompiloGuidance(asRecord(nompiloQ.data))}</Text>
          <Pressable
            style={[styles.handoffButton, handoff.isPending && styles.handoffDisabled]}
            disabled={handoff.isPending}
            onPress={() => handoff.mutate()}
          >
            <Text style={styles.handoffButtonText}>
              {handoff.isPending ? "Requesting…" : "Request staffed callback"}
            </Text>
          </Pressable>
          <Pressable onPress={() => void refetch()}>
            <Text style={styles.refreshLink}>Refresh feed</Text>
          </Pressable>
          <Pressable onPress={() => void Linking.openURL("http://localhost:3000/pharmacy/transaction-journey?patientId=CPID-ZW-00001")}>
            <Text style={styles.refreshLink}>Open Rx journey on web</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  banner: {
    gap: 8,
  },
  bannerText: {
    fontSize: 12,
    color: colors.gray[600],
  },
  title: {
    fontSize: 14,
    fontWeight: "700",
    color: colors.gray[900],
  },
  titleSelected: {
    color: "#1D4ED8",
  },
  meta: {
    fontSize: 12,
    color: colors.gray[600],
    marginTop: 4,
  },
  nextAction: {
    fontSize: 12,
    color: "#059669",
    marginTop: 4,
    fontWeight: "600",
  },
  detailBox: {
    marginTop: 4,
    padding: 12,
    borderRadius: 12,
    backgroundColor: "#EFF6FF",
    gap: 8,
  },
  detailTitle: {
    fontSize: 13,
    fontWeight: "700",
    color: "#1E3A8A",
  },
  actionButton: {
    alignSelf: "flex-start",
    backgroundColor: "#2563EB",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  actionButtonText: {
    color: "#FFFFFF",
    fontSize: 12,
    fontWeight: "600",
  },
  handoffBox: {
    marginTop: 8,
    padding: 12,
    borderRadius: 12,
    backgroundColor: "#ECFDF5",
    gap: 8,
  },
  handoffTitle: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.ui.success.text,
  },
  handoffButton: {
    alignSelf: "flex-start",
    backgroundColor: "#059669",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  handoffDisabled: {
    opacity: 0.6,
  },
  handoffButtonText: {
    color: "#FFFFFF",
    fontSize: 12,
    fontWeight: "600",
  },
  refreshLink: {
    fontSize: 12,
    color: "#2563EB",
  },
});
