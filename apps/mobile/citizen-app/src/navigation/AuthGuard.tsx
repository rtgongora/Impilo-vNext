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
import { ContactSignUpScreen } from "../screens/gateway/ContactSignUpScreen";
import { HealthInfoScreen } from "../screens/gateway/HealthInfoScreen";
import { GatewayVerifyScreen } from "../screens/gateway/GatewayVerifyScreen";
import { TrackByReferenceScreen } from "../screens/gateway/TrackByReferenceScreen";
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
    const clear = () => appStore.getState().setOnboardingScreen(null);
    if (onboardingScreen === "signup") {
      return <SignUpScreen onBack={clear} />;
    }
    if (onboardingScreen === "contact-signup") {
      return <ContactSignUpScreen onBack={clear} />;
    }
    if (onboardingScreen === "health-info") {
      return <HealthInfoScreen onBack={clear} />;
    }
    if (onboardingScreen === "verify") {
      return <GatewayVerifyScreen onBack={clear} />;
    }
    if (onboardingScreen === "track-emergency") {
      return <TrackByReferenceScreen onBack={clear} />;
    }
    return (
      <LoginScreen
        onSignUp={() => appStore.getState().setOnboardingScreen("signup")}
        onContactSignUp={() => appStore.getState().setOnboardingScreen("contact-signup")}
        onGuestDestination={(dest) => appStore.getState().setOnboardingScreen(dest)}
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
