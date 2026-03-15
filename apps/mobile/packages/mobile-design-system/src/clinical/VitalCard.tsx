/**
 * VitalCard — Displays a single vital sign measurement.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";

export interface VitalCardProps {
  label: string;
  value: string | number;
  unit: string;
  timestamp?: string;
  status?: "normal" | "warning" | "critical";
  icon?: React.ReactNode;
  testID?: string;
}

const STATUS_COLORS = {
  normal: "#4CAF50",
  warning: "#FF9800",
  critical: "#F44336",
};

export function VitalCard({
  label,
  value,
  unit,
  timestamp,
  status = "normal",
  icon,
  testID,
}: VitalCardProps) {
  return (
    <View
      testID={testID}
      accessibilityRole="summary"
      accessibilityLabel={`${label}: ${value} ${unit}`}
      style={[styles.container, { borderLeftColor: STATUS_COLORS[status] }]}
    >
      <View style={styles.headerRow}>
        {icon ?? null}
        <Text style={styles.label}>{label}</Text>
      </View>
      <Text style={styles.value}>
        {`${value} `}
        <Text style={styles.unit}>{unit}</Text>
      </Text>
      {timestamp ? <Text style={styles.timestamp}>{timestamp}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderLeftWidth: 3,
    padding: 12,
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  label: {
    fontWeight: "600",
  },
  value: {
    fontSize: 24,
    fontWeight: "700",
  },
  unit: {
    fontSize: 14,
    fontWeight: "400",
  },
  timestamp: {
    fontSize: 12,
    color: "#757575",
  },
});
