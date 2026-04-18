/**
 * ImmunizationsSection — View immunization history and upcoming vaccines.
 * Maps to web: /ehr/[patientId]/immunizations
 * Priority: HIGH (public health compliance)
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
import type { Immunization } from "../types";

const STATUS_VARIANT: Record<string, "default" | "warning" | "success"> = {
  COMPLETED: "success",
  DUE: "warning",
  OVERDUE: "destructive",
  SCHEDULED: "default",
};

interface ImmunizationsSectionProps {
  patientId?: string;
}

export function ImmunizationsSection({ patientId }: ImmunizationsSectionProps) {
  const [immunizations, setImmunizations] = useState<Immunization[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // TODO: Wire to backend service
      // const result = await fetchImmunizations({ patientId });
      // setImmunizations(result.items);
      setImmunizations([]);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    load();
  }, [load]);

  const completedCount = immunizations.filter(i => i.status === "COMPLETED").length;
  const dueCount = immunizations.filter(i => i.status === "DUE" || i.status === "OVERDUE").length;
  const upcomingCount = immunizations.filter(i => i.status === "SCHEDULED").length;

  return (
    <Screen>
      <Header
        title="Immunizations"
        rightElement={
          dueCount > 0 ? (
            <Badge variant="warning">{dueCount} due</Badge>
          ) : null
        }
      />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Summary Cards */}
        <View style={styles.summaryGrid}>
          <Card style={styles.summaryCard}>
            <CardBody>
              <Text style={styles.summaryNumber}>{completedCount}</Text>
              <Text style={styles.summaryLabel}>Completed</Text>
            </CardBody>
          </Card>
          <Card style={styles.summaryCard}>
            <CardBody>
              <Text style={[styles.summaryNumber, styles.dueNumber]}>{dueCount}</Text>
              <Text style={styles.summaryLabel}>Due Now</Text>
            </CardBody>
          </Card>
          <Card style={styles.summaryCard}>
            <CardBody>
              <Text style={styles.summaryNumber}>{upcomingCount}</Text>
              <Text style={styles.summaryLabel}>Scheduled</Text>
            </CardBody>
          </Card>
        </View>

        {/* Immunizations List */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={load} />
        ) : immunizations.length === 0 ? (
          <EmptyState
            title="No immunizations recorded"
            description="Your immunization history will appear here. vaccinations protect both you and your community."
          />
        ) : (
          immunizations.map((immunization) => (
            <Card key={immunization.id}>
              <CardHeader
                title={immunization.vaccineName}
                rightElement={
                  <Badge variant={STATUS_VARIANT[immunization.status] || "default"}>
                    {immunization.status}
                  </Badge>
                }
              />
              <CardBody>
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Date</Text>
                  <Text style={styles.detailValue}>
                    {immunization.administeredDate 
                      ? new Date(immunization.administeredDate).toLocaleDateString()
                      : immunization.scheduledDate
                        ? new Date(immunization.scheduledDate).toLocaleDateString()
                        : "Pending"}
                  </Text>
                </View>
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Provider</Text>
                  <Text style={styles.detailValue}>{immunization.site || "Unknown"}</Text>
                </View>
                {immunization.lotNumber && (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Lot #</Text>
                    <Text style={styles.detailValue}>{immunization.lotNumber}</Text>
                  </View>
                )}
                {immunization.doseNumber && (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Dose</Text>
                    <Text style={styles.detailValue}>
                      {immunization.doseNumber} of {immunization.seriesDoses}
                    </Text>
                  </View>
                )}
              </CardBody>
            </Card>
          ))
        )}

        {/* Schedule New */}
        <Button
          title="Schedule Vaccination"
          variant="secondary"
          onPress={() => {}}
        />
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
  summaryGrid: {
    flexDirection: "row",
    gap: 8,
  },
  summaryCard: {
    flex: 1,
    padding: 0,
  },
  summaryNumber: {
    fontSize: 24,
    fontWeight: "700",
    color: "#1F2937",
    textAlign: "center",
  },
  dueNumber: {
    color: "#F59E0B",
  },
  summaryLabel: {
    fontSize: 11,
    color: "#6B7280",
    textAlign: "center",
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