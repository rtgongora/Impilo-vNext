/**
 * AppNavigator — Root navigator that wraps AuthGuard + ModeRouter.
 *
 * Sets up the app shell: error boundary, network listener, notification setup.
 */

import React from "react";
import { AuthGuard } from "./AuthGuard";
import { ModeRouter } from "./ModeRouter";
import { GlobalErrorBanner } from "../screens/GlobalErrorBanner";
import { NetworkStatusBar } from "../screens/NetworkStatusBar";

export function AppNavigator() {
  return React.createElement(
    "div",
    { "data-testid": "app-navigator", style: { display: "flex", flexDirection: "column", height: "100vh" } },
    React.createElement(NetworkStatusBar, null),
    React.createElement(GlobalErrorBanner, null),
    React.createElement(
      AuthGuard,
      null,
      React.createElement(ModeRouter, null)
    )
  );
}
