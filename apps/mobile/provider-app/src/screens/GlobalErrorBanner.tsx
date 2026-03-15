/**
 * GlobalErrorBanner — Displays global errors from appStore.
 *
 * Dismissible banner at the top of the app.
 */

import React from "react";
import { Button } from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../stores/appStore";

export function GlobalErrorBanner() {
  const { globalError } = useAppStore();

  if (!globalError) return null;

  return React.createElement(
    "div",
    {
      "data-testid": "global-error-banner",
      role: "alert",
      style: {
        backgroundColor: "#FEE2E2",
        borderBottom: "1px solid #FECACA",
        padding: "12px 16px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
      },
    },
    React.createElement(
      "div",
      null,
      React.createElement(
        "strong",
        { style: { color: "#991B1B" } },
        globalError.code
      ),
      React.createElement(
        "span",
        { style: { color: "#991B1B", marginLeft: "8px" } },
        globalError.message
      )
    ),
    React.createElement(Button, {
      title: "Dismiss",
      variant: "ghost",
      size: "sm",
      onPress: () => appStore.getState().setGlobalError(null),
      testID: "dismiss-error",
    })
  );
}
