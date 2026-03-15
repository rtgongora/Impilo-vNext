/**
 * Screen — Top-level screen wrapper with safe area and scroll support.
 */

import React from "react";

export interface ScreenProps {
  children: React.ReactNode;
  scrollable?: boolean;
  padding?: boolean;
  backgroundColor?: string;
  testID?: string;
}

export function Screen({
  children,
  scrollable = true,
  padding = true,
  backgroundColor,
  testID,
}: ScreenProps) {
  return React.createElement(
    "div",
    {
      "data-testid": testID,
      style: {
        flex: 1,
        backgroundColor: backgroundColor ?? "#FFFFFF",
        padding: padding ? 16 : 0,
        overflow: scrollable ? "auto" : "hidden",
        minHeight: "100vh",
      },
    },
    children
  );
}
