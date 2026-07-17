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
import { authStore } from "@impilo/mobile-auth";
import { onStepUpRequired } from "@impilo/mobile-api-client";
import { AppNavigator } from "./navigation/AppNavigator";
import { initializeApp, initializeOfflinePersistence } from "./config";
import { syncEngine } from "@impilo/mobile-offline";
import { appStore } from "./stores/appStore";

export function App() {
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      await initializeOfflinePersistence();
      if (cancelled) return;
      initializeApp();
      await authStore.getState().initialize();
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
        <ActivityIndicator size="large" color="#009739" />
        <Text style={styles.loadingText}>Initializing Impilo...</Text>
      </View>
    );
  }

  return (
    <SafeAreaProvider>
      <StatusBar style="dark" />
      <ThemeProvider mode="light">
        <AppNavigator />
      </ThemeProvider>
    </SafeAreaProvider>
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
