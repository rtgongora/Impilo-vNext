import React from "react";
import { View, Text, StyleSheet } from "react-native";

export type FeatureMaturityStatus =
  | "live"
  | "connected"
  | "partial"
  | "fixture"
  | "prototype"
  | "not_wired"
  | "requires_backend";

interface FeatureMaturityBadgeProps {
  status: FeatureMaturityStatus;
  detail?: string;
}

const LABELS: Record<FeatureMaturityStatus, string> = {
  live: "Live",
  connected: "Connected",
  partial: "Partial",
  fixture: "Fixture",
  prototype: "Prototype",
  not_wired: "Not wired",
  requires_backend: "Requires backend",
};

const COLORS: Record<FeatureMaturityStatus, { bg: string; fg: string; border: string }> = {
  live: { bg: "#ECFDF5", fg: "#047857", border: "#A7F3D0" },
  connected: { bg: "#EFF6FF", fg: "#1D4ED8", border: "#BFDBFE" },
  partial: { bg: "#FFFBEB", fg: "#B45309", border: "#FDE68A" },
  fixture: { bg: "#FDF4FF", fg: "#A21CAF", border: "#F5D0FE" },
  prototype: { bg: "#F5F3FF", fg: "#6D28D9", border: "#DDD6FE" },
  not_wired: { bg: "#FFF1F2", fg: "#BE123C", border: "#FECDD3" },
  requires_backend: { bg: "#F8FAFC", fg: "#334155", border: "#CBD5E1" },
};

export function FeatureMaturityBadge({ status, detail }: FeatureMaturityBadgeProps) {
  const colors = COLORS[status];
  return (
    <View
      accessibilityRole="summary"
      accessibilityLabel={detail ? `${LABELS[status]}: ${detail}` : LABELS[status]}
      style={[
        styles.container,
        {
          backgroundColor: colors.bg,
          borderColor: colors.border,
        },
      ]}
    >
      <Text style={[styles.text, { color: colors.fg }]}>{LABELS[status]}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignSelf: "flex-start",
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  text: {
    fontSize: 11,
    fontWeight: "600",
  },
});
