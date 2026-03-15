/**
 * AppNavigator — Root navigator wrapping AuthGuard + CitizenTabs.
 */

import React from "react";
import { StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { AuthGuard } from "./AuthGuard";
import { CitizenTabs } from "./CitizenTabs";
import { GlobalErrorBanner } from "../screens/GlobalErrorBanner";
import { NetworkStatusBar } from "../screens/NetworkStatusBar";

export function AppNavigator() {
  return (
    <SafeAreaView style={styles.container}>
      <NetworkStatusBar />
      <GlobalErrorBanner />
      <AuthGuard>
        <CitizenTabs />
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
