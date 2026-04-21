/**
 * AllergiesSection — View and manage patient allergies.
 * Maps to web: /ehr/[patientId]/allergies
 * Priority: HIGH (patient safety)
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, Pressable, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import type { Allergy } from "../../types";

const SEVERITY_VARIANT: Record<string, "default" | "warning" | "destructive"> = {
  SEVERE: "destructive",
  MODERATE: "warning",
  MILD: "default",
};

interface AllergiesSectionProps {
  patientId?: string;
}

export function AllergiesSection({ patientId }: AllergiesSectionProps) {
  const [allergies, setAllergies] = useState<Allergy[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // TODO: Wire to backend service
      // const result = await fetchAllergies({ patientId });
      // setAllergies(result.items);
      setAllergies([]);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    load();
  }, [load]);

  const allergyCount = allergies.length;
  const severeCount = allergies.filter(a => a.severity === "SEVERE").length;

  return (
    <Screen>
      <Header
        title="Allergies"
        rightElement={
          severeCount > 0 ? (
            <Badge variant="destructive">{severeCount} severe</Badge>
          ) : null
        }
      />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Summary Card */}
        <Card>
          <CardBody>
            <View style={styles.summaryRow}>
              <View style={styles.summaryItem}>
                <Text style={styles.summaryNumber}>{allergyCount}</Text>
                <Text style={styles.summaryLabel}>Total</Text>
              </View>
              <View style={styles.summaryItem}>
                <Text style={[styles.summaryNumber, styles.severeNumber]}>{severeCount}</Text>
                <Text style={styles.summaryLabel}>Severe</Text>
              </View>
            </View>
          </CardBody>
        </Card>

        {/* Allergies List */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={load} />
        ) : allergies.length === 0 ? (
          <EmptyState
            title="No allergies recorded"
            description="No known allergies on file. Your healthcare provider can add allergies during visits."
            actionLabel="Request Addition"
            onAction={() => {}}
          />
        ) : (
          allergies.map((allergy) => (
            <Card key={allergy.id}>
              <CardHeader
                title={allergy.allergen}
                rightElement={
                  <Badge variant={SEVERITY_VARIANT[allergy.severity] || "default"}>
                    {allergy.severity}
                  </Badge>
                }
              />
              <CardBody>
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Reaction</Text>
                  <Text style={styles.detailValue}>{allergy.reaction}</Text>
                </View>
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Recorded</Text>
                  <Text style={styles.detailValue}>
                    {new Date(allergy.recordedAt).toLocaleDateString()}
                  </Text>
                </View>
                {allergy.facilityName && (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Facility</Text>
                    <Text style={styles.detailValue}>{allergy.facilityName}</Text>
                  </View>
                )}
              </CardBody>
            </Card>
          ))
        )}

        {/* Add Allergy Prompt */}
        {allergies.length === 0 && (
          <Button
            title="Request Allergy Review"
            variant="secondary"
            onPress={() => {}}
          />
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    flex: 1,
  },
  contentContainer: {
    padding: 16,
    gap: 16,
  },
  summaryRow: {
    flexDirection: "row",
    justifyContent: "space-around",
  },
  summaryItem: {
    alignItems: "center",
  },
  summaryNumber: {
    fontSize: 28,
    fontWeight: "700",
    color: "#1F2937",
  },
  severeNumber: {
    color: "#DC2626",
  },
  summaryLabel: {
    fontSize: 12,
    color: "#6B7280",
    marginTop: 4,
  },
  detailRow: {
    paddingVertical: 4,
  },
  detailLabel: {
    fontSize: 12,
    color: "#6B7280",
  },
  detailValue: {
    fontSize: 14,
    color: "#1F2937",
  },
});
