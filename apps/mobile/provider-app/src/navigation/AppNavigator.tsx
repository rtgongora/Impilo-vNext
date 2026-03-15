/**
 * AppNavigator — Root navigator that wraps AuthGuard + ModeRouter.
 *
 * Sets up the app shell: error boundary, network listener, notification setup.
 */

import React from "react";
import { View, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { AuthGuard } from "./AuthGuard";
import { ModeRouter } from "./ModeRouter";
import { GlobalErrorBanner } from "../screens/GlobalErrorBanner";
import { NetworkStatusBar } from "../screens/NetworkStatusBar";

export function AppNavigator() {
  return (
    <SafeAreaView style={styles.container}>
      <NetworkStatusBar />
      <GlobalErrorBanner />
      <AuthGuard>
        <ModeRouter />
      </AuthGuard>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FFFFFF",
  },
});
