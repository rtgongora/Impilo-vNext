/**
 * GlobalErrorBanner — Displays global app-level errors at the top of the screen.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Button } from "@impilo/mobile-design-system";
import { useAppStore } from "../stores/appStore";

export function GlobalErrorBanner() {
  const { globalError, setGlobalError } = useAppStore();

  if (!globalError) return null;

  return (
    <View
      testID="global-error-banner"
      accessibilityRole="alert"
      style={styles.container}
    >
      <Text style={styles.errorText}>
        {`${globalError.code}: ${globalError.message}`}
      </Text>
      <Button
        title="Dismiss"
        variant="ghost"
        size="sm"
        onPress={() => setGlobalError(null)}
        testID="dismiss-error"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 12,
    paddingHorizontal: 16,
    backgroundColor: "#FEE2E2",
    borderBottomWidth: 1,
    borderBottomColor: "#FECACA",
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  errorText: {
    color: "#991B1B",
    fontSize: 14,
    flex: 1,
  },
});
