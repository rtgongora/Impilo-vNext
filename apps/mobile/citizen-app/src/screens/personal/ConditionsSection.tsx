/**
 * ConditionsSection — View and manage medical conditions.
 * Maps to web: /ehr/[patientId]/conditions
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
import type { Condition } from "../types";

const STATUS_VARIANT: Record<string, "default" | "warning" | "success"> = {
  ACTIVE: "warning",
  RESOLVED: "success",
  INACTIVE: "default",
};

interface ConditionsSectionProps {
  patientId?: string;
}

export function ConditionsSection({ patientId }: ConditionsSectionProps) {
  const [conditions, setConditions] = useState<Condition[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [filter, setFilter] = useState<"ALL" | "ACTIVE" | "RESOLVED">("ALL");

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // TODO: Wire to backend service
      // const result = await fetchConditions({ patientId, status: filter });
      // setConditions(result.items);
      setConditions([]);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [patientId, filter]);

  useEffect(() => {
    load();
  }, [load]);

  const activeCount = conditions.filter(c => c.clinicalStatus === "ACTIVE").length;
  const resolvedCount = conditions.filter(c => c.clinicalStatus === "RESOLVED").length;

  const filteredConditions = filter === "ALL" 
    ? conditions 
    : conditions.filter(c => c.clinicalStatus === filter);

  return (
    <Screen>
      <Header
        title="Conditions"
        rightElement={
          activeCount > 0 ? (
            <Badge variant="warning">{activeCount} active</Badge>
          ) : null
        }
      />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Filter Tabs */}
        <View style={styles.filterTabs}>
          <Pressable 
            style={[styles.filterTab, filter === "ALL" && styles.filterTabActive]}
            onPress={() => setFilter("ALL")}
          >
            <Text style={[styles.filterTabText, filter === "ALL" && styles.filterTabTextActive]}>
              All ({conditions.length})
            </Text>
          </Pressable>
          <Pressable 
            style={[styles.filterTab, filter === "ACTIVE" && styles.filterTabActive]}
            onPress={() => setFilter("ACTIVE")}
          >
            <Text style={[styles.filterTabText, filter === "ACTIVE" && styles.filterTabTextActive]}>
              Active ({activeCount})
            </Text>
          </Pressable>
          <Pressable 
            style={[styles.filterTab, filter === "RESOLVED" && styles.filterTabActive]}
            onPress={() => setFilter("RESOLVED")}
          >
            <Text style={[styles.filterTabText, filter === "RESOLVED" && styles.filterTabTextActive]}>
              Resolved ({resolvedCount})
            </Text>
          </Pressable>
        </View>

        {/* Conditions List */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={load} />
        ) : filteredConditions.length === 0 ? (
          <EmptyState
            title="No conditions recorded"
            description="Your medical conditions will appear here. Your healthcare provider adds conditions during visits."
          />
        ) : (
          filteredConditions.map((condition) => (
            <Card key={condition.id}>
              <CardHeader
                title={condition.code}
                subtitle={condition.codeSystem ? `${condition.codeSystem} • ${condition.code}` : undefined}
                rightElement={
                  <Badge variant={STATUS_VARIANT[condition.clinicalStatus] || "default"}>
                    {condition.clinicalStatus}
                  </Badge>
                }
              />
              <CardBody>
                <View style={styles.detailRow}>
                  <Text style={styles.detailLabel}>Onset</Text>
                  <Text style={styles.detailValue}>
                    {condition.onsetDate 
                      ? new Date(condition.onsetDate).toLocaleDateString()
                      : "Unknown"}
                  </Text>
                </View>
                {condition.category && (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Category</Text>
                    <Text style={styles.detailValue}>{condition.category}</Text>
                  </View>
                )}
                {condition.severity && (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Severity</Text>
                    <Text style={styles.detailValue}>{condition.severity}</Text>
                  </View>
                )}
                {condition.notes && (
                  <View style={styles.detailRow}>
                    <Text style={styles.detailLabel}>Notes</Text>
                    <Text style={styles.detailValue}>{condition.notes}</Text>
                  </View>
                )}
              </CardBody>
            </Card>
          ))
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
  filterTabs: {
    flexDirection: "row",
    gap: 8,
    backgroundColor: "#F3F4F6",
    padding: 4,
    borderRadius: 8,
  },
  filterTab: {
    flex: 1,
    paddingVertical: 8,
    alignItems: "center",
    borderRadius: 6,
  },
  filterTabActive: {
    backgroundColor: "#FFFFFF",
  },
  filterTabText: {
    fontSize: 13,
    color: "#6B7280",
  },
  filterTabTextActive: {
    color: "#1F2937",
    fontWeight: "600",
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