/**
 * RecordsScreen — View medical records and clinical documents.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, StyleSheet, FlatList, Pressable, ScrollView } from "react-native";
import {
  Card,
  CardBody,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { getRecords } from "../../services/recordsService";
import type { MedicalRecord, RecordType } from "../../types";

type FilterType = "ALL" | RecordType;

const FILTER_OPTIONS: { label: string; value: FilterType }[] = [
  { label: "All", value: "ALL" },
  { label: "Lab Reports", value: "LAB_REPORT" },
  { label: "Clinical Notes", value: "CLINICAL_NOTE" },
  { label: "Discharge Summaries", value: "DISCHARGE_SUMMARY" },
  { label: "Imaging", value: "IMAGING" },
];

const TYPE_COLORS: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  LAB_REPORT: "default",
  CLINICAL_NOTE: "secondary",
  DISCHARGE_SUMMARY: "destructive",
  IMAGING: "outline",
};

export function RecordsScreen() {
  const [records, setRecords] = useState<MedicalRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [activeFilter, setActiveFilter] = useState<FilterType>("ALL");

  const load = useCallback(async (filter: FilterType) => {
    setIsLoading(true);
    setError(null);
    try {
      const params: { type?: RecordType; size?: number } = { size: 50 };
      if (filter !== "ALL") {
        params.type = filter;
      }
      const result = await getRecords(params);
      setRecords(result.items);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load(activeFilter);
  }, [activeFilter, load]);

  const handleFilterChange = useCallback((filter: FilterType) => {
    setActiveFilter(filter);
  }, []);

  const renderFilterChip = ({ item }: { item: (typeof FILTER_OPTIONS)[number] }) => (
    <Pressable
      testID={`filter-${item.value}`}
      onPress={() => handleFilterChange(item.value)}
      style={[
        styles.filterChip,
        activeFilter === item.value && styles.filterChipActive,
      ]}
    >
      <Text
        style={[
          styles.filterChipText,
          activeFilter === item.value && styles.filterChipTextActive,
        ]}
      >
        {item.label}
      </Text>
    </Pressable>
  );

  if (error) return <ErrorState title="Error" message={error.message} onRetry={() => load(activeFilter)} />;

  return (
    <View testID="records-screen" style={styles.container}>
      <Text style={styles.heading}>Medical Records</Text>

      <View style={styles.filterContainer}>
        <FlatList
          horizontal
          data={FILTER_OPTIONS}
          keyExtractor={(item) => item.value}
          renderItem={renderFilterChip}
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterList}
        />
      </View>

      {isLoading ? (
        <LoadingSpinner size="md" />
      ) : records.length === 0 ? (
        <EmptyState
          title="No records found"
          message="Your clinical documents will appear here after visits"
        />
      ) : (
        <ScrollView style={styles.scrollArea} showsVerticalScrollIndicator={false}>
          <View style={styles.recordsList}>
            {records.map((record) => (
              <Card key={record.id}>
                <CardBody>
                  <View testID={`record-${record.id}`} style={styles.recordRow}>
                    <View style={styles.recordContent}>
                      <View style={styles.badgeRow}>
                        <Text style={styles.recordTitle}>{record.title}</Text>
                        <Badge variant={TYPE_COLORS[record.type] ?? "outline"}>
                          {record.type.replace(/_/g, " ")}
                        </Badge>
                      </View>
                      <Text style={styles.dateText}>
                        {new Date(record.date).toLocaleDateString()}
                      </Text>
                      <Text style={styles.providerText}>{record.provider}</Text>
                      <Text style={styles.facilityText}>{record.facilityName}</Text>
                      {record.summary ? (
                        <Text style={styles.summaryText} numberOfLines={2}>
                          {record.summary}
                        </Text>
                      ) : null}
                    </View>
                  </View>
                </CardBody>
              </Card>
            ))}
          </View>
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    gap: 12,
  },
  heading: {
    fontSize: 18,
    fontWeight: "600",
  },
  filterContainer: {
    marginBottom: 4,
  },
  filterList: {
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: "#F3F4F6",
  },
  filterChipActive: {
    backgroundColor: "#2563EB",
  },
  filterChipText: {
    fontSize: 13,
    fontWeight: "500",
    color: "#6B7280",
  },
  filterChipTextActive: {
    color: "#FFFFFF",
  },
  scrollArea: {
    flex: 1,
  },
  recordsList: {
    gap: 12,
  },
  recordRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  recordContent: {
    flex: 1,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
    marginBottom: 4,
    flexWrap: "wrap",
  },
  recordTitle: {
    fontWeight: "700",
    fontSize: 15,
  },
  dateText: {
    fontSize: 14,
    color: "#374151",
    marginVertical: 2,
  },
  providerText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  facilityText: {
    fontSize: 13,
    color: "#6B7280",
    marginVertical: 2,
  },
  summaryText: {
    fontSize: 13,
    color: "#9CA3AF",
    marginTop: 4,
  },
});
