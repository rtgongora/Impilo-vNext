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
import { SignUpScreen } from "../screens/auth/SignUpScreen";
import { AssuranceChoiceScreen } from "../screens/auth/AssuranceChoiceScreen";
import { appStore, useAppStore } from "../stores/appStore";
import { fetchProfile } from "../services/profileService";

interface AuthGuardProps {
  children: React.ReactNode;
}

export function AuthGuard({ children }: AuthGuardProps) {
  const auth = useAuth();
  const { profile, onboardingScreen } = useAppStore();

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
        <ActivityIndicator size="large" color="#009739" />
      </View>
    );
  }

  if (!auth.isAuthenticated) {
    if (onboardingScreen === "signup") {
      return (
        <SignUpScreen
          onBack={() => appStore.getState().setOnboardingScreen(null)}
        />
      );
    }
    return (
      <LoginScreen
        onSignUp={() => appStore.getState().setOnboardingScreen("signup")}
      />
    );
  }

  if (onboardingScreen === "assurance") {
    return <AssuranceChoiceScreen />;
  }

  if (!profile) {
    return (
      <View testID="profile-loading" style={styles.loading}>
        <ActivityIndicator size="large" color="#009739" />
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
