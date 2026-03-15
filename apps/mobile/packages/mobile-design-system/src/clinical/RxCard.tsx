/**
 * RxCard — Displays a prescription summary.
 */

import React from "react";

export type RxStatus = "ACTIVE" | "COMPLETED" | "CANCELLED" | "DRAFT" | "ON_HOLD";

export interface RxCardProps {
  medicationName: string;
  dosage: string;
  frequency: string;
  duration?: string;
  prescribedBy?: string;
  prescribedDate?: string;
  status: RxStatus;
  refillsRemaining?: number;
  onPress?: () => void;
  testID?: string;
}

const STATUS_COLORS: Record<RxStatus, string> = {
  ACTIVE: "#4CAF50",
  COMPLETED: "#9E9E9E",
  CANCELLED: "#F44336",
  DRAFT: "#FF9800",
  ON_HOLD: "#2196F3",
};

export function RxCard({
  medicationName,
  dosage,
  frequency,
  duration,
  prescribedBy,
  prescribedDate,
  status,
  refillsRemaining,
  onPress,
  testID,
}: RxCardProps) {
  const tag = onPress ? "button" : "div";
  return React.createElement(
    tag,
    {
      "data-testid": testID,
      onClick: onPress,
      role: onPress ? "button" : "group",
      "aria-label": `Prescription: ${medicationName} ${dosage} ${frequency}`,
      style: { borderLeft: `3px solid ${STATUS_COLORS[status]}`, padding: 12, textAlign: "left" as const },
    },
    React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
      React.createElement("span", { style: { fontWeight: 600, fontSize: 15 } }, medicationName),
      React.createElement("span", { style: { fontSize: 11, fontWeight: 600, color: STATUS_COLORS[status] } }, status)
    ),
    React.createElement("div", { style: { fontSize: 13, color: "#616161" } }, `${dosage} — ${frequency}`),
    duration ? React.createElement("div", { style: { fontSize: 12, color: "#757575" } }, `Duration: ${duration}`) : null,
    prescribedBy ? React.createElement("div", { style: { fontSize: 12, color: "#757575" } }, `By: ${prescribedBy}${prescribedDate ? ` on ${prescribedDate}` : ""}`) : null,
    refillsRemaining !== undefined ? React.createElement("div", { style: { fontSize: 12, color: refillsRemaining === 0 ? "#F44336" : "#4CAF50" } }, `Refills remaining: ${refillsRemaining}`) : null
  );
}
