/**
 * StatusIndicator — Colored dot + label for sync/connection/entity status.
 */

import React from "react";

export type IndicatorStatus = "online" | "offline" | "syncing" | "error" | "warning" | "idle";

export interface StatusIndicatorProps {
  status: IndicatorStatus;
  label?: string;
  size?: "sm" | "md" | "lg";
  testID?: string;
}

const STATUS_COLORS: Record<IndicatorStatus, string> = {
  online: "#4CAF50",
  offline: "#9E9E9E",
  syncing: "#2196F3",
  error: "#F44336",
  warning: "#FF9800",
  idle: "#BDBDBD",
};

export function StatusIndicator({
  status,
  label,
  size = "md",
  testID,
}: StatusIndicatorProps) {
  const dotSize = size === "sm" ? 8 : size === "md" ? 10 : 14;

  return React.createElement(
    "div",
    {
      "data-testid": testID,
      role: "status",
      "aria-label": label ?? status,
      style: { display: "flex", alignItems: "center", gap: 6 },
    },
    React.createElement("span", {
      style: {
        width: dotSize,
        height: dotSize,
        borderRadius: "50%",
        backgroundColor: STATUS_COLORS[status],
        display: "inline-block",
      },
    }),
    label ? React.createElement("span", null, label) : null
  );
}
