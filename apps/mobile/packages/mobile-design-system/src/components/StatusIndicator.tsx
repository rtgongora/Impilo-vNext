/**
 * StatusIndicator — Colored dot + label for sync/connection/entity status.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";

export type IndicatorStatus = "online" | "offline" | "syncing" | "error" | "warning" | "idle";

export interface StatusIndicatorProps {
  status: IndicatorStatus;
  label?: string;
  size?: "sm" | "md" | "lg";
  testID?: string;
}

const STATUS_COLORS: Record<IndicatorStatus, string> = {
  online: "#009739",
  offline: "#9E9E9E",
  syncing: "#2196F3",
  error: "#F44336",
  warning: "#FF9800",
  idle: "#BDBDBD",
};

export function StatusIndicator({
  status,
  label,
  size = "md",
  testID,
}: StatusIndicatorProps) {
  const dotSize = size === "sm" ? 8 : size === "md" ? 10 : 14;

  return (
    <View
      testID={testID}
      accessibilityRole="summary"
      accessibilityLabel={label ?? status}
      style={styles.container}
    >
      <View
        style={{
          width: dotSize,
          height: dotSize,
          borderRadius: dotSize / 2,
          backgroundColor: STATUS_COLORS[status],
        }}
      />
      {label ? <Text>{label}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
});
