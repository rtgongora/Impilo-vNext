/**
 * EmptyState — Displayed when a list or feed has no content.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
  action?: {
    label: string;
    onPress: () => void;
  };
  testID?: string;
}

export function EmptyState({ title, description, icon, action, testID }: EmptyStateProps) {
  return (
    <View
      testID={testID}
      accessibilityRole="summary"
      style={styles.container}
    >
      {icon ?? null}
      <Text style={styles.title}>{title}</Text>
      {description ? <Text style={styles.description}>{description}</Text> : null}
      {action ? (
        <Pressable onPress={action.onPress} style={styles.actionButton}>
          <Text>{action.label}</Text>
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
