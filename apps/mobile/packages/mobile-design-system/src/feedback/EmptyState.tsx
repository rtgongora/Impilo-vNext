/**
 * EmptyState — Displayed when a list or feed has no content.
 */

import React from "react";

export interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  action?: {
    label: string;
    onPress: () => void;
  };
  testID?: string;
}

export function EmptyState({ title, description, icon, action, testID }: EmptyStateProps) {
  return React.createElement(
    "div",
    {
      "data-testid": testID,
      role: "status",
      style: {
        display: "flex",
        flexDirection: "column" as const,
        alignItems: "center",
        justifyContent: "center",
        padding: 48,
        textAlign: "center" as const,
      },
    },
    icon ?? null,
    React.createElement("h3", { style: { fontSize: 17, fontWeight: 600, marginTop: 16 } }, title),
    description ? React.createElement("p", { style: { fontSize: 14, color: "#757575", marginTop: 8 } }, description) : null,
    action
      ? React.createElement(
          "button",
          { onClick: action.onPress, style: { marginTop: 16 } },
          action.label
        )
      : null
  );
}
