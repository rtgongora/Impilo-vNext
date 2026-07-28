/**
 * AuthGuard — Redirects unauthenticated users to the login screen.
 *
 * Renders children only when authenticated and facility context is set.
 * Shows loading spinner during initialization.
 */

import React from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { useAuth } from "@impilo/mobile-auth";
import { LoginScreen } from "../screens/LoginScreen";
import { SelectFacilityScreen } from "../screens/SelectFacilityScreen";
import { SelectWorkspaceScreen } from "../screens/SelectWorkspaceScreen";
import { ProviderActivationScreen } from "../screens/ProviderActivationScreen";
import { useAppStore } from "../stores/appStore";
import { useAutoResolveWorkContext } from "../hooks/useAutoResolveWorkContext";

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const auth = useAuth();
  const { facilityId, workspaceId } = useAppStore();
  // Phase G1 — best-effort, non-blocking: mints a real work-context token once
  // facility+workspace are set. Never gates rendering on its result (see the
  // hook's doc comment for why some accounts legitimately resolve nothing yet).
  useAutoResolveWorkContext();

  if (auth.isLoading) {
    return (
      <View testID="auth-loading" style={styles.loading}>
        <ActivityIndicator size="large" color="#009739" />
      </View>
    );
  }

  if (!auth.isAuthenticated) {
    return <LoginScreen />;
  }

  // Health OS §6: "Sign in as a person; practice as a provider only under activated Provider ID."
  if (!auth.hasActiveProvider()) {
    return <ProviderActivationScreen />;
  }

  if (!facilityId) {
    return <SelectFacilityScreen />;
  }

  if (!workspaceId) {
    return <SelectWorkspaceScreen />;
  }

  return <>{children}</>;
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
});
