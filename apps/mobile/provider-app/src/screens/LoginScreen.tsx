/**
 * LoginScreen — Keycloak PKCE authentication entry point.
 *
 * Renders the login button and handles the OAuth PKCE flow.
 */

import React, { useState, useCallback } from "react";
import { useAuth } from "@impilo/mobile-auth";
import { Button, Card, CardBody, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";

export function LoginScreen() {
  const auth = useAuth();
  const [pendingState, setPendingState] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = useCallback(async () => {
    setError(null);
    try {
      const { authUrl, state } = await auth.login();
      setPendingState(state);
      // In a real mobile app this opens the system browser via Linking.openURL
      // For web-based testing, redirect directly
      window.location.href = authUrl;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    }
  }, [auth]);

  // Handle OAuth callback (code in URL params)
  React.useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const state = params.get("state");

    if (code && state && pendingState) {
      auth.handleCallback(code, state, pendingState).catch((err) => {
        setError(err instanceof Error ? err.message : "Authentication failed");
      });
    }
  }, [auth, pendingState]);

  return React.createElement(
    "div",
    {
      "data-testid": "login-screen",
      style: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100vh",
        padding: "24px",
        backgroundColor: "#F3F4F6",
      },
    },
    React.createElement(
      "h1",
      { style: { fontSize: "28px", fontWeight: "700", marginBottom: "8px", color: "#111827" } },
      "Impilo Provider"
    ),
    React.createElement(
      "p",
      { style: { fontSize: "16px", color: "#6B7280", marginBottom: "32px" } },
      "Clinical workflow management"
    ),
    React.createElement(
      Card,
      null,
      React.createElement(
        CardBody,
        null,
        error
          ? React.createElement(ErrorState, {
              title: "Authentication Error",
              message: error,
              onRetry: () => setError(null),
            })
          : null,
        auth.isLoading
          ? React.createElement(LoadingSpinner, { size: "md" })
          : React.createElement(Button, {
              title: "Sign in with Impilo",
              onPress: handleLogin,
              variant: "primary",
              size: "lg",
              fullWidth: true,
              testID: "login-button",
              accessibilityLabel: "Sign in to Impilo Provider App",
            })
      )
    )
  );
}
