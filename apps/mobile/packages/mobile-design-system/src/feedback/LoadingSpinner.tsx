/**
 * LoadingSpinner — Centered loading indicator with optional message.
 */

import React from "react";

export interface LoadingSpinnerProps {
  size?: "sm" | "md" | "lg";
  message?: string;
  fullScreen?: boolean;
  testID?: string;
}

export function LoadingSpinner({
  size = "md",
  message,
  fullScreen = false,
  testID,
}: LoadingSpinnerProps) {
  return React.createElement(
    "div",
    {
      "data-testid": testID,
      role: "progressbar",
      "aria-label": message ?? "Loading",
      "aria-busy": true,
      style: {
        display: "flex",
        flexDirection: "column" as const,
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        ...(fullScreen ? { position: "fixed" as const, inset: 0, zIndex: 50 } : {}),
      },
    },
    React.createElement("div", {
      "data-size": size,
      style: { animation: "spin 1s linear infinite" },
    }, "\u23F3"),
    message ? React.createElement("p", { style: { marginTop: 12, color: "#757575" } }, message) : null
  );
}
