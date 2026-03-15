/**
 * NetworkStatusBar — Shows offline status warning.
 */

import React from "react";
import { useAppStore } from "../stores/appStore";

export function NetworkStatusBar() {
  const { isOnline } = useAppStore();

  if (isOnline) return null;

  return React.createElement(
    "div",
    {
      "data-testid": "network-status-bar",
      role: "status",
      style: {
        padding: "8px 16px",
        backgroundColor: "#FEF3C7",
        borderBottom: "1px solid #FDE68A",
        textAlign: "center",
        fontSize: "13px",
        color: "#92400E",
      },
    },
    "You are currently offline. Some features may be unavailable."
  );
}
