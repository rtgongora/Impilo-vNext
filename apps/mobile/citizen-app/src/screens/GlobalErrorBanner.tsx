/**
 * GlobalErrorBanner — Displays global app-level errors at the top of the screen.
 */

import React from "react";
import { Button } from "@impilo/mobile-design-system";
import { useAppStore } from "../stores/appStore";

export function GlobalErrorBanner() {
  const { globalError, setGlobalError } = useAppStore();

  if (!globalError) return null;

  return React.createElement(
    "div",
    {
      "data-testid": "global-error-banner",
      role: "alert",
      style: {
        padding: "12px 16px",
        backgroundColor: "#FEE2E2",
        borderBottom: "1px solid #FECACA",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
      },
    },
    React.createElement(
      "span",
      { style: { color: "#991B1B", fontSize: "14px" } },
      `${globalError.code}: ${globalError.message}`
    ),
    React.createElement(Button, {
      title: "Dismiss",
      variant: "ghost",
      size: "sm",
      onPress: () => setGlobalError(null),
      testID: "dismiss-error",
    })
  );
}
