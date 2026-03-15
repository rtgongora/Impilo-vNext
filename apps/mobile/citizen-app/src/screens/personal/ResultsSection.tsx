/**
 * ResultsSection — View lab results and diagnostic reports.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import {
  Card,
  CardBody,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { fetchLabResults } from "../../services/labResultService";
import type { LabResult } from "../../types";

const STATUS_VARIANT: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ORDERED: "outline",
  COLLECTED: "secondary",
  PROCESSING: "default",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

export function ResultsSection() {
  const [results, setResults] = useState<LabResult[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fetchLabResults({ size: 50 });
      setResults(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (isLoading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Error" message={error.message} onRetry={load} />;

  if (results.length === 0) {
    return (
      <EmptyState
        title="No results"
        message="Your lab results and reports will appear here"
      />
    );
  }

  return (
    <View testID="results-section" style={styles.container}>
      <Text style={styles.heading}>Lab Results & Reports</Text>
      {results.map((lab) => (
        <Card key={lab.id}>
          <CardBody>
            <TouchableOpacity
              testID={`result-${lab.id}`}
              onPress={() => setExpandedId(expandedId === lab.id ? null : lab.id)}
              activeOpacity={0.7}
            >
              <View style={styles.resultRow}>
                <View>
                  <View style={styles.badgeRow}>
                    <Text style={styles.boldText}>{lab.testName}</Text>
                    <Badge variant={STATUS_VARIANT[lab.status] ?? "outline"}>
                      {lab.status}
                    </Badge>
                  </View>
                  <Text style={styles.categoryText}>
                    {`${lab.category} \u2022 ${lab.facilityName}`}
                  </Text>
                  <Text style={styles.orderedByText}>
                    {`Ordered by ${lab.orderedBy}`}
                  </Text>
                </View>
                <Text style={styles.chevronText}>
                  {expandedId === lab.id ? "\u25B2" : "\u25BC"}
                </Text>
              </View>

              {expandedId === lab.id ? (
                <View style={styles.expandedContainer}>
                  {lab.value ? (
                    <Text style={styles.detailText}>
                      {`Value: ${lab.value} ${lab.unit ?? ""}`}
                    </Text>
                  ) : null}
                  {lab.referenceRange ? (
                    <Text style={styles.detailTextSecondary}>
                      {`Reference Range: ${lab.referenceRange}`}
                    </Text>
                  ) : null}
                  {lab.interpretation ? (
                    <Text style={styles.detailText}>
                      {`Interpretation: ${lab.interpretation}`}
                    </Text>
                  ) : null}
                  {lab.collectedAt ? (
                    <Text style={styles.detailTextTertiary}>
                      {`Collected: ${new Date(lab.collectedAt).toLocaleString()}`}
                    </Text>
                  ) : null}
                  {lab.resultAt ? (
                    <Text style={styles.detailTextTertiary}>
                      {`Result: ${new Date(lab.resultAt).toLocaleString()}`}
                    </Text>
                  ) : null}
                </View>
              ) : null}
            </TouchableOpacity>
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
  resultRow: {
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
  boldText: {
    fontWeight: "700",
  },
  categoryText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  orderedByText: {
    fontSize: 12,
    color: "#9CA3AF",
    marginVertical: 2,
  },
  chevronText: {
    fontSize: 12,
    color: "#9CA3AF",
  },
  expandedContainer: {
    marginTop: 12,
    padding: 12,
    backgroundColor: "#F9FAFB",
    borderRadius: 8,
  },
  detailText: {
    marginVertical: 4,
    fontSize: 14,
  },
  detailTextSecondary: {
    marginVertical: 4,
    fontSize: 14,
    color: "#6B7280",
  },
  detailTextTertiary: {
    marginVertical: 4,
    fontSize: 13,
    color: "#9CA3AF",
  },
});
