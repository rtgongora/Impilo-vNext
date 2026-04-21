/**
 * LoginScreen — Keycloak PKCE authentication for citizens.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, Image } from "react-native";
import * as WebBrowser from "expo-web-browser";
import * as Linking from "expo-linking";
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

  return (
    <View testID="login-screen" style={styles.container}>
      <Image source={require("../../assets/icon.png")} style={styles.logo} resizeMode="contain" />
      <Text style={styles.title}>Impilo Health</Text>
      <Text style={styles.subtitle}>Your health, in your hands</Text>
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
              title="Sign in to Impilo"
              onPress={handleLogin}
              variant="primary"
              size="lg"
              fullWidth
              testID="login-button"
              accessibilityLabel="Sign in to Impilo Citizen App"
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
