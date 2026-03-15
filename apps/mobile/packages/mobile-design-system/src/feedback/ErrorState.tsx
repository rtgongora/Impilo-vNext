/**
 * ErrorState — Displayed when a request or operation fails.
 */

import React from "react";

export interface ErrorStateProps {
  title?: string;
  message: string;
  correlationId?: string;
  onRetry?: () => void;
  testID?: string;
}

export function ErrorState({
  title = "Something went wrong",
  message,
  correlationId,
  onRetry,
  testID,
}: ErrorStateProps) {
  return React.createElement(
    "div",
    {
      "data-testid": testID,
      role: "alert",
      style: {
        display: "flex",
        flexDirection: "column" as const,
        alignItems: "center",
        justifyContent: "center",
        padding: 48,
        textAlign: "center" as const,
      },
    },
    React.createElement("h3", { style: { fontSize: 17, fontWeight: 600, color: "#C62828" } }, title),
    React.createElement("p", { style: { fontSize: 14, color: "#616161", marginTop: 8 } }, message),
    correlationId
      ? React.createElement("p", { style: { fontSize: 11, color: "#9E9E9E", marginTop: 4, fontFamily: "monospace" } }, `Ref: ${correlationId}`)
      : null,
    onRetry
      ? React.createElement("button", { onClick: onRetry, style: { marginTop: 16 } }, "Retry")
      : null
  );
}
