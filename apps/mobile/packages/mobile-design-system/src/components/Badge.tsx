/**
 * Badge — Status indicator label.
 */

import React from "react";

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
  return React.createElement(
    "span",
    {
      "data-testid": testID,
      "data-variant": variant,
      "data-size": size,
      role: "status",
      "aria-label": label,
    },
    icon ?? null,
    label
  );
}
