import React from "react";
import { View, StyleSheet } from "react-native";

export interface ProgressProps {
  value: number;
  max?: number;
  size?: "sm" | "md" | "lg";
  colorScheme?: "default" | "success" | "warning" | "destructive";
  testID?: string;
}

const HEIGHT_MAP = {
  sm: 6,
  md: 8,
  lg: 12,
} as const;

const COLOR_MAP = {
  default: "#2563EB",
  success: "#16A34A",
  warning: "#D97706",
  destructive: "#DC2626",
} as const;

export function Progress({
  value,
  max = 1,
  size = "md",
  colorScheme = "default",
  testID,
}: ProgressProps) {
  const safeMax = max <= 0 ? 1 : max;
  const ratio = Math.max(0, Math.min(1, value / safeMax));
  return (
    <View
      testID={testID}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: safeMax, now: Math.min(Math.max(value, 0), safeMax) }}
      style={[styles.track, { height: HEIGHT_MAP[size] }]}
    >
      <View
        style={[
          styles.fill,
          {
            width: `${ratio * 100}%`,
            backgroundColor: COLOR_MAP[colorScheme],
          },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: "100%",
    backgroundColor: "#E5E7EB",
    borderRadius: 999,
    overflow: "hidden",
  },
  fill: {
    height: "100%",
    borderRadius: 999,
  },
});
