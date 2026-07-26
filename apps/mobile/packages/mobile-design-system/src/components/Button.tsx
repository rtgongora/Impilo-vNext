/**
 * Button — Primary interaction component.
 *
 * Variants: primary, secondary, outline, ghost, destructive
 * Sizes: sm, md, lg
 * Accessibility: accessibilityRole="button", accessibilityLabel required
 */

import React from "react";
import {
  Pressable,
  Text,
  View,
  StyleSheet,
  ActivityIndicator,
  type StyleProp,
  type ViewStyle,
} from "react-native";
import { useOptionalTheme } from "../theme/ThemeProvider";
import { colors } from "../tokens/colors";
import type { Theme } from "../theme/ThemeProvider";

export type ButtonVariant =
  | "primary"
  | "secondary"
  | "outline"
  | "ghost"
  | "destructive"
  | "default";
export type ButtonSize = "sm" | "md" | "lg" | "small";

export interface ButtonProps {
  title?: string;
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

/**
 * Variant colors, split deliberately into two kinds:
 *   - BRAND-TIED (primary, outline): resolve from theme.colors.primary, so
 *     Citizen and Provider render their own identity through the same
 *     component. This is what unblocks apps/mobile visual divergence — see
 *     docs/design/mobile-visual-redesign-brief.md finding #2.
 *   - UNIVERSAL/NEUTRAL (secondary, ghost, destructive, default): these carry
 *     fixed UI semantics ("a de-emphasised action", "a dangerous action") that
 *     must NOT vary by app, so they stay pinned to neutral/error tokens rather
 *     than to the brand accent. Only actual brand elements should diverge.
 */
function getVariantStyles(theme: Theme): Record<ButtonVariant, { bg: string; text: string; border?: string }> {
  return {
    primary:     { bg: theme.colors.primary, text: theme.colors.onPrimary },
    secondary:   { bg: colors.neutral[700], text: "#FFFFFF" },
    outline:     { bg: "transparent", text: theme.colors.primary, border: theme.colors.primary },
    ghost:       { bg: "transparent", text: colors.neutral[700] },
    destructive: { bg: theme.colors.error, text: "#FFFFFF" },
    default:     { bg: colors.neutral[500], text: "#FFFFFF" },
  };
}

const SIZE_STYLES: Record<ButtonSize, { py: number; px: number; fontSize: number; radius: number }> = {
  sm:    { py: 6,  px: 12, fontSize: 13, radius: 8  },
  small: { py: 6,  px: 12, fontSize: 13, radius: 8  },
  md:    { py: 10, px: 20, fontSize: 15, radius: 10 },
  lg:    { py: 14, px: 28, fontSize: 17, radius: 12 },
};

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
  const { theme } = useOptionalTheme();
  const isDisabled = disabled || loading;
  const resolvedTitle = title ?? label ?? "";
  const vs = getVariantStyles(theme)[variant];
  const ss = SIZE_STYLES[size];

  return (
    <Pressable
      onPress={isDisabled ? undefined : onPress}
      disabled={isDisabled}
      accessibilityLabel={accessibilityLabel ?? resolvedTitle}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      testID={testID}
      style={({ pressed }) => [
        styles.base,
        {
          backgroundColor: vs.bg,
          paddingVertical: ss.py,
          paddingHorizontal: ss.px,
          borderRadius: ss.radius,
          borderWidth: vs.border ? 1.5 : 0,
          borderColor: vs.border ?? "transparent",
          opacity: isDisabled ? 0.45 : pressed ? 0.82 : 1,
        },
        fullWidth ? styles.fullWidth : undefined,
        style,
      ]}
    >
      <View style={styles.content}>
        {loading ? (
          <ActivityIndicator size="small" color={vs.text} />
        ) : (
          <>
            {icon && iconPosition === "left" ? icon : null}
            <Text
              style={[
                styles.label,
                { color: vs.text, fontSize: ss.fontSize, fontWeight: "600" },
              ]}
            >
              {resolvedTitle}
            </Text>
            {icon && iconPosition === "right" ? icon : null}
          </>
        )}
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
    letterSpacing: 0.2,
  },
});
