/**
 * ErrorState — Displayed when a request or operation fails.
 */

import React from "react";
import { View, Text, Pressable, Platform, StyleSheet } from "react-native";

export interface ErrorStateProps {
  title?: string;
  message: string;
  correlationId?: string;
  onRetry?: () => void;
  testID?: string;
}

export function ErrorState({
  title = "Something went wrong",
  message,
  correlationId,
  onRetry,
  testID,
}: ErrorStateProps) {
  return (
    <View
      testID={testID}
      accessibilityRole="alert"
      style={styles.container}
    >
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.message}>{message}</Text>
      {correlationId ? (
        <Text style={styles.correlationId}>Ref: {correlationId}</Text>
      ) : null}
      {onRetry ? (
        <Pressable onPress={onRetry} style={styles.retryButton}>
          <Text>Retry</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
    padding: 48,
  },
  title: {
    fontSize: 17,
    fontWeight: "600",
    color: "#C62828",
    textAlign: "center",
  },
  message: {
    fontSize: 14,
    color: "#616161",
    marginTop: 8,
    textAlign: "center",
  },
  correlationId: {
    fontSize: 11,
    color: "#9E9E9E",
    marginTop: 4,
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
  },
  retryButton: {
    marginTop: 16,
  },
});
