/**
 * Card — Container component with optional header, body, and footer.
 */

import React from "react";

export interface CardProps {
  children: React.ReactNode;
  variant?: "elevated" | "outlined" | "filled";
  padding?: "none" | "sm" | "md" | "lg";
  onPress?: () => void;
  testID?: string;
  accessibilityLabel?: string;
}

export function Card({
  children,
  variant = "elevated",
  padding = "md",
  onPress,
  testID,
  accessibilityLabel,
}: CardProps) {
  const tag = onPress ? "button" : "div";
  return React.createElement(
    tag,
    {
      onClick: onPress,
      "data-testid": testID,
      "data-variant": variant,
      "data-padding": padding,
      "aria-label": accessibilityLabel,
      role: onPress ? "button" : undefined,
    },
    children
  );
}

export interface CardHeaderProps {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}

export function CardHeader({ title, subtitle, action }: CardHeaderProps) {
  return React.createElement(
    "div",
    { "data-slot": "card-header" },
    React.createElement("div", null,
      React.createElement("h3", null, title),
      subtitle ? React.createElement("p", null, subtitle) : null
    ),
    action ?? null
  );
}

export interface CardBodyProps {
  children: React.ReactNode;
}

export function CardBody({ children }: CardBodyProps) {
  return React.createElement("div", { "data-slot": "card-body" }, children);
}

export interface CardFooterProps {
  children: React.ReactNode;
}

export function CardFooter({ children }: CardFooterProps) {
  return React.createElement("div", { "data-slot": "card-footer" }, children);
}
