/**
 * Badge — Status indicator label with full variant styling.
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

const VARIANT_MAP: Record<BadgeVariant, { bg: string; text: string; border?: string }> = {
  default:     { bg: "#E5E7EB", text: "#374151" },
  primary:     { bg: "#D1FAE5", text: "#065F46" },
  success:     { bg: "#D1FAE5", text: "#065F46" },
  warning:     { bg: "#FEF3C7", text: "#92400E" },
  error:       { bg: "#FEE2E2", text: "#991B1B" },
  destructive: { bg: "#FEE2E2", text: "#991B1B" },
  info:        { bg: "#DBEAFE", text: "#1E40AF" },
  secondary:   { bg: "#EDE9FE", text: "#5B21B6" },
  outline:     { bg: "transparent", text: "#374151", border: "#D1D5DB" },
};

export function Badge({
  label,
  children,
  variant = "default",
  size = "md",
  icon,
  testID,
}: BadgeProps) {
  const resolvedLabel = typeof children === "string" ? children : (typeof children === "number" ? String(children) : label ?? "");
  const vs = VARIANT_MAP[variant];
  const isSmall = size === "sm";

  return (
    <View
      testID={testID}
      accessibilityRole="summary"
      accessibilityLabel={resolvedLabel}
      style={[
        styles.container,
        {
          backgroundColor: vs.bg,
          borderRadius: 999,
          paddingVertical: isSmall ? 2 : 3,
          paddingHorizontal: isSmall ? 7 : 9,
          borderWidth: vs.border ? 1 : 0,
          borderColor: vs.border ?? "transparent",
        },
      ]}
    >
      {icon ?? null}
      <Text
        style={[
          styles.label,
          {
            color: vs.text,
            fontSize: isSmall ? 10 : 12,
            fontWeight: "600",
          },
        ]}
      >
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
    alignSelf: "flex-start",
  },
  label: {
    letterSpacing: 0.2,
  },
});
