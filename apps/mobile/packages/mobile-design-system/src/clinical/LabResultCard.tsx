/**
 * LabResultCard — Displays a lab test result with reference range.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import {
  interpretationFlag,
  trendArrow,
  type InterpretationCode,
  type Trend,
} from "./interpretation-flag";

export type LabResultStatus = "NORMAL" | "ABNORMAL" | "CRITICAL" | "PENDING";

export interface LabResultCardProps {
  testName: string;
  value?: string;
  unit?: string;
  referenceRange?: string;
  status: LabResultStatus;
  collectedDate?: string;
  reportedDate?: string;
  performedBy?: string;
  /** Precise CDS interpretation (LOW/HIGH/CRITICAL_LOW/CRITICAL_HIGH/NO_REFERENCE_RANGE) — advisory flag. */
  interpretation?: InterpretationCode;
  /** Trend vs prior value, shown as an arrow next to the flag. */
  trend?: Trend;
  onPress?: () => void;
  testID?: string;
}

const STATUS_CONFIG: Record<LabResultStatus, { color: string; bg: string }> = {
  NORMAL: { color: "#005420", bg: "#E6F5EC" },
  ABNORMAL: { color: "#E65100", bg: "#FFF3E0" },
  CRITICAL: { color: "#C62828", bg: "#FFEBEE" },
  PENDING: { color: "#1565C0", bg: "#E3F2FD" },
};

export function LabResultCard({
  testName,
  value,
  unit,
  referenceRange,
  status,
  collectedDate,
  reportedDate,
  performedBy,
  interpretation,
  trend,
  onPress,
  testID,
}: LabResultCardProps) {
  const cfg = STATUS_CONFIG[status];
  const flag = interpretation ? interpretationFlag(interpretation) : null;

  const content = (
    <View
      accessibilityLabel={`Lab: ${testName} — ${value ?? "Pending"} ${unit ?? ""}`}
      style={[styles.container, { backgroundColor: cfg.bg }]}
    >
      <View style={styles.headerRow}>
        <Text style={styles.testName}>{testName}</Text>
        <Text style={[styles.statusText, { color: cfg.color }]}>{status}</Text>
      </View>
      {flag ? (
        <View style={[styles.flagBadge, { backgroundColor: flag.bg }]}>
          <Text style={[styles.flagText, { color: flag.color }]}>
            {`${flag.label}${trend ? " " + trendArrow(trend) : ""}`}
          </Text>
        </View>
      ) : null}
      {value ? (
        <Text style={[styles.value, { color: cfg.color }]}>
          {`${value} `}
          {unit ? <Text style={styles.unit}>{unit}</Text> : null}
        </Text>
      ) : (
        <Text style={styles.pendingText}>Result pending</Text>
      )}
      {referenceRange ? (
        <Text style={styles.detailText}>{`Ref: ${referenceRange}`}</Text>
      ) : null}
      {collectedDate ? (
        <Text style={styles.metaText}>{`Collected: ${collectedDate}`}</Text>
      ) : null}
      {reportedDate ? (
        <Text style={styles.metaText}>{`Reported: ${reportedDate}`}</Text>
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
    padding: 12,
    borderRadius: 8,
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  testName: {
    fontWeight: "600",
    fontSize: 14,
  },
  statusText: {
    fontSize: 11,
    fontWeight: "600",
  },
  flagBadge: {
    alignSelf: "flex-start",
    marginTop: 4,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  flagText: {
    fontSize: 11,
    fontWeight: "700",
  },
  value: {
    fontSize: 20,
    fontWeight: "700",
  },
  unit: {
    fontSize: 12,
    fontWeight: "400",
  },
  pendingText: {
    fontSize: 14,
    color: "#757575",
  },
  detailText: {
    fontSize: 12,
    color: "#757575",
  },
  metaText: {
    fontSize: 11,
    color: "#9E9E9E",
  },
});
