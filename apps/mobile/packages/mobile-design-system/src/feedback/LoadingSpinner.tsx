/**
 * LoadingSpinner — Centered loading indicator with optional message.
 */

import React from "react";
import { View, Text, ActivityIndicator, StyleSheet } from "react-native";

export interface LoadingSpinnerProps {
  size?: "sm" | "md" | "lg";
  /** Primary loading message shown below the spinner. */
  message?: string;
  /** Back-compat alias used by several mobile screens. */
  label?: string;
  fullScreen?: boolean;
  testID?: string;
}

const SIZE_MAP: Record<"sm" | "md" | "lg", "small" | "large"> = {
  sm: "small",
  md: "small",
  lg: "large",
};

export function LoadingSpinner({
  size = "md",
  message,
  label,
  fullScreen = false,
  testID,
}: LoadingSpinnerProps) {
  const resolvedMessage = message ?? label;
  return (
    <View
      testID={testID}
      accessibilityLabel={resolvedMessage ?? "Loading"}
      accessibilityRole="progressbar"
      style={[styles.container, fullScreen ? styles.fullScreen : undefined]}
    >
      <ActivityIndicator size={SIZE_MAP[size]} />
      {resolvedMessage ? <Text style={styles.message}>{resolvedMessage}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
  },
  fullScreen: {
    flex: 1,
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 50,
  },
  message: {
    marginTop: 12,
    color: "#757575",
  },
});
