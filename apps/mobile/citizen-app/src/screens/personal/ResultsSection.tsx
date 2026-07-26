import React, { useState, useEffect, useCallback } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
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
      <View style={styles.emptyContainer}>
        <View style={styles.emptyIconCircle}>
          <Ionicons name="flask-outline" size={32} color={colors.gray[300]} />
        </View>
        <Text style={styles.emptyTitle}>No results</Text>
        <Text style={styles.emptyMessage}>Your lab results and reports will appear here</Text>
      </View>
    );
  }

  return (
    <View testID="results-section" style={styles.container}>
      <Text style={styles.sectionLabel}>LAB RESULTS</Text>
      {results.map((lab) => {
        const isExpanded = expandedId === lab.id;
        return (
          <View key={lab.id} style={styles.resultCard}>
            <Pressable
              testID={`result-${lab.id}`}
              onPress={() => setExpandedId(isExpanded ? null : lab.id)}
              style={({ pressed }) => [{ opacity: pressed ? 0.85 : 1 }]}
            >
              <View style={styles.resultRow}>
                <View style={styles.iconCircle}>
                  <Ionicons name="flask" size={20} color="#D97706" />
                </View>
                <View style={styles.resultMeta}>
                  <View style={styles.nameRow}>
                    <Text style={styles.testName}>{lab.testName}</Text>
                    <Badge variant={STATUS_VARIANT[lab.status] ?? "outline"}>
                      {lab.status}
                    </Badge>
                  </View>
                  <View style={styles.metaRow}>
                    <Ionicons name="grid-outline" size={12} color={colors.gray[400]} />
                    <Text style={styles.metaText}>{lab.category}</Text>
                  </View>
                  <View style={styles.metaRow}>
                    <Ionicons name="location-outline" size={12} color={colors.gray[400]} />
                    <Text style={styles.metaText}>{lab.facilityName}</Text>
                  </View>
                  <View style={styles.metaRow}>
                    <Ionicons name="person-outline" size={12} color={colors.gray[400]} />
                    <Text style={styles.metaText}>{`Ordered by ${lab.orderedBy}`}</Text>
                  </View>
                </View>
                <Ionicons
                  name={isExpanded ? "chevron-up" : "chevron-down"}
                  size={16}
                  color={colors.gray[400]}
                  style={styles.chevron}
                />
              </View>

              {isExpanded ? (
                <View style={styles.expandedBox}>
                  {lab.value ? (
                    <View style={styles.expandedRow}>
                      <Ionicons name="analytics-outline" size={14} color="#059669" />
                      <Text style={styles.expandedLabel}>Value</Text>
                      <Text style={styles.expandedValue}>{`${lab.value} ${lab.unit ?? ""}`}</Text>
                    </View>
                  ) : null}
                  {lab.referenceRange ? (
                    <View style={styles.expandedRow}>
                      <Ionicons name="swap-horizontal-outline" size={14} color={colors.gray[500]} />
                      <Text style={styles.expandedLabel}>Reference</Text>
                      <Text style={styles.expandedValueSecondary}>{lab.referenceRange}</Text>
                    </View>
                  ) : null}
                  {lab.interpretation ? (
                    <View style={styles.expandedRow}>
                      <Ionicons name="document-text-outline" size={14} color={colors.gray[500]} />
                      <Text style={styles.expandedLabel}>Interpretation</Text>
                      <Text style={styles.expandedValue}>{lab.interpretation}</Text>
                    </View>
                  ) : null}
                  {lab.collectedAt ? (
                    <View style={styles.expandedRow}>
                      <Ionicons name="time-outline" size={14} color={colors.gray[400]} />
                      <Text style={styles.expandedLabel}>Collected</Text>
                      <Text style={styles.expandedValueTertiary}>{new Date(lab.collectedAt).toLocaleString()}</Text>
                    </View>
                  ) : null}
                  {lab.resultAt ? (
                    <View style={styles.expandedRow}>
                      <Ionicons name="checkmark-circle-outline" size={14} color={colors.gray[400]} />
                      <Text style={styles.expandedLabel}>Result</Text>
                      <Text style={styles.expandedValueTertiary}>{new Date(lab.resultAt).toLocaleString()}</Text>
                    </View>
                  ) : null}
                </View>
              ) : null}
            </Pressable>
          </View>
        );
      })}
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
  resultCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
  resultRow: {
    flexDirection: "row",
    gap: 12,
    alignItems: "flex-start",
  },
  iconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.ui.warning.light,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    marginTop: 2,
  },
  resultMeta: {
    flex: 1,
    gap: 4,
  },
  nameRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    marginBottom: 2,
  },
  testName: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.gray[900],
    flex: 1,
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
  chevron: {
    marginTop: 2,
    flexShrink: 0,
  },
  expandedBox: {
    marginTop: 12,
    padding: 12,
    backgroundColor: "#F0FDF4",
    borderRadius: 10,
    gap: 8,
  },
  expandedRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  expandedLabel: {
    fontSize: 12,
    fontWeight: "600",
    color: colors.gray[500],
    width: 90,
  },
  expandedValue: {
    fontSize: 13,
    color: colors.gray[900],
    flex: 1,
  },
  expandedValueSecondary: {
    fontSize: 13,
    color: colors.gray[500],
    flex: 1,
  },
  expandedValueTertiary: {
    fontSize: 12,
    color: colors.gray[400],
    flex: 1,
  },
});
