import React, { useState } from "react";
import { View, Text, StyleSheet, Pressable, Alert } from "react-native";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Card, CardBody, FeatureMaturityBadge, LoadingSpinner } from "@impilo/mobile-design-system";
import {
  fetchNompiloContext,
  listCoreTransactions,
  requestNompiloHandoff,
} from "../../services/coreTransactionService";

type AnyRecord = Record<string, unknown>;

function transactionId(row: AnyRecord): string {
  const tx = (row.transaction as AnyRecord | undefined) ?? row;
  return String(tx.id ?? row.id ?? "");
}

export function CoreTransactionJourneyShellScreen() {
  const [selectedId, setSelectedId] = useState<string>("");

  const { data: transactions = [], isLoading, isError, refetch } = useQuery({
    queryKey: ["provider-core-transactions"],
    queryFn: () => listCoreTransactions(),
  });

  const activeId = selectedId || (transactions[0] ? transactionId(transactions[0]) : "");

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
        transactions.slice(0, 8).map((transaction, index) => {
          const id = transactionId(transaction);
          const tx = (transaction.transaction as AnyRecord | undefined) ?? transaction;
          const selected = id === activeId;
          return (
            <Pressable key={id || `tx-${index}`} onPress={() => setSelectedId(id)}>
              <Card>
                <CardBody>
                  <Text style={[styles.title, selected && styles.titleSelected]}>{id || "Unknown transaction"}</Text>
                  <Text style={styles.meta}>Type: {String(tx.type ?? "UNKNOWN")}</Text>
                  <Text style={styles.meta}>State: {String(tx.currentState ?? "UNKNOWN")}</Text>
                </CardBody>
              </Card>
            </Pressable>
          );
        })
      )}

      {activeId ? (
        <View style={styles.handoffBox}>
          <Text style={styles.handoffTitle}>Nompilo companion</Text>
          <Text style={styles.meta}>
            {String((nompiloQ.data as AnyRecord)?.guidanceHint ?? "Select handoff when staffed support is required.")}
          </Text>
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
    color: "#4B5563",
  },
  title: {
    fontSize: 14,
    fontWeight: "700",
    color: "#111827",
  },
  titleSelected: {
    color: "#1D4ED8",
  },
  meta: {
    fontSize: 12,
    color: "#4B5563",
    marginTop: 4,
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
    color: "#065F46",
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
