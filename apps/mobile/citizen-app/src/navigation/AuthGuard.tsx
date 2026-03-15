/**
 * AuthGuard — Redirects unauthenticated citizens to the login screen.
 *
 * For the citizen app, we do not require facility selection (unlike provider app).
 * After authentication, profile bootstrap is triggered.
 */

import React, { useEffect } from "react";
import { View, ActivityIndicator, Text, StyleSheet } from "react-native";
import { useAuth } from "@impilo/mobile-auth";
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
    return (
      <View testID="auth-loading" style={styles.loading}>
        <ActivityIndicator size="large" color="#059669" />
      </View>
    );
  }

  if (!auth.isAuthenticated) {
    return <LoginScreen />;
  }

  if (!profile) {
    return (
      <View testID="profile-loading" style={styles.loading}>
        <ActivityIndicator size="large" color="#059669" />
        <Text style={styles.loadingText}>Loading your profile...</Text>
      </View>
    );
  }

  return <>{children}</>;
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: "#6B7280",
  },
});
