import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { fetchPrescriptions, requestRefill } from "../../services/prescriptionService";
import type { Prescription } from "../../types";

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ACTIVE: "default",
  PENDING: "secondary",
  DISPENSED: "secondary",
  EXPIRED: "destructive",
  CANCELLED: "outline",
};

export function PrescriptionsSection() {
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [refillInProgress, setRefillInProgress] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchPrescriptions({ size: 50 });
      setPrescriptions(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRefill = useCallback(async (rxId: string) => {
    setRefillInProgress(rxId);
    try {
      await requestRefill(rxId);
      load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setRefillInProgress(null);
    }
  }, [load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  if (prescriptions.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <View style={styles.emptyIconCircle}>
          <Ionicons name="receipt-outline" size={32} color={colors.gray[300]} />
        </View>
        <Text style={styles.emptyTitle}>No prescriptions</Text>
        <Text style={styles.emptyMessage}>Your prescriptions will appear here after a visit</Text>
      </View>
    );
  }

  return (
    <View testID="prescriptions-section" style={styles.container}>
      <Text style={styles.sectionLabel}>MY MEDICATIONS</Text>
      {prescriptions.map((rx) => (
        <View key={rx.id} style={styles.rxCard}>
          <View testID={`prescription-${rx.id}`} style={styles.rxRow}>
            <View style={styles.iconCircle}>
              <Ionicons name="medical" size={20} color="#1E40AF" />
            </View>
            <View style={styles.rxMeta}>
              <View style={styles.nameRow}>
                <Text style={styles.medicationName}>{rx.medicationName}</Text>
                <View style={styles.badgeStack}>
                  <Badge variant={STATUS_VARIANT[rx.status] ?? "outline"}>
                    {rx.status}
                  </Badge>
                </View>
              </View>
              <Text style={styles.dosageText}>
                {`${rx.dosage} \u2022 ${rx.frequency} \u2022 ${rx.duration}`}
              </Text>
              <View style={styles.metaRow}>
                <Ionicons name="person-outline" size={12} color={colors.gray[400]} />
                <Text style={styles.metaText}>
                  {`${rx.prescribedBy} \u2022 ${rx.facilityName}`}
                </Text>
              </View>
              <View style={styles.refillRow}>
                <View style={styles.refillCountPill}>
                  <Ionicons name="refresh-outline" size={11} color={colors.gray[500]} />
                  <Text style={styles.refillCountText}>{`${rx.refillsRemaining} refills left`}</Text>
                </View>
                {rx.lastFilledAt ? (
                  <Text style={styles.lastFilledText}>
                    {`Filled ${new Date(rx.lastFilledAt).toLocaleDateString()}`}
                  </Text>
                ) : null}
              </View>
              {rx.status === "ACTIVE" && rx.refillsRemaining > 0 ? (
                <View style={styles.refillButtonWrapper}>
                  <Button
                    title={refillInProgress === rx.id ? "Requesting..." : "Request Refill"}
                    variant="secondary"
                    size="sm"
                    onPress={() => handleRefill(rx.id)}
                    disabled={refillInProgress === rx.id}
                    testID={`refill-${rx.id}`}
                  />
                </View>
              ) : null}
            </View>
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  sectionLabel: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[500],
    letterSpacing: 0.8,
    textTransform: "uppercase",
    marginBottom: 4,
  },
  emptyContainer: {
    alignItems: "center",
    paddingVertical: 48,
    gap: 12,
  },
  emptyIconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: colors.gray[100],
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 4,
  },
  emptyTitle: {
    fontSize: 17,
    fontWeight: "600",
    color: colors.gray[700],
  },
  emptyMessage: {
    fontSize: 14,
    color: colors.gray[400],
    textAlign: "center",
    paddingHorizontal: 24,
  },
  rxCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  rxRow: {
    flexDirection: "row",
    gap: 12,
    alignItems: "flex-start",
  },
  iconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    marginTop: 2,
  },
  rxMeta: {
    flex: 1,
    gap: 5,
  },
  nameRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  medicationName: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.gray[900],
    flex: 1,
  },
  badgeStack: {
    flexShrink: 0,
    marginTop: 1,
  },
  dosageText: {
    fontSize: 13,
    color: colors.gray[700],
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  metaText: {
    fontSize: 12,
    color: colors.gray[400],
  },
  refillRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginTop: 2,
  },
  refillCountPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.gray[100],
    borderRadius: 10,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  refillCountText: {
    fontSize: 11,
    color: colors.gray[500],
    fontWeight: "600",
  },
  lastFilledText: {
    fontSize: 11,
    color: colors.gray[400],
  },
  refillButtonWrapper: {
    marginTop: 6,
    alignSelf: "flex-start",
  },
});
