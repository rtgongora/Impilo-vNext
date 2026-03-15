/**
 * RxCard — Displays a prescription summary.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";

export type RxStatus = "ACTIVE" | "COMPLETED" | "CANCELLED" | "DRAFT" | "ON_HOLD";

export interface RxCardProps {
  medicationName: string;
  dosage: string;
  frequency: string;
  duration?: string;
  prescribedBy?: string;
  prescribedDate?: string;
  status: RxStatus;
  refillsRemaining?: number;
  onPress?: () => void;
  testID?: string;
}

const STATUS_COLORS: Record<RxStatus, string> = {
  ACTIVE: "#4CAF50",
  COMPLETED: "#9E9E9E",
  CANCELLED: "#F44336",
  DRAFT: "#FF9800",
  ON_HOLD: "#2196F3",
};

export function RxCard({
  medicationName,
  dosage,
  frequency,
  duration,
  prescribedBy,
  prescribedDate,
  status,
  refillsRemaining,
  onPress,
  testID,
}: RxCardProps) {
  const content = (
    <View
      accessibilityLabel={`Prescription: ${medicationName} ${dosage} ${frequency}`}
      style={[styles.container, { borderLeftColor: STATUS_COLORS[status] }]}
    >
      <View style={styles.headerRow}>
        <Text style={styles.medicationName}>{medicationName}</Text>
        <Text style={[styles.statusText, { color: STATUS_COLORS[status] }]}>{status}</Text>
      </View>
      <Text style={styles.dosageText}>{`${dosage} — ${frequency}`}</Text>
      {duration ? <Text style={styles.detailText}>{`Duration: ${duration}`}</Text> : null}
      {prescribedBy ? (
        <Text style={styles.detailText}>
          {`By: ${prescribedBy}${prescribedDate ? ` on ${prescribedDate}` : ""}`}
        </Text>
      ) : null}
      {refillsRemaining !== undefined ? (
        <Text
          style={[
            styles.detailText,
            { color: refillsRemaining === 0 ? "#F44336" : "#4CAF50" },
          ]}
        >
          {`Refills remaining: ${refillsRemaining}`}
        </Text>
      ) : null}
    </View>
  );

  if (onPress) {
    return (
      <Pressable
        testID={testID}
        onPress={onPress}
        accessibilityRole="button"
      >
        {content}
      </Pressable>
    );
  }

  return (
    <View testID={testID} accessibilityRole="summary">
      {content}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderLeftWidth: 3,
    padding: 12,
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  medicationName: {
    fontWeight: "600",
    fontSize: 15,
  },
  statusText: {
    fontSize: 11,
    fontWeight: "600",
  },
  dosageText: {
    fontSize: 13,
    color: "#616161",
  },
  detailText: {
    fontSize: 12,
    color: "#757575",
  },
});
