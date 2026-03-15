/**
 * AuthGuard — Redirects unauthenticated users to the login screen.
 *
 * Renders children only when authenticated and facility context is set.
 * Shows loading spinner during initialization.
 */

import React from "react";
import { useAuth } from "@impilo/mobile-auth";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { LoginScreen } from "../screens/LoginScreen";
import { SelectFacilityScreen } from "../screens/SelectFacilityScreen";
import { useAppStore } from "../stores/appStore";

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const auth = useAuth();
  const { facilityId } = useAppStore();

  if (auth.isLoading) {
    return React.createElement(
      "div",
      {
        "data-testid": "auth-loading",
        style: { display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" },
      },
      React.createElement(LoadingSpinner, { size: "lg" })
    );
  }

  if (!auth.isAuthenticated) {
    return React.createElement(LoginScreen, null);
  }

  if (!facilityId) {
    return React.createElement(SelectFacilityScreen, null);
  }

  return React.createElement(React.Fragment, null, children);
}
