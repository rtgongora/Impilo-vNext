import React from "react";
import { View, Text, StyleSheet } from "react-native";

export interface DashboardSectionProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  testID?: string;
}

export function DashboardSection({ title, subtitle, children, testID }: DashboardSectionProps) {
  return (
    <View style={styles.section} testID={testID}>
      <View style={styles.header}>
        <Text style={styles.title}>{title}</Text>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  section: {
    marginBottom: 20,
  },
  header: {
    marginBottom: 12,
    gap: 2,
  },
  title: {
    fontSize: 18,
    fontWeight: "700",
    color: "#111827",
  },
  subtitle: {
    fontSize: 13,
    color: "#6B7280",
  },
});
