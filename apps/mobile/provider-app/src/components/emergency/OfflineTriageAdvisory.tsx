import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { colors } from "@impilo/mobile-design-system";
import { NOT_TRIAGEABLE_OFFLINE, NOT_TRIAGEABLE_OFFLINE_MESSAGE } from "../../lib/emergencyOfflinePolicy";

export function OfflineTriageAdvisory({ offline }: { offline: boolean }) {
  if (!offline) return null;
  return (
    <View style={s.panel} testID="offline-triage-advisory">
      <Text style={s.code}>{NOT_TRIAGEABLE_OFFLINE}</Text>
      <Text style={s.message}>{NOT_TRIAGEABLE_OFFLINE_MESSAGE}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  panel: {
    backgroundColor: "#FEF2F2",
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: colors.ui.error.main,
    gap: 4,
  },
  code: { fontSize: 13, fontWeight: "700", color: colors.ui.error.main },
  message: { fontSize: 12, color: colors.gray[700], lineHeight: 18 },
});
