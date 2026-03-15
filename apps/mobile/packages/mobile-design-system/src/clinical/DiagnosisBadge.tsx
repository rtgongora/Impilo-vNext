/**
 * DiagnosisBadge — Displays an ICD-11 diagnosis code with description.
 */

import React from "react";

export interface DiagnosisBadgeProps {
  code: string;
  description: string;
  isPrimary?: boolean;
  testID?: string;
}

export function DiagnosisBadge({ code, description, isPrimary = false, testID }: DiagnosisBadgeProps) {
  return React.createElement(
    "div",
    {
      "data-testid": testID,
      "aria-label": `${isPrimary ? "Primary " : ""}Diagnosis: ${code} - ${description}`,
      style: {
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "4px 10px",
        borderRadius: 6,
        backgroundColor: isPrimary ? "#FFF3E0" : "#F5F5F5",
        border: isPrimary ? "1px solid #FF9800" : "1px solid #E0E0E0",
      },
    },
    React.createElement("span", { style: { fontWeight: 600, fontFamily: "monospace", fontSize: 12 } }, code),
    React.createElement("span", { style: { fontSize: 13 } }, description),
    isPrimary ? React.createElement("span", { style: { fontSize: 10, fontWeight: 600, color: "#E65100" } }, "PRIMARY") : null
  );
}
