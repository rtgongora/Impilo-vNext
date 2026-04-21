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
import { useAppStore } from "../stores/appStore";

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const auth = useAuth();
  const { facilityId } = useAppStore();

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

  if (!facilityId) {
    return <SelectFacilityScreen />;
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
