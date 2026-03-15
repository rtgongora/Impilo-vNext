/**
 * AuthGuard — Redirects unauthenticated citizens to the login screen.
 *
 * For the citizen app, we do not require facility selection (unlike provider app).
 * After authentication, profile bootstrap is triggered.
 */

import React, { useEffect } from "react";
import { useAuth } from "@impilo/mobile-auth";
import { LoadingSpinner } from "@impilo/mobile-design-system";
import { LoginScreen } from "../screens/LoginScreen";
import { appStore, useAppStore } from "../stores/appStore";
import { fetchProfile } from "../services/profileService";

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const auth = useAuth();
  const { profile } = useAppStore();

  useEffect(() => {
    if (auth.isAuthenticated && !profile) {
      fetchProfile()
        .then((p) => appStore.getState().setProfile(p))
        .catch(() => {
          appStore.getState().setGlobalError({
            code: "PROFILE_LOAD_FAILED",
            message: "Unable to load your profile. Please try again.",
          });
        });
    }
  }, [auth.isAuthenticated, profile]);

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

  if (!profile) {
    return React.createElement(
      "div",
      {
        "data-testid": "profile-loading",
        style: { display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" },
      },
      React.createElement(LoadingSpinner, { size: "lg", message: "Loading your profile..." })
    );
  }

  return React.createElement(React.Fragment, null, children);
}
