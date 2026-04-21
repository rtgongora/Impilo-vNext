/**
 * LoginScreen — Keycloak PKCE authentication entry point.
 *
 * Renders the login button and handles the OAuth PKCE flow.
 * Uses expo-web-browser for the auth session and expo-linking for URL parsing.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, Text, StyleSheet, Image } from "react-native";
import * as WebBrowser from "expo-web-browser";
import * as Linking from "expo-linking";
import { useAuth } from "@impilo/mobile-auth";
import { Button, Card, CardBody, LoadingSpinner, ErrorState } from "@impilo/mobile-design-system";

WebBrowser.maybeCompleteAuthSession();

export function LoginScreen() {
  const auth = useAuth();
  const [pendingState, setPendingState] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = useCallback(async () => {
    setError(null);
    try {
      const { authUrl, state } = await auth.login();
      setPendingState(state);
      const result = await WebBrowser.openAuthSessionAsync(
        authUrl,
        Linking.createURL("auth/callback")
      );
      if (result.type === "success" && result.url) {
        const parsed = Linking.parse(result.url);
        const code = parsed.queryParams?.code as string | undefined;
        const returnedState = parsed.queryParams?.state as string | undefined;
        if (code && returnedState) {
          await auth.handleCallback(code, returnedState, state);
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    }
  }, [auth]);

  // Handle deep-link OAuth callback (code in URL params)
  useEffect(() => {
    const handleUrl = (event: { url: string }) => {
      const parsed = Linking.parse(event.url);
      const code = parsed.queryParams?.code as string | undefined;
      const state = parsed.queryParams?.state as string | undefined;

      if (code && state && pendingState) {
        auth.handleCallback(code, state, pendingState).catch((err) => {
          setError(err instanceof Error ? err.message : "Authentication failed");
        });
      }
    };

    const subscription = Linking.addEventListener("url", handleUrl);

    // Check if the app was opened via a deep link
    Linking.getInitialURL().then((url) => {
      if (url) {
        handleUrl({ url });
      }
    });

    return () => {
      subscription.remove();
    };
  }, [auth, pendingState]);

  return (
    <View testID="login-screen" style={styles.container}>
      <Image source={require("../../assets/icon.png")} style={styles.logo} resizeMode="contain" />
      <Text style={styles.title}>Impilo Provider</Text>
      <Text style={styles.subtitle}>Clinical workflow management</Text>
      <Card>
        <CardBody>
          {error ? (
            <ErrorState
              title="Authentication Error"
              message={error}
              onRetry={() => setError(null)}
            />
          ) : null}
          {auth.isLoading ? (
            <LoadingSpinner size="md" />
          ) : (
            <Button
              title="Sign in with Impilo"
              onPress={handleLogin}
              variant="primary"
              size="lg"
              fullWidth
              testID="login-button"
              accessibilityLabel="Sign in to Impilo Provider App"
            />
          )}
        </CardBody>
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
    backgroundColor: "#F3F4F6",
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 8,
    color: "#111827",
  },
  logo: {
    width: 96,
    height: 96,
    marginBottom: 18,
  },
  subtitle: {
    fontSize: 16,
    color: "#6B7280",
    marginBottom: 32,
  },
});
