/**
 * Citizen App — Root Component
 *
 * Initializes auth, API client, offline storage, and messaging.
 * Wraps the app in ThemeProvider and renders AppNavigator.
 */

import React, { useEffect, useState } from "react";
import { ThemeProvider } from "@impilo/mobile-design-system";
import { authStore } from "@impilo/mobile-auth";
import { onStepUpRequired } from "@impilo/mobile-api-client";
import { AppNavigator } from "./navigation/AppNavigator";
import { initializeApp } from "./config";
import { appStore } from "./stores/appStore";

export function App() {
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    initializeApp();

    authStore.getState().initialize().then(() => {
      setInitialized(true);
    });

    const unsubStepUp = onStepUpRequired((challenge) => {
      appStore.getState().setGlobalError({
        code: "STEP_UP_REQUIRED",
        message: `Additional authentication required: ${challenge.methods.join(", ")}`,
      });
    });

    const handleOnline = () => appStore.getState().setOnlineStatus(true);
    const handleOffline = () => appStore.getState().setOnlineStatus(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      unsubStepUp();
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  if (!initialized) {
    return React.createElement(
      "div",
      {
        style: {
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          height: "100vh",
          fontSize: "18px",
          color: "#6B7280",
        },
      },
      "Initializing Impilo Health..."
    );
  }

  return React.createElement(
    ThemeProvider,
    { mode: "light" },
    React.createElement(AppNavigator, null)
  );
}
