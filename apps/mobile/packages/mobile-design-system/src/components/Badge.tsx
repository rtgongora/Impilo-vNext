/**
 * Badge — Status indicator label.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";

export type BadgeVariant = "default" | "primary" | "success" | "warning" | "error" | "info";
export type BadgeSize = "sm" | "md";

export interface BadgeProps {
  label: string;
  variant?: BadgeVariant;
  size?: BadgeSize;
  icon?: React.ReactNode;
  testID?: string;
}

export function Badge({
  label,
  variant = "default",
  size = "md",
  icon,
  testID,
}: BadgeProps) {
  return (
    <View
      testID={testID}
      accessibilityRole="summary"
      accessibilityLabel={label}
      style={styles.container}
    >
      {icon ?? null}
      <Text style={size === "sm" ? styles.labelSm : styles.labelMd}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  labelSm: {
    fontSize: 11,
  },
  labelMd: {
    fontSize: 13,
  },
});
