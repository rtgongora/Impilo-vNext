/**
 * Header — Screen header with title, optional back button and actions.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export interface HeaderProps {
  title: string;
  subtitle?: string;
  onBack?: () => void;
  actions?: React.ReactNode;
  rightElement?: React.ReactNode;
  leftElement?: React.ReactNode;
  accent?: string;
  testID?: string;
}

export function Header({
  title,
  subtitle,
  onBack,
  actions,
  rightElement,
  leftElement,
  accent = "#059669",
  testID,
}: HeaderProps) {
  const resolvedActions = actions ?? rightElement;
  return (
    <View testID={testID} style={styles.container}>
      <View style={styles.leftSection}>
        {leftElement ?? null}
        {onBack ? (
          <Pressable
            onPress={onBack}
            accessibilityLabel="Go back"
            accessibilityRole="button"
            hitSlop={8}
            style={styles.backButton}
          >
            <View style={[styles.backIconCircle, { borderColor: accent + "30" }]}>
              <Text style={[styles.backArrow, { color: accent }]}>{"\u2190"}</Text>
            </View>
          </Pressable>
        ) : null}
        <View style={styles.titleBlock}>
          <Text style={styles.title} numberOfLines={1}>{title}</Text>
          {subtitle ? (
            <Text style={styles.subtitle} numberOfLines={1}>{subtitle}</Text>
          ) : null}
        </View>
      </View>
      {resolvedActions ? (
        <View style={styles.actions}>{resolvedActions}</View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 14,
    paddingHorizontal: 20,
    backgroundColor: "#FFFFFF",
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: "#E5E7EB",
  },
  leftSection: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    flex: 1,
  },
  backButton: {
    marginRight: 4,
  },
  backIconCircle: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  backArrow: {
    fontSize: 16,
    fontWeight: "600",
    lineHeight: 20,
  },
  titleBlock: {
    flex: 1,
  },
  title: {
    fontSize: 18,
    fontWeight: "700",
    color: "#111827",
    letterSpacing: -0.3,
  },
  subtitle: {
    fontSize: 12,
    color: "#6B7280",
    marginTop: 1,
  },
  actions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
});
