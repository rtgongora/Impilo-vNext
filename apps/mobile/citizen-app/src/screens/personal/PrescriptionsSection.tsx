/**
 * PrescriptionsSection — View prescriptions and request refills.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet } from "react-native";
import {
  Card,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
  RxCard,
} from "@impilo/mobile-design-system";
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
      <EmptyState
        title="No prescriptions"
        message="Your prescriptions will appear here after a visit"
      />
    );
  }

  return (
    <View testID="prescriptions-section" style={styles.container}>
      <Text style={styles.heading}>Prescriptions</Text>
      {prescriptions.map((rx) => (
        <Card key={rx.id}>
          <CardBody>
            <View testID={`prescription-${rx.id}`}>
              <View style={styles.prescriptionRow}>
                <View>
                  <View style={styles.badgeRow}>
                    <Text style={styles.medicationName}>{rx.medicationName}</Text>
                    <Badge variant={STATUS_VARIANT[rx.status] ?? "outline"}>
                      {rx.status}
                    </Badge>
                  </View>
                  <Text style={styles.dosageText}>
                    {`${rx.dosage} \u2022 ${rx.frequency} \u2022 ${rx.duration}`}
                  </Text>
                  <Text style={styles.prescribedByText}>
                    {`Prescribed by ${rx.prescribedBy} at ${rx.facilityName}`}
                  </Text>
                  <Text style={styles.quantityText}>
                    {`Qty: ${rx.quantity} \u2022 Refills remaining: ${rx.refillsRemaining}`}
                  </Text>
                  {rx.lastFilledAt ? (
                    <Text style={styles.lastFilledText}>
                      {`Last filled: ${new Date(rx.lastFilledAt).toLocaleDateString()}`}
                    </Text>
                  ) : null}
                </View>
                {rx.status === "ACTIVE" && rx.refillsRemaining > 0 ? (
                  <Button
                    title={refillInProgress === rx.id ? "Requesting..." : "Request Refill"}
                    variant="secondary"
                    size="sm"
                    onPress={() => handleRefill(rx.id)}
                    disabled={refillInProgress === rx.id}
                    testID={`refill-${rx.id}`}
                  />
                ) : null}
              </View>
            </View>
          </CardBody>
        </Card>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  prescriptionRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 4,
  },
  medicationName: {
    fontWeight: "700",
    fontSize: 15,
  },
  dosageText: {
    fontSize: 14,
    color: "#374151",
    marginVertical: 2,
  },
  prescribedByText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  quantityText: {
    fontSize: 13,
    color: "#9CA3AF",
    marginVertical: 2,
  },
  lastFilledText: {
    fontSize: 12,
    color: "#9CA3AF",
    marginVertical: 2,
  },
});
