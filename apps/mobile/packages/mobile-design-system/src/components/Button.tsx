/**
 * Button — Primary interaction component.
 *
 * Variants: primary, secondary, outline, ghost, destructive
 * Sizes: sm, md, lg
 * Accessibility: role="button", accessibilityLabel required
 */

import React from "react";

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

  return React.createElement(
    "button",
    {
      onClick: isDisabled ? undefined : onPress,
      disabled: isDisabled,
      "aria-label": accessibilityLabel ?? title,
      "aria-disabled": isDisabled,
      "aria-busy": loading,
      "data-testid": testID,
      "data-variant": variant,
      "data-size": size,
      style: {
        width: fullWidth ? "100%" : undefined,
        opacity: isDisabled ? 0.5 : 1,
        cursor: isDisabled ? "not-allowed" : "pointer",
      },
    },
    icon && iconPosition === "left" ? icon : null,
    loading ? "Loading..." : title,
    icon && iconPosition === "right" ? icon : null
  );
}
