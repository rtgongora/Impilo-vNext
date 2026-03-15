/**
 * LabResultCard — Displays a lab test result with reference range.
 */

import React from "react";

export type LabResultStatus = "NORMAL" | "ABNORMAL" | "CRITICAL" | "PENDING";

export interface LabResultCardProps {
  testName: string;
  value?: string;
  unit?: string;
  referenceRange?: string;
  status: LabResultStatus;
  collectedDate?: string;
  reportedDate?: string;
  performedBy?: string;
  onPress?: () => void;
  testID?: string;
}

const STATUS_CONFIG: Record<LabResultStatus, { color: string; bg: string }> = {
  NORMAL: { color: "#2E7D32", bg: "#E8F5E9" },
  ABNORMAL: { color: "#E65100", bg: "#FFF3E0" },
  CRITICAL: { color: "#C62828", bg: "#FFEBEE" },
  PENDING: { color: "#1565C0", bg: "#E3F2FD" },
};

export function LabResultCard({
  testName,
  value,
  unit,
  referenceRange,
  status,
  collectedDate,
  reportedDate,
  performedBy,
  onPress,
  testID,
}: LabResultCardProps) {
  const cfg = STATUS_CONFIG[status];
  const tag = onPress ? "button" : "div";

  return React.createElement(
    tag,
    {
      "data-testid": testID,
      onClick: onPress,
      role: onPress ? "button" : "group",
      "aria-label": `Lab: ${testName} — ${value ?? "Pending"} ${unit ?? ""}`,
      style: { padding: 12, backgroundColor: cfg.bg, borderRadius: 8, textAlign: "left" as const },
    },
    React.createElement("div", { style: { display: "flex", justifyContent: "space-between" } },
      React.createElement("span", { style: { fontWeight: 600, fontSize: 14 } }, testName),
      React.createElement("span", { style: { fontSize: 11, fontWeight: 600, color: cfg.color } }, status)
    ),
    value
      ? React.createElement("div", { style: { fontSize: 20, fontWeight: 700, color: cfg.color } },
          `${value} `,
          unit ? React.createElement("span", { style: { fontSize: 12, fontWeight: 400 } }, unit) : null
        )
      : React.createElement("div", { style: { fontSize: 14, color: "#757575" } }, "Result pending"),
    referenceRange ? React.createElement("div", { style: { fontSize: 12, color: "#757575" } }, `Ref: ${referenceRange}`) : null,
    collectedDate ? React.createElement("div", { style: { fontSize: 11, color: "#9E9E9E" } }, `Collected: ${collectedDate}`) : null,
    reportedDate ? React.createElement("div", { style: { fontSize: 11, color: "#9E9E9E" } }, `Reported: ${reportedDate}`) : null
  );
}
