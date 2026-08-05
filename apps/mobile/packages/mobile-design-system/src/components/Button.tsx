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
import { glossButtonStops, glossSheen, mix } from "../tokens/gloss";
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
/**
 * Which variants take the gloss treatment, and with what stops.
 *
 * Only FILLED variants get it — an outline/ghost button has no fill to light. The
 * gradient is derived from the variant's own resolved background, so the citizen accent
 * stays green, the provider accent stays teal, and `destructive` stays red: structure is
 * shared, hue is not. Pure and RN-free so it is unit-testable in isolation.
 */
export function resolveButtonGloss(
  variant: ButtonVariant,
  bg: string,
): { enabled: boolean; from: string; to: string } {
  const filled = variant === "primary" || variant === "secondary" || variant === "destructive" || variant === "default";
  if (!filled || bg === "transparent") return { enabled: false, from: bg, to: bg };
  const { from, to } = glossButtonStops(bg);
  return { enabled: from !== to, from, to };
}

function getVariantStyles(theme: Theme): Record<ButtonVariant, { bg: string; text: string; border?: string }> {
  return {
    primary:     { bg: theme.colors.primary, text: theme.colors.onPrimary },
    // Deliberately gray, not brand-blue like the original hardcoded #1E40AF —
    // matches how "secondary" is actually used (Cancel/Close/Sign Out — a
    // de-emphasised action), but using colors.gray (Tailwind, matches the rest
    // of the app) rather than colors.neutral (Material, matches nothing).
    secondary:   { bg: colors.gray[700], text: "#FFFFFF" },
    outline:     { bg: "transparent", text: theme.colors.primary, border: theme.colors.primary },
    // Exact original value (#374151) — see colors.ts `gray` for why this is
    // NOT colors.neutral[700] (#616161, a visibly different gray).
    ghost:       { bg: "transparent", text: colors.gray[700] },
    // Exact original value (#DC2626) — NOT theme.colors.error (#F44336, the
    // orphaned/unused semantic red). See colors.ts `ui.error`.
    destructive: { bg: colors.ui.error.main, text: "#FFFFFF" },
    // Exact original value (#6B7280) — NOT colors.neutral[500] (#9E9E9E).
    default:     { bg: colors.gray[500], text: "#FFFFFF" },
  };
}

const BUTTON_GLOSS_BANDS = 10;

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
  const gloss = resolveButtonGloss(variant, vs.bg);

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
      {gloss.enabled ? (
        <>
          {/* Flat bands stand in for a gradient — see GlossCanvas for why no gradient
              library is usable here. A button is short, so a handful of bands is smooth. */}
          <View
            style={StyleSheet.absoluteFill}
            pointerEvents="none"
            testID={testID ? `${testID}-gloss` : undefined}
          >
            {Array.from({ length: BUTTON_GLOSS_BANDS }, (_, i) => (
              <View
                key={i}
                style={{
                  flex: 1,
                  backgroundColor: mix(gloss.from, gloss.to, (i + 0.5) / BUTTON_GLOSS_BANDS),
                }}
              />
            ))}
          </View>
          {/* The inner top highlight — the web's `inset 0 1px 0 rgba(255,255,255,.35)`. */}
          <View style={styles.sheen} pointerEvents="none" />
        </>
      ) : null}
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
    // The gradient layer is absolutely positioned; without this it paints past the radius.
    overflow: "hidden",
  },
  sheen: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: glossSheen,
  },
  fullWidth: {
    width: "100%",
  },
  content: {
    // Above the gradient layer.
    position: "relative",
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  label: {
    textAlign: "center",
    letterSpacing: 0.2,
  },
});
