/**
 * EmptyState — Displayed when a list or feed has no content.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export interface EmptyStateProps {
  title: string;
  description?: string;
  /**
   * Back-compat alias for `description` (older app screens).
   * Prefer `description`.
   */
  message?: string;
  icon?: React.ReactNode;
  action?: {
    label: string;
    onPress: () => void;
  };
  /**
   * Back-compat aliases used by older screens.
   */
  actionLabel?: string;
  onAction?: () => void;
  testID?: string;
}

export function EmptyState({
  title,
  description,
  message,
  icon,
  action,
  actionLabel,
  onAction,
  testID,
}: EmptyStateProps) {
  const resolvedDescription = description ?? message;
  const resolvedAction = action ?? (actionLabel && onAction ? { label: actionLabel, onPress: onAction } : undefined);
  return (
    <View
      testID={testID}
      accessibilityRole="summary"
      style={styles.container}
    >
      {icon ?? null}
      <Text style={styles.title}>{title}</Text>
      {resolvedDescription ? (
        <Text style={styles.description}>{resolvedDescription}</Text>
      ) : null}
      {resolvedAction ? (
        <Pressable onPress={resolvedAction.onPress} style={styles.actionButton}>
          <Text>{resolvedAction.label}</Text>
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
    marginTop: 16,
    textAlign: "center",
  },
  description: {
    fontSize: 14,
    color: "#757575",
    marginTop: 8,
    textAlign: "center",
  },
  actionButton: {
    marginTop: 16,
  },
});
