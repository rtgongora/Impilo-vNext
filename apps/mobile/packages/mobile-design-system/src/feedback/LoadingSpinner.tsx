/**
 * LoadingSpinner — Centered loading indicator with optional message.
 */

import React from "react";
import { View, Text, ActivityIndicator, StyleSheet } from "react-native";

export interface LoadingSpinnerProps {
  size?: "sm" | "md" | "lg";
  message?: string;
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
  fullScreen = false,
  testID,
}: LoadingSpinnerProps) {
  return (
    <View
      testID={testID}
      accessibilityLabel={message ?? "Loading"}
      accessibilityRole="progressbar"
      style={[styles.container, fullScreen ? styles.fullScreen : undefined]}
    >
      <ActivityIndicator size={SIZE_MAP[size]} />
      {message ? <Text style={styles.message}>{message}</Text> : null}
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
