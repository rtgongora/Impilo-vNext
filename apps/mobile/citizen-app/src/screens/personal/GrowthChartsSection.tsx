/**
 * GrowthChartsSection — Track child growth metrics.
 * Maps to web: /personal/growth-charts
 * Priority: LOW
 */

import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView, Dimensions } from "react-native";
import { Screen, Header, Card, CardBody, Select, EmptyState, LoadingSpinner } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import type { GrowthMeasurement } from "../../types";

const CHART_TYPES = [
  { value: "WEIGHT_FOR_AGE", label: "Weight for Age" },
  { value: "HEIGHT_FOR_AGE", label: "Height for Age" },
  { value: "BMI_FOR_AGE", label: "BMI for Age" },
  { value: "HEAD_CIRCUMFERENCE", label: "Head Circumference" },
];

export function GrowthChartsSection() {
  const [chartType, setChartType] = useState("WEIGHT_FOR_AGE");

  const { data: measurements = [], isLoading } = useQuery({
    queryKey: ["growth-measurements", chartType],
    queryFn: async () => {
      return [];
    },
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <Screen>
      <Header title="Growth Charts" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        <Select
          label="Chart Type"
          options={CHART_TYPES}
          value={chartType}
          onChange={(v) => setChartType(v)}
        />

        {measurements.length === 0 ? (
          <EmptyState
            title="No measurements"
            description="Growth measurements will appear here once recorded."
          />
        ) : (
          <>
            <Card style={styles.chartCard}>
              <CardBody>
                <Text style={styles.chartTitle}>
                  {CHART_TYPES.find((t) => t.value === chartType)?.label}
                </Text>
                <View style={styles.chartPlaceholder}>
                  <Text style={styles.placeholderText}>📊</Text>
                  <Text style={styles.placeholderLabel}>Growth Chart</Text>
                </View>
              </CardBody>
            </Card>

            <Text style={styles.sectionTitle}>Recent Measurements</Text>
            {measurements.map((m) => (
              <MeasurementCard key={m.id} measurement={m} />
            ))}
          </>
        )}
      </ScrollView>
    </Screen>
  );
}

function MeasurementCard({ measurement }: { measurement: GrowthMeasurement }) {
  const value =
    measurement.weightKg !== undefined
      ? `${measurement.weightKg} kg`
      : measurement.heightCm !== undefined
      ? `${measurement.heightCm} cm`
      : measurement.headCircumferenceCm !== undefined
      ? `${measurement.headCircumferenceCm} cm`
      : measurement.bmi !== undefined
      ? `${measurement.bmi}`
      : "-";

  return (
    <Card style={styles.measurementCard}>
      <CardBody>
        <Text style={styles.measurementValue}>{value}</Text>
        <Text style={styles.measurementMeta}>
          Age: {measurement.ageMonths} months • {new Date(measurement.date).toLocaleDateString()}
        </Text>
        {measurement.percentile !== undefined && (
          <Text style={styles.percentile}>Percentile: {measurement.percentile}%</Text>
        )}
      </CardBody>
    </Card>
  );
}

const styles = StyleSheet.create({
  content: { flex: 1 },
  contentContainer: { padding: 16, gap: 16 },
  chartCard: { marginTop: 8 },
  chartTitle: { fontSize: 16, fontWeight: "600", marginBottom: 12 },
  chartPlaceholder: {
    height: 200,
    backgroundColor: "#F3F4F6",
    borderRadius: 8,
    justifyContent: "center",
    alignItems: "center",
  },
  placeholderText: { fontSize: 48, marginBottom: 8 },
  placeholderLabel: { color: "#6B7280" },
  sectionTitle: { fontSize: 16, fontWeight: "600", marginTop: 16 },
  measurementCard: { marginTop: 8 },
  measurementValue: { fontSize: 20, fontWeight: "600" },
  measurementMeta: { fontSize: 14, color: "#6B7280", marginTop: 4 },
  percentile: { fontSize: 14, color: "#2563EB", marginTop: 4 },
});