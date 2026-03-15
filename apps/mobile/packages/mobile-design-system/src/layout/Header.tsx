/**
 * Header — Screen header with title, back button, and optional actions.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export interface HeaderProps {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  actions?: React.ReactNode;
  testID?: string;
}

export function Header({ title, subtitle, onBack, actions, testID }: HeaderProps) {
  return (
    <View testID={testID} style={styles.container}>
      <View style={styles.leftSection}>
        {onBack ? (
          <Pressable
            onPress={onBack}
            accessibilityLabel="Go back"
            accessibilityRole="button"
            style={styles.backButton}
          >
            <Text style={styles.backArrow}>{"\u2190"}</Text>
          </Pressable>
        ) : null}
        <View>
          <Text style={styles.title}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        </View>
      </View>
      {actions ?? null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: "#E0E0E0",
  },
  leftSection: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  backButton: {
    padding: 4,
  },
  backArrow: {
    fontSize: 20,
  },
  title: {
    fontSize: 17,
    fontWeight: "600",
  },
  subtitle: {
    fontSize: 13,
    color: "#757575",
  },
});
