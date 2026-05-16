import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";
import { Card, CardBody, FeatureMaturityBadge, LoadingSpinner } from "@impilo/mobile-design-system";

type AnyRecord = Record<string, unknown>;

function asArray(value: unknown): AnyRecord[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is AnyRecord => typeof item === "object" && item !== null);
  }
  return [];
}

export function CoreTransactionJourneyShellScreen() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["provider-core-transactions"],
    queryFn: () =>
      apiClient.get<{ data?: { items?: AnyRecord[] } | AnyRecord[] }>(
        "/internal/v1/core-transactions"
      ),
  });

  const payload = data?.data?.data;
  const transactions = asArray(Array.isArray(payload) ? payload : payload?.items);

  return (
    <View style={styles.container}>
      <View style={styles.banner}>
        <FeatureMaturityBadge
          status={transactions.length > 0 ? "connected" : "partial"}
          detail="Provider core transaction shell is backed by /internal/v1/core-transactions."
        />
        <Text style={styles.bannerText}>
          {isLoading
            ? "Loading provider core-transaction journey shell..."
            : isError
              ? "Core-transaction endpoint unavailable."
              : `Loaded ${transactions.length} core transaction(s) for provider journey context.`}
        </Text>
      </View>

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        transactions.slice(0, 8).map((transaction, index) => {
          const tx = (transaction.transaction as AnyRecord | undefined) ?? {};
          return (
            <Card key={String(tx.id ?? transaction.id ?? `tx-${index}`)}>
              <CardBody>
                <Text style={styles.title}>{String(tx.id ?? transaction.id ?? "Unknown transaction")}</Text>
                <Text style={styles.meta}>Type: {String(tx.type ?? "UNKNOWN")}</Text>
                <Text style={styles.meta}>State: {String(tx.currentState ?? "UNKNOWN")}</Text>
              </CardBody>
            </Card>
          );
        })
      )}
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
  meta: {
    fontSize: 12,
    color: "#4B5563",
    marginTop: 4,
  },
});
