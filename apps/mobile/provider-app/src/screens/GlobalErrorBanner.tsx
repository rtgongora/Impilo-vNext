/**
 * GlobalErrorBanner — Displays global errors from appStore.
 *
 * Dismissible banner at the top of the app.
 */

import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Button, colors } from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../stores/appStore";

export function GlobalErrorBanner() {
  const { globalError } = useAppStore();

  if (!globalError) return null;

  return (
    <View
      testID="global-error-banner"
      accessibilityRole="alert"
      style={styles.container}
    >
      <View style={styles.messageContainer}>
        <Text style={styles.code}>{globalError.code}</Text>
        <Text style={styles.message}>{globalError.message}</Text>
      </View>
      <Button
        title="Dismiss"
        variant="ghost"
        size="sm"
        onPress={() => appStore.getState().setGlobalError(null)}
        testID="dismiss-error"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.ui.error.light,
    borderBottomWidth: 1,
    borderBottomColor: "#FECACA",
    paddingVertical: 12,
    paddingHorizontal: 16,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  messageContainer: {
    flexDirection: "row",
    alignItems: "center",
    flex: 1,
  },
  code: {
    color: colors.ui.error.text,
    fontWeight: "bold",
  },
  message: {
    color: colors.ui.error.text,
    marginLeft: 8,
  },
});
