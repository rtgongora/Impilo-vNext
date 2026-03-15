/**
 * DiagnosisBadge — Displays an ICD-11 diagnosis code with description.
 */

import React from "react";
import { View, Text, Platform, StyleSheet } from "react-native";

export interface DiagnosisBadgeProps {
  code: string;
  description: string;
  isPrimary?: boolean;
  testID?: string;
}

export function DiagnosisBadge({ code, description, isPrimary = false, testID }: DiagnosisBadgeProps) {
  return (
    <View
      testID={testID}
      accessibilityLabel={`${isPrimary ? "Primary " : ""}Diagnosis: ${code} - ${description}`}
      style={[
        styles.container,
        isPrimary ? styles.containerPrimary : styles.containerDefault,
      ]}
    >
      <Text style={styles.code}>{code}</Text>
      <Text style={styles.description}>{description}</Text>
      {isPrimary ? <Text style={styles.primaryLabel}>PRIMARY</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 6,
    borderWidth: 1,
  },
  containerPrimary: {
    backgroundColor: "#FFF3E0",
    borderColor: "#FF9800",
  },
  containerDefault: {
    backgroundColor: "#F5F5F5",
    borderColor: "#E0E0E0",
  },
  code: {
    fontWeight: "600",
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
    fontSize: 12,
  },
  description: {
    fontSize: 13,
  },
  primaryLabel: {
    fontSize: 10,
    fontWeight: "600",
    color: "#E65100",
  },
});
