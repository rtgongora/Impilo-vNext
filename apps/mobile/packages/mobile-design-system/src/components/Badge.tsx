/**
 * Badge — Status indicator label.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";

export type BadgeVariant =
  | "default"
  | "primary"
  | "success"
  | "warning"
  | "error"
  | "info"
  /**
   * Back-compat variants used by older app screens.
   * These currently render the same as "default".
   */
  | "secondary"
  | "outline"
  | "destructive";
export type BadgeSize = "sm" | "md";

export interface BadgeProps {
  label?: string;
  children?: React.ReactNode;
  variant?: BadgeVariant;
  size?: BadgeSize;
  icon?: React.ReactNode;
  testID?: string;
}

export function Badge({
  label,
  children,
  variant = "default",
  size = "md",
  icon,
  testID,
}: BadgeProps) {
  const resolvedLabel = typeof children === "string" ? children : label ?? "";
  return (
    <View
      testID={testID}
      accessibilityRole="summary"
      accessibilityLabel={resolvedLabel}
      style={styles.container}
    >
      {icon ?? null}
      <Text style={size === "sm" ? styles.labelSm : styles.labelMd}>
        {resolvedLabel}
      </Text>
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
