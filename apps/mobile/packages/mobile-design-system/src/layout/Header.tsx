/**
 * Header — Screen header with title, back button, and optional actions.
 */

import React from "react";

export interface HeaderProps {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  actions?: React.ReactNode;
  testID?: string;
}

export function Header({ title, subtitle, onBack, actions, testID }: HeaderProps) {
  return React.createElement(
    "header",
    {
      "data-testid": testID,
      style: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "12px 16px",
        borderBottom: "1px solid #E0E0E0",
      },
    },
    React.createElement(
      "div",
      { style: { display: "flex", alignItems: "center", gap: 12 } },
      onBack
        ? React.createElement("button", { onClick: onBack, "aria-label": "Go back" }, "\u2190")
        : null,
      React.createElement(
        "div",
        null,
        React.createElement("h1", { style: { fontSize: 17, fontWeight: 600, margin: 0 } }, title),
        subtitle
          ? React.createElement("p", { style: { fontSize: 13, color: "#757575", margin: 0 } }, subtitle)
          : null
      )
    ),
    actions ?? null
  );
}
