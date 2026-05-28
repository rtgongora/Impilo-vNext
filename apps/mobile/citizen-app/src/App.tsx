/**
 * Citizen App — Root Component
 *
 * Initializes auth, API client, offline storage, and messaging.
 * Wraps the app in ThemeProvider and renders AppNavigator.
 */

import React, { useEffect, useState } from "react";
import { View, Text, StyleSheet, ActivityIndicator } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import NetInfo from "@react-native-community/netinfo";
import { ThemeProvider } from "@impilo/mobile-design-system";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { authStore } from "@impilo/mobile-auth";
import { onStepUpRequired } from "@impilo/mobile-api-client";
import { AppNavigator } from "./navigation/AppNavigator";
import { initializeApp, initializeOfflinePersistence } from "./config";
import { syncEngine } from "@impilo/mobile-offline";
import { appStore } from "./stores/appStore";
import { ToastProvider } from "./components/Toast";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 2, staleTime: 30_000 } },
});

const DEV_BYPASS_AUTH = process.env.EXPO_PUBLIC_DEV_BYPASS_AUTH === "true";

const DEV_TOKEN = "dev-hardcoded-token";

const DEV_SESSION = {
  tenantId: "tenant-moh-zw",
  podId: "national-spine",
  actorId: "dev-citizen-001",
  actorType: "CITIZEN" as const,
  accessToken: DEV_TOKEN,
  refreshToken: DEV_TOKEN,
  expiresAt: Date.now() + 24 * 60 * 60 * 1000,
  purposeOfUse: "TREATMENT" as const,
};

const DEV_USER = {
  sub: "dev-citizen-001",
  preferred_username: "dev.citizen",
  email: "dev@impilo.gov.zw",
  given_name: "Dev",
  family_name: "Citizen",
  realm_access: { roles: ["CITIZEN"] },
};

const DEV_PROFILE = {
  cpid: "dev-citizen-001",
  givenName: "Dev",
  familyName: "Citizen",
  dateOfBirth: "1990-01-01",
  sex: "MALE",
  phone: "+263770000000",
  email: "dev@impilo.gov.zw",
  preferredLanguage: "en",
};

export function App() {
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      await initializeOfflinePersistence();
      if (cancelled) return;
      initializeApp();

      if (DEV_BYPASS_AUTH) {
        authStore.setState({
          isAuthenticated: true,
          isLoading: false,
          user: DEV_USER,
          session: DEV_SESSION,
          error: null,
        });
        appStore.getState().setProfile(DEV_PROFILE);
      } else {
        await authStore.getState().initialize();
      }

      if (!cancelled) setInitialized(true);
    })();

    const unsubStepUp = onStepUpRequired((challenge) => {
      appStore.getState().setGlobalError({
        code: "STEP_UP_REQUIRED",
        message: `Additional authentication required: ${challenge.methods.join(", ")}`,
      });
    });

    const unsubscribeNetInfo = NetInfo.addEventListener((state) => {
      const online = state.isConnected ?? false;
      appStore.getState().setOnlineStatus(online);
      if (online) {
        void syncEngine.sync();
      }
    });

    return () => {
      cancelled = true;
      unsubStepUp();
      unsubscribeNetInfo();
    };
  }, []);

  if (!initialized) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#00897B" />
        <Text style={styles.loadingText}>Initializing Impilo Health...</Text>
      </View>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <StatusBar style="dark" />
        <ThemeProvider mode="light">
          <ToastProvider>
            <AppNavigator />
          </ToastProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#FFFFFF",
  },
  loadingText: {
    marginTop: 16,
    fontSize: 18,
    color: "#6B7280",
  },
});
