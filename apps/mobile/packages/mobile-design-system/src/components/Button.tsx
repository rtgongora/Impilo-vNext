/**
 * Button — Primary interaction component.
 *
 * Variants: primary, secondary, outline, ghost, destructive
 * Sizes: sm, md, lg
 * Accessibility: accessibilityRole="button", accessibilityLabel required
 */

import React from "react";
import { Pressable, Text, View, StyleSheet, type StyleProp, type ViewStyle } from "react-native";

export type ButtonVariant =
  | "primary"
  | "secondary"
  | "outline"
  | "ghost"
  | "destructive"
  /**
   * Back-compat alias used by older screens.
   */
  | "default";
export type ButtonSize = "sm" | "md" | "lg" | "small";

export interface ButtonProps {
  title?: string;
  /**
   * Back-compat alias for `title` (older app screens).
   * Prefer `title`.
   */
  label?: string;
  onPress: () => void;
  variant?: ButtonVariant;
  size?: ButtonSize;
  disabled?: boolean;
  loading?: boolean;
  icon?: React.ReactNode;
  iconPosition?: "left" | "right";
  fullWidth?: boolean;
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
  testID?: string;
}

export function Button({
  title,
  label,
  onPress,
  variant = "primary",
  size = "md",
  disabled = false,
  loading = false,
  icon,
  iconPosition = "left",
  fullWidth = false,
  style,
  accessibilityLabel,
  testID,
}: ButtonProps) {
  const isDisabled = disabled || loading;
  const resolvedTitle = title ?? label ?? "";

  return (
    <Pressable
      onPress={isDisabled ? undefined : onPress}
      disabled={isDisabled}
      accessibilityLabel={accessibilityLabel ?? resolvedTitle}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      testID={testID}
      style={[
        styles.base,
        fullWidth ? styles.fullWidth : undefined,
        style,
        { opacity: isDisabled ? 0.5 : 1 },
      ]}
    >
      <View style={styles.content}>
        {icon && iconPosition === "left" ? icon : null}
        <Text style={styles.label}>{loading ? "Loading..." : resolvedTitle}</Text>
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
