/**
 * AppNavigator — Root navigator wrapping AuthGuard + CitizenTabs.
 */

import React from "react";
import { AuthGuard } from "./AuthGuard";
import { CitizenTabs } from "./CitizenTabs";
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
      React.createElement(CitizenTabs, null)
    )
  );
}
