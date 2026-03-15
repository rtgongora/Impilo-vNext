/**
 * Button — Primary interaction component.
 *
 * Variants: primary, secondary, outline, ghost, destructive
 * Sizes: sm, md, lg
 * Accessibility: accessibilityRole="button", accessibilityLabel required
 */

import React from "react";
import { Pressable, Text, View, StyleSheet } from "react-native";

export type ButtonVariant = "primary" | "secondary" | "outline" | "ghost" | "destructive";
export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  size?: ButtonSize;
  disabled?: boolean;
  loading?: boolean;
  icon?: React.ReactNode;
  iconPosition?: "left" | "right";
  fullWidth?: boolean;
  accessibilityLabel?: string;
  testID?: string;
}

export function Button({
  title,
  onPress,
  variant = "primary",
  size = "md",
  disabled = false,
  loading = false,
  icon,
  iconPosition = "left",
  fullWidth = false,
  accessibilityLabel,
  testID,
}: ButtonProps) {
  const isDisabled = disabled || loading;

  return (
    <Pressable
      onPress={isDisabled ? undefined : onPress}
      disabled={isDisabled}
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      testID={testID}
      style={[
        styles.base,
        fullWidth ? styles.fullWidth : undefined,
        { opacity: isDisabled ? 0.5 : 1 },
      ]}
    >
      <View style={styles.content}>
        {icon && iconPosition === "left" ? icon : null}
        <Text style={styles.label}>{loading ? "Loading..." : title}</Text>
        {icon && iconPosition === "right" ? icon : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    alignItems: "center",
    justifyContent: "center",
  },
  fullWidth: {
    width: "100%",
  },
  content: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  label: {
    textAlign: "center",
  },
});
