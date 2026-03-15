/**
 * VitalCard — Displays a single vital sign measurement.
 */

import React from "react";

export interface VitalCardProps {
  label: string;
  value: string | number;
  unit: string;
  timestamp?: string;
  status?: "normal" | "warning" | "critical";
  icon?: React.ReactNode;
  testID?: string;
}

const STATUS_COLORS = {
  normal: "#4CAF50",
  warning: "#FF9800",
  critical: "#F44336",
};

export function VitalCard({
  label,
  value,
  unit,
  timestamp,
  status = "normal",
  icon,
  testID,
}: VitalCardProps) {
  return React.createElement(
    "div",
    {
      "data-testid": testID,
      role: "group",
      "aria-label": `${label}: ${value} ${unit}`,
      style: { borderLeft: `3px solid ${STATUS_COLORS[status]}`, padding: 12 },
    },
    React.createElement(
      "div",
      { style: { display: "flex", alignItems: "center", gap: 8 } },
      icon ?? null,
      React.createElement("span", { style: { fontWeight: 600 } }, label)
    ),
    React.createElement(
      "div",
      { style: { fontSize: 24, fontWeight: 700 } },
      `${value} `,
      React.createElement("span", { style: { fontSize: 14, fontWeight: 400 } }, unit)
    ),
    timestamp
      ? React.createElement("div", { style: { fontSize: 12, color: "#757575" } }, timestamp)
      : null
  );
}
